package ru.spbstu.kspt.librarylink

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jnr.unixsocket.UnixSocketAddress
import jnr.unixsocket.UnixSocketChannel
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread


fun mkfifo(path: String) {
    val exitCode = Runtime.getRuntime().exec(arrayOf("mkfifo", path)).waitFor()
    check(exitCode == 0)
}

object LibraryLink {
    lateinit var runner: ReceiverRunner
    val exchange: ProcessDataExchange by lazy {
        SimpleTextProcessDataExchange(runner)
    }
}

interface ForeignChannelManager {
    data class BidirectionalChannel(val inputStream: InputStream,
                                    val outputStream: OutputStream)

    fun getBidirectionalChannel(): BidirectionalChannel
    fun getBidirectionalChannel(subchannel: String): BidirectionalChannel
}

interface ReceiverRunner {
    val isMultiThreaded: Boolean
    val foreignChannelManager: ForeignChannelManager
    val requestGenerator: RequestGenerator
    val callbackDataExchange: CallbackDataExchange
    fun defaultRequestGenerator(): RequestGenerator = if (isMultiThreaded) {
        ThreadLocalRequestGenerator(foreignChannelManager, callbackDataExchange)
    } else {
        BlockingRequestGenerator(foreignChannelManager, callbackDataExchange)
    }

    fun stop()
}

open class Python3Runner(pathToScript: String, channelPrefix: String) : ReceiverRunner {
    private val pythonProcess: Process = ProcessBuilder("python3", System.getProperty("user.dir") + "/" + pathToScript, channelPrefix) // TODO
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    final override val isMultiThreaded = false
    final override val foreignChannelManager = UnixSocketChannelManager(channelPrefix)
    override val callbackDataExchange = CallbackDataExchange()
    override val requestGenerator = this.defaultRequestGenerator()

    val logger = LoggerFactory.getLogger(Python3Runner::class.java)

    override fun stop() {
        logger.info("Stop Python")
        pythonProcess.destroy()
    }
}

class DummyRunner(override val isMultiThreaded: Boolean = false,
                  channelPrefix: String) : ReceiverRunner {
    override val foreignChannelManager: ForeignChannelManager = UnixSocketChannelManager(channelPrefix)
    override val callbackDataExchange = CallbackDataExchange()
    override val requestGenerator: RequestGenerator = this.defaultRequestGenerator()

    override fun stop() = Unit // TODO: Use a callback
}

open class UnixSocketChannelManager(val channelPrefix: String) : ForeignChannelManager {
    val logger = LoggerFactory.getLogger(UnixSocketChannelManager::class.java)

    override fun getBidirectionalChannel(): ForeignChannelManager.BidirectionalChannel = connect()

    override fun getBidirectionalChannel(subchannel: String): ForeignChannelManager.BidirectionalChannel = connect()

    private fun connect(): ForeignChannelManager.BidirectionalChannel {
        val socketPath = File(channelPrefix)
        val address = UnixSocketAddress(socketPath)
        val channel = UnixSocketChannel.open(address)
        logger.info("connected to " + channel.remoteSocketAddress)

        val inputStream = Channels.newInputStream(channel)
        val outputStream = Channels.newOutputStream(channel)

        return ForeignChannelManager.BidirectionalChannel(inputStream, outputStream)
    }
}

enum class Tag(val code: Int) {
    REQUEST(0),
    RESPONSE(1),
    CALLBACK_REQUEST(2),
    CALLBACK_RESPONSE(3),
    OPEN_CHANNEL(4),
    DELETE_FROM_PERSISTENCE(5),
    START_BUFFERING(6),
    STOP_BUFFERING(7);

    companion object {
        private val reverseValues: Map<Int, Tag> = values().associate { it.code to it }
        fun valueFrom(i: Int): Tag = reverseValues[i]!!
    }
}

interface Framing {
    fun write(tag: Tag, message: ByteArray)
    fun read(): Pair<Tag, ByteArray>
    var buffering: Boolean
}

//open class BorderFraming : Framing {
//    override fun write(os: OutputStream, message: ByteArray) {
//        os.write(message)
//        os.write(0)
//        os.flush()
//    }
//
//    override fun writeMultiple(writer: OutputStream, messages: ByteArray) {
//        val sb = StringBuffer()
//        for (message in messages) {
//            sb.append(message)
//            sb.append(String(ByteArray(1) { num -> 0 }))
//        }
//        writer.write(sb.toString())
//        writer.flush()
//    }
//
//    override fun read(reader: Reader): String {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//}

