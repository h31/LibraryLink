import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
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
    fun stopChannelInteraction()
}

interface ReceiverRunner {
    val isMultiThreaded: Boolean
    val foreignChannelManager: ForeignChannelManager
    val requestGenerator: RequestGenerator

    fun stop()
}

open class Python3Runner(pathToScript: String, channelPrefix: String) : ReceiverRunner {
    private val pythonProcess: Process = ProcessBuilder("python3", pathToScript, channelPrefix)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    final override val isMultiThreaded = false
    final override val foreignChannelManager = FIFOChannelManager(this, channelPrefix)
    override val requestGenerator: RequestGenerator =
            if (isMultiThreaded) ThreadLocalRequestGenerator(foreignChannelManager) else BlockingRequestGenerator(foreignChannelManager)

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
    override fun getBidirectionalChannel(subchannel: String) = createChannel(subchannel)

    val writer: Writer
    val reader: Reader

    private fun createChannel(subchannel: String): ForeignChannelManager.BidirectionalChannel {
        val outputFile = File("${channelPrefix}_to_receiver_${subchannel}")
        val inputFile = File("${channelPrefix}_from_receiver_${subchannel}")
        if (!outputFile.exists()) {
            mkfifo(outputFile.path)
        }
        if (!inputFile.exists()) {
            mkfifo(inputFile.path)
        }
        val channelWriter = outputFile.writer()
        logger.info("Output opened!")
        val channelReader = inputFile.reader()
        logger.info("Input opened!")
        return ForeignChannelManager.BidirectionalChannel(channelReader, channelWriter)
    }

    init {
        val channel = createChannel("main")
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

open class BlockingRequestGenerator(private val channel: ForeignChannelManager) : RequestGenerator, ForeignChannelManager by channel, SimpleTextFraming() {
    override fun sendRequest(requestMessage: String): String {
        val (reader, writer) = getBidirectionalChannel()

        synchronized(channel) {
            write(writer, requestMessage)
            val response = read(reader)
            return response
        }
    }
}

open class ThreadLocalRequestGenerator(private val channelManager: ForeignChannelManager) : RequestGenerator, ForeignChannelManager by channelManager, SimpleTextFraming() {
    private val localChannel: ThreadLocal<ForeignChannelManager.BidirectionalChannel> = ThreadLocal.withInitial {
        channelManager.getBidirectionalChannel(Thread.currentThread().id.toString())
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

interface Argument {
    val type: String
    val value: Any?
    val key: String?
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
}

open class SimpleTextProcessDataExchange(runner: ReceiverRunner,
                                         val requestGenerator: RequestGenerator = runner.requestGenerator) : ProcessDataExchange, RequestGenerator by requestGenerator {
    val mapper = ObjectMapper().registerModule(KotlinModule())

    val logger = LoggerFactory.getLogger(ProcessDataExchange::class.java)

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
}
