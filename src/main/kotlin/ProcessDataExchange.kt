import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DatabindContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Reader
import java.io.Writer
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

fun mkfifo(path: String) {
    val exitCode = Runtime.getRuntime().exec(arrayOf("mkfifo", path)).waitFor()
    check(exitCode == 0)
}

object LibraryLink {
    lateinit var runner: ReceiverRunner
}

interface ForeignChannelManager {
    data class BidirectionalChannel(val reader: Reader,
                                    val writer: Writer)

    fun getBidirectionalChannel(): BidirectionalChannel
    fun getBidirectionalChannel(subchannel: String): BidirectionalChannel
    fun getBidirectionalCallbackChannel(subchannel: String): BidirectionalChannel
    fun createBidirectionalChannel(subchannel: String) // TODO: find a way to perform without a dedicated create call
    fun stopChannelInteraction()
}

interface ReceiverRunner {
    val isMultiThreaded: Boolean
    val foreignChannelManager: ForeignChannelManager
    val requestGenerator: RequestGenerator
    fun defaultRequestGenerator(): RequestGenerator = if (isMultiThreaded) {
        ThreadLocalRequestGenerator(foreignChannelManager)
    } else {
        BlockingRequestGenerator(foreignChannelManager)
    }

    fun stop()
}

open class Python3Runner(pathToScript: String, channelPrefix: String) : ReceiverRunner {
    private val pythonProcess: Process = ProcessBuilder("python3", pathToScript, channelPrefix)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    final override val isMultiThreaded = false
    final override val foreignChannelManager = FIFOChannelManager(this, channelPrefix)
    override val requestGenerator = this.defaultRequestGenerator()

    val logger = LoggerFactory.getLogger(Python3Runner::class.java)

    override fun stop() {
        logger.info("Stop Python")
        pythonProcess.destroy()
    }
}

class DummyRunner(override val isMultiThreaded: Boolean = false,
                  channelPrefix: String) : ReceiverRunner {
    override val foreignChannelManager: ForeignChannelManager = FIFOChannelManager(this, channelPrefix)
    override val requestGenerator: RequestGenerator =
            if (isMultiThreaded) ThreadLocalRequestGenerator(foreignChannelManager) else BlockingRequestGenerator(foreignChannelManager)

    override fun stop() = Unit // TODO: Use a callback
}

open class FIFOChannelManager(val runner: ReceiverRunner, val channelPrefix: String) : ForeignChannelManager {
    val logger = LoggerFactory.getLogger(FIFOChannelManager::class.java)

    override fun getBidirectionalChannel() = ForeignChannelManager.BidirectionalChannel(reader, writer)
    override fun getBidirectionalChannel(subchannel: String) = openFIFOChannel(subchannel)
    override fun getBidirectionalCallbackChannel(subchannel: String) = openFIFOChannel("callback") // TODO: Use subchannel name?
    override fun createBidirectionalChannel(subchannel: String) {
        makeFIFO(subchannel)
    }

    val writer: Writer
    val reader: Reader

    fun makeFIFO(subchannel: String): Pair<File, File> {
        val outputFile = File("${channelPrefix}_to_receiver_${subchannel}")
        val inputFile = File("${channelPrefix}_from_receiver_${subchannel}")
        if (!outputFile.exists()) {
            mkfifo(outputFile.path)
        }
        if (!inputFile.exists()) {
            mkfifo(inputFile.path)
        }
        return Pair(outputFile, inputFile)
    }

    private fun openFIFOChannel(subchannel: String): ForeignChannelManager.BidirectionalChannel {
        val (outputFile, inputFile) = makeFIFO(subchannel)
        val channelWriter = outputFile.writer()
        logger.info("Output opened!")
        val channelReader = inputFile.reader()
        logger.info("Input opened!")
        return ForeignChannelManager.BidirectionalChannel(channelReader, channelWriter)
    }

    init {
        val channel = openFIFOChannel("main")
        writer = channel.writer
        reader = channel.reader
    }

    override fun stopChannelInteraction() {
        reader.close()
        writer.close()
        logger.info("Main channel stopped")
        runner.stop()
    }
}

interface Framing {
    fun write(writer: Writer, message: String)
    fun read(reader: Reader): String
}

open class SimpleTextFraming : Framing {
    override fun write(writer: Writer, message: String) {
        writer.write("%04d".format(message.length))
        writer.write(message)
        writer.flush()
    }

    override fun read(reader: Reader): String {
        val lengthBuffer = CharArray(4)
        var receivedDataSize = reader.read(lengthBuffer)
        check(receivedDataSize == 4)
        val length = String(lengthBuffer).toInt()

        val actualData = CharArray(length)
        receivedDataSize = reader.read(actualData)
        check(receivedDataSize == length)
        return String(actualData)
    }
}

interface RequestGenerator {
    fun sendRequest(requestMessage: String): String
}

open class BlockingRequestGenerator(private val channelManager: ForeignChannelManager) : RequestGenerator, ForeignChannelManager by channelManager, SimpleTextFraming() {
    override fun sendRequest(requestMessage: String): String {
        val channel = getBidirectionalChannel()
        val (reader, writer) = channel

        synchronized(channel) {
            write(writer, requestMessage)
            val response = read(reader)
            return response
        }
    }
}