open class SimpleTextFraming(private val writer: Writer, private val reader: Reader) : Framing {
    override fun write(tag: Tag, message: ByteArray) {
        val decodedMsg = message.toString()
        writer.write("%04d".format(decodedMsg.length))
        writer.write(decodedMsg)
        writer.flush()
    }

    override fun read(): Pair<Tag, ByteArray> {
        val lengthBuffer = CharArray(4)
        var receivedDataSize = reader.read(lengthBuffer)
        check(receivedDataSize == 4)
        val length = String(lengthBuffer).toInt()

        val actualData = CharArray(length)
        receivedDataSize = reader.read(actualData)
        check(receivedDataSize == length)
        return Pair(Tag.RESPONSE, String(actualData).toByteArray()) // TODO
    }

    override var buffering: Boolean = false
        set(value) {
            TODO()
        }
}

open class SimpleBinaryFraming(baseInputStream: InputStream, baseOutputStream: OutputStream) : Framing {
    val bufferSize = 16 * 1024
    val inputStream = BufferedInputStream(baseInputStream, bufferSize)
    val outputStream = BufferedOutputStream(baseOutputStream, bufferSize)

    override fun write(tag: Tag, message: ByteArray) {
        outputStream.write(message.size.toByteArray())
        outputStream.write(tag.code.toByteArray())
        outputStream.write(message)
        if (!buffering) {
            outputStream.flush()
        }
    }

    private fun readByteArray(length: Int): ByteArray {
        val buffer = ByteArray(length)
        val receivedDataSize = inputStream.read(buffer)
        check(receivedDataSize == length)
        return buffer
    }

    override fun read(): Pair<Tag, ByteArray> {
        val length = readByteArray(4).toInt()
        val tagCode = readByteArray(4).toInt()
        val tag = Tag.valueFrom(tagCode)

        val actualData = readByteArray(length)
        return Pair(tag, actualData)
    }

    override var buffering: Boolean = false
        set(value) {
            field = value
            outputStream.flush()
        }
}

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array()

fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).int

interface RequestGenerator {
    fun sendRequest(requestMessage: ByteArray): ByteArray
}

open class BlockingRequestGenerator(private val channelManager: ForeignChannelManager, val callbackDataExchange: CallbackDataExchange) : RequestGenerator, ForeignChannelManager by channelManager {
    val framing by lazy {
        val channel = getBidirectionalChannel()
        SimpleBinaryFraming(channel.inputStream, channel.outputStream)
    }

    override fun sendRequest(requestMessage: ByteArray): ByteArray {
        synchronized(framing) {
            framing.write(Tag.REQUEST, requestMessage)
            while (true) {
                val response = framing.read()
                if (response.first == Tag.CALLBACK_REQUEST) {
                    val callbackResponse = callbackDataExchange.handleRequest(response.second)
                    framing.write(Tag.CALLBACK_RESPONSE, callbackResponse)
                } else {
                    return response.second
                }
            }
        }
    }
}

open class ThreadLocalRequestGenerator(private val channelManager: ForeignChannelManager, val callbackDataExchange: CallbackDataExchange) : RequestGenerator, ForeignChannelManager by channelManager {
    private val logger = LoggerFactory.getLogger(ThreadLocalRequestGenerator::class.java)

    private val localFraming: ThreadLocal<Framing> = ThreadLocal.withInitial {
        val channelID = Thread.currentThread().id.toString()
        logger.info("Open new channel $channelID")
        val channel = channelManager.getBidirectionalChannel(channelID)
        SimpleBinaryFraming(channel.inputStream, channel.outputStream)
    }

    override fun sendRequest(requestMessage: ByteArray): ByteArray {
        val framing = localFraming.get()
        framing.write(Tag.REQUEST, requestMessage)
        while (true) {
            val response = framing.read()
            if (response.first == Tag.CALLBACK_REQUEST) {
                val callbackResponse = callbackDataExchange.handleRequest(response.second)
                framing.write(Tag.CALLBACK_RESPONSE, callbackResponse)
            } else {
                return response.second
            }
        }
    }
}

interface Request

interface Identifiable {
    val assignedID: String
}

