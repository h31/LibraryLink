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

interface ForeignChannelRunner {
    data class BidirectionalChannel(val output: Writer,
                                    val input: Reader)

    fun getBidirectionalChannel(): BidirectionalChannel
    fun getBidirectionalChannel(subchannel: String): BidirectionalChannel
    fun stopChannelInteraction()
}

open class PythonChannelRunner : ForeignChannelRunner {
    override fun getBidirectionalChannel() = ForeignChannelRunner.BidirectionalChannel(output, input)
    override fun getBidirectionalChannel(subchannel: String): ForeignChannelRunner.BidirectionalChannel {
        val outputFile = File("/tmp/wrapperfifo_input_$subchannel")
        val inputFile = File("/tmp/wrapperfifo_output_$subchannel")
        if (!outputFile.exists()) {
            mkfifo(outputFile.path)
        }
        if (!inputFile.exists()) {
            mkfifo(inputFile.path)
        }
        val subchannelOutput = inputFile.writer()
        logger.info("Output $subchannel opened!")
        val subchannelInput = outputFile.reader()
        logger.info("Input $subchannel opened!")
        return ForeignChannelRunner.BidirectionalChannel(subchannelOutput, subchannelInput)
    }

    val output: Writer
    val input: Reader

    private val pythonProcess: Process

    val logger = LoggerFactory.getLogger(PythonChannelRunner::class.java)

    init {
        pythonProcess = ProcessBuilder("python3", "src/main/python/main.py")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        val outputFile = File("/tmp/wrapperfifo_input")
        val inputFile = File("/tmp/wrapperfifo_output")
        if (!outputFile.exists()) {
            mkfifo(outputFile.path)
        }
        if (!inputFile.exists()) {
            mkfifo(inputFile.path)
        }
        output = File("/tmp/wrapperfifo_input").writer()
        logger.info("Output opened!")
        input = File("/tmp/wrapperfifo_output").reader()
        logger.info("Input opened!")
    }

    @Deprecated("")
    fun waitForGreeting() {
        val greeting = pythonProcess.inputStream.bufferedReader().readLine()
        check(greeting == "Done")
        logger.info("Greeting received")
    }

    override fun stopChannelInteraction() = stopPython()

    fun stopPython() {
        logger.info("Stop Python")
        output.close()
        input.close()
        pythonProcess.destroy()
    }
}

open class BlockingRequestGenerator(private val channel: ForeignChannelRunner) : ForeignChannelRunner by channel {
    protected fun sendRequest(requestMessage: String): String {
        val (output, input) = getBidirectionalChannel()

        synchronized(channel) {
            output.write("%04d".format(requestMessage.length))
            output.write(requestMessage)
            output.flush()

            val lengthBuffer = CharArray(4)
            var receivedDataSize = input.read(lengthBuffer)
            check(receivedDataSize == 4)
            val length = String(lengthBuffer).toInt()

            val actualData = CharArray(length)
            receivedDataSize = input.read(actualData)
            check(receivedDataSize == length)
            return String(actualData)
        }
    }
}

open class ThreadLocalRequestGenerator(private val channel: ForeignChannelRunner) : ForeignChannelRunner by channel {
    private val channelCache: MutableMap<Long, ForeignChannelRunner.BidirectionalChannel> = mutableMapOf()

    private fun getChannel(): ForeignChannelRunner.BidirectionalChannel {
        synchronized(this) {
            val threadID = Thread.currentThread().id
            if (threadID in channelCache) {
                return channelCache[threadID]!!
            }
            val subchannel = getBidirectionalChannel(threadID.toString())
            channelCache += threadID to subchannel
            return subchannel
        }
    }

    protected fun sendRequest(requestMessage: String): String {
        val (output, input) = getChannel()

        output.write("%04d".format(requestMessage.length))
        output.write(requestMessage)
        output.flush()

        val lengthBuffer = CharArray(4)
        var receivedDataSize = input.read(lengthBuffer)
        check(receivedDataSize == 4)
        val length = String(lengthBuffer).toInt()

        val actualData = CharArray(length)
        receivedDataSize = input.read(actualData)
        check(receivedDataSize == length)
        return String(actualData)
    }
}

data class Request(val import: String,
                   val objectID: String,
                   val methodName: String, // TODO: Static
                   val args: List<String>,
                   val isStatic: Boolean = false,
                   val doGetReturnValue: Boolean = false,
                   val isProperty: Boolean = false,
                   val assignedID: String = "var" + AssignedIDCounter.counter.getAndIncrement().toString())

object AssignedIDCounter {
    val counter = AtomicInteger()
}

class ChannelResponse(@JsonProperty("return_value") val returnValue: Any? = null)

data class ProcessExchangeResponse(val returnValue: Any?,
                                   val assignedID: String) // TODO

open class Handle(assignedID: String) {
    init {
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

open class SimpleTextProcessDataExchange(channel: ForeignChannelRunner) : ProcessDataExchange, BlockingRequestGenerator(channel) {
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