open class ThreadLocalRequestGenerator(private val channelManager: ForeignChannelManager) : RequestGenerator, ForeignChannelManager by channelManager, SimpleTextFraming() {
    private val localChannel: ThreadLocal<ForeignChannelManager.BidirectionalChannel> = ThreadLocal.withInitial {
        val channelID = Thread.currentThread().id.toString()
        println("Open new channel $channelID")
        channelManager.createBidirectionalChannel(channelID)
        val mainChannel = channelManager.getBidirectionalChannel()
        write(mainChannel.writer, "{\"register_channel\": \"$channelID\"}") // TODO
        return@withInitial channelManager.getBidirectionalChannel(channelID)
    }

    override fun sendRequest(requestMessage: String): String {
        val (reader, writer) = localChannel.get()

        write(writer, requestMessage)
        val response = read(reader)
        return response
    }
}

data class Request @JvmOverloads constructor(
        val methodName: String,
        val objectID: String = "",
        var args: List<Argument> = listOf(),
        val import: String = "",
        val isStatic: Boolean = false,
        val doGetReturnValue: Boolean = false,
        val isProperty: Boolean = false,
        val assignedID: String = AssignedIDCounter.getNextID())

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
            "raw" -> ReferenceArgument::class.java
            else -> ReferenceArgument::class.java
        }
        return context.constructSpecializedType(superType, ReferenceArgument::class.java)
    }
}

data class StringArgument(override val value: String,
                          override val key: String? = null) : Argument {
    override val type = "string"
}

data class NumArgument(override val value: String,
                       override val key: String? = null) : Argument {
    override val type = "num"
}

data class RawArgument(override val value: String,
                       override val key: String? = null) : Argument {
    override val type = "raw"
}

data class ReferenceArgument(override val value: String,
                             override val key: String? = null) : Argument {
    override val type = "raw"
}

object AssignedIDCounter {
    val counter = AtomicInteger()

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

object HandleReferenceQueue {
    val refqueue = ReferenceQueue<Any>()
    val references: MutableSet<HandlePhantomReference<Any>> = mutableSetOf()
}

class HandlePhantomReference<T>(referent: T, q: ReferenceQueue<T>, val assignedID: String) : PhantomReference<T>(referent, q)

interface ProcessDataExchange {
    fun makeRequest(request: Request): ProcessExchangeResponse
    fun registerCallback(funcName: String, receiver: CallbackReceiver)
}

interface CallbackReceiver {
    operator fun invoke(request: Request): Any?
}

open class CallbackDataExchange(val channelManager: ForeignChannelManager,
                                val framing: Framing) : Framing by framing {
    private val logger = LoggerFactory.getLogger(CallbackDataExchange::class.java)

    private val mapper = ObjectMapper().registerModule(KotlinModule())

    private val callbackReceiversMap: MutableMap<String, CallbackReceiver> = mutableMapOf()

    fun registerCallback(funcName: String, receiver: CallbackReceiver) {
        callbackReceiversMap += funcName to receiver
    }

    fun run() = thread {
        val channel = channelManager.getBidirectionalCallbackChannel("") // TODO: subchannel name?

        val callbackRequest = read(channel.reader)
        val request = mapper.readValue(callbackRequest, Request::class.java)
        logger.info("Received callback request $request")
        val callback = callbackReceiversMap[request.methodName] ?: TODO()
        val returnValue = callback(request)
        val response = mapOf("return_value" to returnValue)
        val message = mapper.writeValueAsString(response)
        logger.info("Responded with $message")
        write(channel.writer, message)
    }
}

open class SimpleTextProcessDataExchange(runner: ReceiverRunner,
                                         val requestGenerator: RequestGenerator = runner.requestGenerator) : ProcessDataExchange, RequestGenerator by requestGenerator {
    val mapper = ObjectMapper().registerModule(KotlinModule())

    val logger = LoggerFactory.getLogger(ProcessDataExchange::class.java)

    private val callbackDataExchange = CallbackDataExchange(runner.foreignChannelManager, SimpleTextFraming())

    init {
        thread {
            logger.info("Waiting to ReferenceQueue elements")
            while (true) {
                val ref = HandleReferenceQueue.refqueue.remove() as HandlePhantomReference
                logger.info("${ref.assignedID} was deleted, sending a message")
                val message = mapper.writeValueAsString(mapOf("delete" to ref.assignedID))
                makeRequest(message)
            }
        }
        if (runner.isMultiThreaded) {
            callbackDataExchange.run()
        }
    }

    private fun makeRequest(requestMessage: String): ChannelResponse {
        val responseText = sendRequest(requestMessage)
        val response = mapper.readValue(responseText, ChannelResponse::class.java)
        return response
    }

    override fun makeRequest(request: Request): ProcessExchangeResponse {
        val message = mapper.writeValueAsString(request)
        val channelResponse = makeRequest(message)
        logger.info("Wrote $request")
        val response = ProcessExchangeResponse(returnValue = channelResponse.returnValue, assignedID = request.assignedID)
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
        makeRequest(message)
        logger.info("Wrote $description")
    }

    override fun registerCallback(funcName: String, receiver: CallbackReceiver) = callbackDataExchange.registerCallback(funcName, receiver)
}