data class MethodCallRequest @JvmOverloads constructor(
        val methodName: String,
        val objectID: String = "",
        var args: List<Argument> = listOf(),
        @JsonProperty("static")
        val isStatic: Boolean = false,
        val doGetReturnValue: Boolean = false,
        @JsonProperty("property")
        val isProperty: Boolean = false,
        override val assignedID: String = AssignedIDCounter.getNextID()) : Request, Identifiable

data class ConstructorRequest(
        val className: String,
        var args: List<Argument> = listOf(),
        override val assignedID: String = AssignedIDCounter.getNextID()
) : Request, Identifiable

data class ImportRequest(
        val importedName: String
) : Request

data class EvalRequest(
        val executedCode: String,
        var args: List<Argument> = listOf(),
        val doGetReturnValue: Boolean = false,
        override val assignedID: String = AssignedIDCounter.getNextID()
) : Request, Identifiable

data class DynamicInheritRequest(
        val importName: String,
        val automatonName: String,
        val methodArguments: Map<String, List<Argument>>,
        override val assignedID: String = AssignedIDCounter.getNextID()
) : Request, Identifiable

data class DynamicCallbackRequest(
        val arguments: List<Argument>,
        override val assignedID: String = AssignedIDCounter.getNextID()
) : Request, Identifiable

data class PersistenceFetchRequest(
        val key: String
) : Request

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonTypeIdResolver(ArgumentIdResolver::class)
interface Argument {
    val type: String
    val value: Any?
    val key: String?
}

public class ArgumentIdResolver : TypeIdResolverBase() {
    lateinit var superType: JavaType

    override fun init(baseType: JavaType) {
        superType = baseType;
    }

    override fun getMechanism(): JsonTypeInfo.Id {
        return JsonTypeInfo.Id.NAME
    }

    override fun idFromValueAndType(value: Any?, suggestedType: Class<*>?): String {
        return (value as Argument).type
    }

    override fun idFromValue(value: Any?): String {
        return (value as Argument).type
    }

    override fun typeFromId(context: DatabindContext, id: String): JavaType {
        val argClass = when (id) {
            "raw" -> PersistenceArgument::class.java
            else -> PersistenceArgument::class.java
        }
        return context.constructSpecializedType(superType, PersistenceArgument::class.java)
    }
}

data class InPlaceArgument(override val value: Any,
                           override val key: String? = null) : Argument {
    override val type = "inplace"
}

data class PersistenceArgument @JvmOverloads constructor(val handle: Handle,
                                                         override val key: String? = null,
                                                         override val value: String = handle.assignedID) : Argument {
    override val type = "persistence"
}

data class ClassObjectArgument @JvmOverloads constructor(val clazz: Class<Handle>,
                                                         override val key: String? = null,
                                                         override val value: String) : Argument {
    override val type = "persistence"
}

object AssignedIDCounter {
    private val counter = AtomicInteger()

    fun getNextID() = "var" + AssignedIDCounter.counter.getAndIncrement().toString()
}

class ChannelResponse(@JsonProperty("return_value") val returnValue: Any? = null)

data class ProcessExchangeResponse(val returnValue: Any?,
                                   val assignedID: String) // TODO

open class Handle() {
    lateinit var assignedID: String

    constructor(assignedID: String) : this() {
        registerReference(assignedID)
    }

    fun registerReference(assignedID: String) {
        this.assignedID = assignedID
        val ref = HandlePhantomReference(this, HandleReferenceQueue.refqueue, assignedID)
        HandleReferenceQueue.references += ref
    }
}

open class ClassDecl(clazz: Class<Handle>, private val exchange: ProcessDataExchange = LibraryLink.exchange) : Handle() {
    init {
        val classID = exchange.makeRequest(DynamicInheritRequest(importName = "socketserver",
                automatonName = "BaseRequestHandler", methodArguments = mapOf("handle" to listOf())))
        registerReference(classID.assignedID)
    }
}

open class DataHandle(assignedID: String, val dataExchange: ProcessDataExchange) : Handle(assignedID) {
    fun asString(): String {
        val content = dataExchange.makeRequest(MethodCallRequest("__read_data", args = listOf(PersistenceArgument(this)))) // TODO
        return content.returnValue as String
    }
}

object HandleReferenceQueue {
    val refqueue = ReferenceQueue<Any>()
    val references: MutableSet<HandlePhantomReference<Any>> = mutableSetOf()
}

class HandlePhantomReference<T>(referent: T, q: ReferenceQueue<T>, val assignedID: String) : PhantomReference<T>(referent, q)

