package ru.spbstu.kspt.librarylink

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
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
import kotlin.reflect.KProperty


fun mkfifo(path: String) {
    val exitCode = Runtime.getRuntime().exec(arrayOf("mkfifo", path)).waitFor()
    check(exitCode == 0)
}

object LibraryLink {
    lateinit var runner: ReceiverRunner
    var defaultExchange: ProcessDataExchange? = null
    var exchange: ProcessDataExchange by lazy {
        val default = defaultExchange
        if (default != null) {
            default
        } else {
            SimpleTextProcessDataExchange(runner)
        }
    }
}

private operator fun <T> Lazy<T>.setValue(libraryLink: LibraryLink, property: KProperty<*>, processDataExchange: ProcessDataExchange) {
    libraryLink.defaultExchange = processDataExchange
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
            write(if (value) Tag.START_BUFFERING else Tag.STOP_BUFFERING, ByteArray(0))
            outputStream.flush()
        }
}

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array()

fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).int

interface RequestGenerator {
    fun sendRequest(requestMessage: ByteArray): ByteArray
    val framing: Framing
}

open class BlockingRequestGenerator(private val channelManager: ForeignChannelManager, val callbackDataExchange: CallbackDataExchange) : RequestGenerator, ForeignChannelManager by channelManager {
    override val framing by lazy {
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

    override val framing: Framing
        get() = localFraming.get()

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
        val inherits: String,
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

//@JsonTypeInfo(
//        use = JsonTypeInfo.Id.NAME,
//        include = JsonTypeInfo.As.PROPERTY,
//        property = "type"
//)
//@JsonTypeIdResolver(ArgumentIdResolver::class)
class Argument {
    val type: String
    val value: Any?
    val key: String?

    @JvmOverloads constructor(obj: Handle, key: String? = null) {
        this.type = "persistence"
        this.value = obj.assignedID
        this.key = key
    }

    @JvmOverloads constructor(obj: Class<out Handle>, key: String? = null) {
        this.type = "persistence"
        this.value = "class_${obj.canonicalName}" // TODO
        this.key = key
    }

    @JvmOverloads constructor(obj: Number, key: String? = null) {
        this.type = "inplace"
        this.value = obj
        this.key = key
    }

    @JvmOverloads constructor(obj: Boolean, key: String? = null) {
        this.type = "inplace"
        this.value = obj
        this.key = key
    }

    @JvmOverloads constructor(obj: String, key: String? = null) {
        this.type = "inplace"
        this.value = obj
        this.key = key
    }

    @JsonCreator
    constructor(@JsonProperty("type") type: String,
                @JsonProperty("value") value: Any?,
                @JsonProperty("key") key: String?) {
        this.type = type
        this.value = value
        this.key = key
    }
}

//public class ArgumentIdResolver : TypeIdResolverBase() {
//    lateinit var superType: JavaType
//
//    override fun init(baseType: JavaType) {
//        superType = baseType;
//    }
//
//    override fun getMechanism(): JsonTypeInfo.Id {
//        return JsonTypeInfo.Id.NAME
//    }
//
//    override fun idFromValueAndType(value: Any?, suggestedType: Class<*>?): String {
//        return (value as Argument).type
//    }
//
//    override fun idFromValue(value: Any?): String {
//        return (value as Argument).type
//    }
//
//    override fun typeFromId(context: DatabindContext, id: String): JavaType {
//        val argClass = when (id) {
//            "raw" -> PersistenceArgument::class.java
//            else -> PersistenceArgument::class.java
//        }
//        return context.constructSpecializedType(superType, PersistenceArgument::class.java)
//    }
//}

//data class InPlaceArgument(override val value: Any,
//                           override val key: String? = null) : Argument {
//    override val type = "inplace"
//}
//
//data class PersistenceArgument @JvmOverloads constructor(val handle: Handle,
//                                                         override val key: String? = null,
//                                                         override val value: String = handle.assignedID) : Argument {
//    override val type = "persistence"
//}
//
//data class ClassObjectArgument @JvmOverloads constructor(val clazz: Class<Handle>,
//                                                         override val key: String? = null,
//                                                         override val value: String) : Argument {
//    override val type = "persistence"
//}

object AssignedIDCounter {
    private val counter = AtomicInteger()

    fun getNextID() = "var" + AssignedIDCounter.counter.getAndIncrement().toString()
}

class ChannelResponse(
        @JsonProperty("return_value") val returnValue: Any? = null,
        @JsonProperty("exception_message") val exceptionMessage: String? = null
)

data class ProcessExchangeResponse(val returnValue: Any?,
                                   val assignedID: String) // TODO

open class Handle() {
    lateinit var assignedID: String

    constructor(assignedID: String) : this() {
        registerReference(assignedID)
    }

    fun registerReference(assignedID: String): Handle {
        this.assignedID = assignedID
        val ref = HandlePhantomReference(this, HandleReferenceQueue.refqueue, assignedID)
        HandleReferenceQueue.references += ref
        return this
    }
}

open class ClassDecl<T>(clazz: Class<T>, exchange: ProcessDataExchange = LibraryLink.exchange) : Handle() {
    init {
        val classID = exchange.makeRequest(DynamicInheritRequest(importName = "socketserver",
                automatonName = clazz.simpleName, inherits = clazz.superclass.simpleName,
                methodArguments = mapOf("handle" to listOf())))
        registerReference(classID.assignedID)

        exchange.registerConstructorCallback(clazz.simpleName) { req ->
            val instance = clazz.constructors.first { !it.isSynthetic }.newInstance()
            (instance as Handle).registerReference(req.assignedID)
            instance
        } // TODO: Args
    }
}

open class DataHandle(assignedID: String, val dataExchange: ProcessDataExchange) : Handle(assignedID) {
    fun asString(): String {
        val content = dataExchange.makeRequest(MethodCallRequest("__read_data", args = listOf(Argument(this)))) // TODO
        return content.returnValue as String
    }
}

object HandleReferenceQueue {
    val refqueue = ReferenceQueue<Any>()
    val references: MutableSet<HandlePhantomReference<Any>> = mutableSetOf()
}

class HandlePhantomReference<T>(referent: T, q: ReferenceQueue<T>, val assignedID: String) : PhantomReference<T>(referent, q)

interface CallbackRegistrable {
    fun registerCallback(funcName: String, receiver: CallbackReceiver)
    fun registerCallback(funcName: String, receiver: (request: MethodCallRequest, obj: Any) -> Any?)
    fun registerConstructorCallback(className: String, receiver: (request: MethodCallRequest) -> Any?)
}

interface ProcessDataExchange : CallbackRegistrable {
    fun makeRequest(request: Request): ProcessExchangeResponse
    fun registerPrefetch(request: MethodCallRequest)
}

interface CallbackReceiver {
    operator fun invoke(request: MethodCallRequest): Any?
}

open class CallbackDataExchange : CallbackRegistrable {
    private val logger = LoggerFactory.getLogger(CallbackDataExchange::class.java)

    private val mapper = ObjectMapper().registerModule(KotlinModule())

    private val callbackReceiversMap: MutableMap<String, (request: MethodCallRequest, obj: Any) -> Any?> = mutableMapOf()

    private val callbackConstructorsMap: MutableMap<String, (request: MethodCallRequest) -> Any?> = mutableMapOf()

    private val localPersistence: MutableMap<String, Any?> = mutableMapOf()

    override fun registerCallback(funcName: String, receiver: CallbackReceiver) {
        callbackReceiversMap += funcName to { req, _ -> receiver(req) to null } // TODO
    }

    override fun registerCallback(funcName: String, receiver: (request: MethodCallRequest, obj: Any) -> Any?) {
        callbackReceiversMap += funcName to receiver
    }

    override fun registerConstructorCallback(className: String, receiver: (request: MethodCallRequest) -> Any?) {
        callbackConstructorsMap += className to receiver
    }

    fun handleRequest(callbackRequest: ByteArray): ByteArray {
        val request = mapper.readValue(callbackRequest, MethodCallRequest::class.java)
        logger.info("Received callback request $request")
        val existingObject = localPersistence[request.assignedID]
        val obj = existingObject ?: callbackConstructorsMap[request.objectID]?.invoke(request) ?: TODO() // TODO: Too Dirty
        if (existingObject == null) {
            localPersistence[request.assignedID] = obj
        }
        val callback = callbackReceiversMap[request.methodName] ?: TODO()
        val returnValue = callback(request, obj) // TODO
        val response = ChannelResponse(returnValue)
        val message = mapper.writeValueAsBytes(response)
        logger.info("Responded with ${message.toString(Charset.defaultCharset())}")
        return message
    }
}

class LibraryLinkException(message: String) : Exception(message)

open class ProtobufDataExchange(val runner: ReceiverRunner = LibraryLink.runner,
                                val requestGenerator: RequestGenerator = runner.requestGenerator) : ProcessDataExchange,
        RequestGenerator by requestGenerator, CallbackRegistrable by runner.callbackDataExchange {
    override fun makeRequest(request: Request): ProcessExchangeResponse {
        val responseBinary = when (request) {
            is MethodCallRequest -> requestGenerator.sendRequest(request.toProtobuf())
            is ImportRequest -> requestGenerator.sendRequest(request.toProtobuf())
            else -> TODO()
        }
        val channelResponse = Exchange.ChannelResponse.parseFrom(responseBinary)
        val assignedID = if (request is Identifiable) request.assignedID else "" // TODO
        return when (channelResponse.returnValueCase) {
            Exchange.ChannelResponse.ReturnValueCase.RETURN_VALUE_STRING ->
                ProcessExchangeResponse(returnValue = channelResponse.returnValueString, assignedID = assignedID)
            Exchange.ChannelResponse.ReturnValueCase.RETURN_VALUE_INT ->
                ProcessExchangeResponse(returnValue = channelResponse.returnValueInt, assignedID = assignedID)
            Exchange.ChannelResponse.ReturnValueCase.NO_RETURN_VALUE,
            Exchange.ChannelResponse.ReturnValueCase.RETURNVALUE_NOT_SET,
            null ->
                ProcessExchangeResponse(returnValue = null, assignedID = assignedID)
            Exchange.ChannelResponse.ReturnValueCase.EXCEPTION_MESSAGE ->
                throw LibraryLinkException(channelResponse.exceptionMessage)
        }
    }

    override fun registerPrefetch(request: MethodCallRequest) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun MethodCallRequest.toProtobuf(): ByteArray {
        val builder = Exchange.MethodCallRequest.newBuilder()
        builder.methodName = this.methodName
        builder.objectID = this.objectID
        for (arg in this.args) {
            val argBuilder = Exchange.MethodCallRequest.Argument.newBuilder()
            argBuilder.type = when (arg.type) {
                "persistence" -> Exchange.MethodCallRequest.ArgumentType.PERSISTENCE
                "inplace" -> Exchange.MethodCallRequest.ArgumentType.INPLACE
                else -> throw IllegalArgumentException("arg.type == ${arg.type}")
            }
            when (arg.value) {
                is Number -> argBuilder.intValue = arg.value.toInt() // TODO: Long?
                is String -> argBuilder.stringValue = arg.value
            }
            if (arg.key != null) argBuilder.key = arg.key
            builder.addArgs(argBuilder)
        }
        builder.static = this.isStatic
        builder.doGetReturnValue = this.doGetReturnValue
        builder.property = this.isProperty
        builder.assignedID = this.assignedID
        return builder.build().toByteArray()
    }

    fun ImportRequest.toProtobuf(): ByteArray =
            Exchange.ImportRequest.newBuilder().setImportedName(this.importedName).build().toByteArray()
}

open class SimpleTextProcessDataExchange(val runner: ReceiverRunner = LibraryLink.runner,
                                         val requestGenerator: RequestGenerator = runner.requestGenerator) : ProcessDataExchange,
        RequestGenerator by requestGenerator, CallbackRegistrable by runner.callbackDataExchange {
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
        logger.info("Wrote $request")
        val channelResponse = makeChannelRequest(message)
        if (channelResponse.exceptionMessage != null) {
            throw LibraryLinkException(channelResponse.exceptionMessage)
        }
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

    private var prefetchedRequest: MethodCallRequest? = null

    override fun registerPrefetch(request: MethodCallRequest) {
        prefetchedRequest = request
    }
}