interface ProcessDataExchange {
    fun makeRequest(request: Request): ProcessExchangeResponse
    fun registerCallback(funcName: String, receiver: CallbackReceiver)
    fun registerCallback(funcName: String, receiver: (request: MethodCallRequest, obj: Any?) -> Pair<Any?, Any?>)
    fun registerPrefetch(request: MethodCallRequest)
}

interface CallbackReceiver {
    operator fun invoke(request: MethodCallRequest): Any?
}

open class CallbackDataExchange {
    private val logger = LoggerFactory.getLogger(CallbackDataExchange::class.java)

    private val mapper = ObjectMapper().registerModule(KotlinModule())

    private val callbackReceiversMap: MutableMap<String, (request: MethodCallRequest, obj: Any?) -> Pair<Any?, Any?>> = mutableMapOf()

    private val localPersistence: MutableMap<String, Any?> = mutableMapOf()

    fun registerCallback(funcName: String, receiver: CallbackReceiver) {
        callbackReceiversMap += funcName to { req, _ -> receiver(req) to null } // TODO
    }

    fun registerCallback(funcName: String, receiver: (request: MethodCallRequest, obj: Any?) -> Pair<Any?, Any?>) {
        callbackReceiversMap += funcName to receiver
    }

    fun handleRequest(callbackRequest: ByteArray): ByteArray {
        val request = mapper.readValue(callbackRequest, MethodCallRequest::class.java)
        logger.info("Received callback request $request")
        val callback = callbackReceiversMap[request.methodName] ?: TODO()
        val obj = localPersistence[request.assignedID]
        val (returnValue, createdObj) = callback(request, obj) // TODO
        if (createdObj !== obj) {
            localPersistence[request.assignedID] = createdObj
        }
        val response = ChannelResponse(returnValue)
        val message = mapper.writeValueAsBytes(response)
        logger.info("Responded with ${message.toString(Charset.defaultCharset())}")
        return message
    }
}

open class SimpleTextProcessDataExchange(val runner: ReceiverRunner,
                                         val requestGenerator: RequestGenerator = runner.requestGenerator) : ProcessDataExchange, RequestGenerator by requestGenerator {
    val mapper = ObjectMapper().registerModule(KotlinModule())

    val logger = LoggerFactory.getLogger(ProcessDataExchange::class.java)

    private val logFileWriter = File("link.log").writer()

    init {
        thread {
            logger.info("Waiting to ReferenceQueue elements")
            while (true) {
                val ref = HandleReferenceQueue.refqueue.remove() as HandlePhantomReference
                logger.info("${ref.assignedID} was deleted, sending a message")
                val message = mapper.writeValueAsString(mapOf("delete" to ref.assignedID))
                makeChannelRequest(message)
            }
        }
    }

    private fun logMessage(requestMessage: String) {
        logFileWriter.write(requestMessage + "\n")
        logFileWriter.flush()
    }

    private fun makeChannelRequest(requestMessage: String): ChannelResponse {
        logMessage(requestMessage)
        val responseText = sendRequest(requestMessage.toByteArray())
        val response = mapper.readValue(responseText, ChannelResponse::class.java)
        return response
    }

    override fun makeRequest(request: Request): ProcessExchangeResponse {
        val message = mapper.writeValueAsString(request)
        val channelResponse = makeChannelRequest(message)
        logger.info("Wrote $request")
        val response = ProcessExchangeResponse(returnValue = channelResponse.returnValue, assignedID = if (request is Identifiable) request.assignedID else "") // TODO
        logger.info("Received $response")
        return response
    }

    @Deprecated("Old protocol")
    fun makeRequest(exec: String?, eval: String?, store: String?, description: String) {
        val request = mutableMapOf<String, String>()
        if (exec != null) request += "exec" to exec
        if (store != null) request += "store" to store
        if (eval != null) request += "eval" to eval
        val message = mapper.writeValueAsString(request)
        makeChannelRequest(message)
        logger.info("Wrote $description")
    }

    override fun registerCallback(funcName: String, receiver: CallbackReceiver) =
            runner.callbackDataExchange.registerCallback(funcName, receiver)

    override fun registerCallback(funcName: String, receiver: (request: MethodCallRequest, obj: Any?) -> Pair<Any?, Any?>) =
            runner.callbackDataExchange.registerCallback(funcName, receiver)

    private var prefetchedRequest: MethodCallRequest? = null

    override fun registerPrefetch(request: MethodCallRequest) {
        prefetchedRequest = request
    }
}
