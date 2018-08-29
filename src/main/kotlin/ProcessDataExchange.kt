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
    val output: Writer
    val input: Reader

    fun stopChannelInteraction()
}

open class PythonChannelRunner : ForeignChannelRunner {
    final override val output: Writer
    final override val input: Reader

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

open class BlockingRequestGenerator(private val channel: ForeignChannelRunner): ForeignChannelRunner by channel {
    protected fun sendRequest(requestMessage: String): String {
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

data class ProcessExchangeResponse(@JsonProperty("return_value") val returnValue: Any? = null,
                                   val assignedID: String? = null)

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
    fun makeRequestSeparated(request: Request): ProcessExchangeResponse
    fun makeRequest(exec: String? = null, eval: String? = null, store: String? = null, description: String)
    fun registerHandle(handle: Any, assignedID: String)
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

    fun receiveResponse(): ProcessExchangeResponse {
        val lengthBuffer = CharArray(4)
        var size = input.read(lengthBuffer)
        println(size)
        check(size == 4)
        val length = String(lengthBuffer).toInt()

        val actualData = CharArray(length)
        size = input.read(actualData)
        check(size == length)
        val response = mapper.readValue(String(actualData), ProcessExchangeResponse::class.java)
        return response
    }

    private fun makeRequest(requestMessage: String): ProcessExchangeResponse {
        val responseText = sendRequest(requestMessage)
        val response = mapper.readValue(responseText, ProcessExchangeResponse::class.java)
        return response
    }

    override fun makeRequestSeparated(request: Request): ProcessExchangeResponse {
        val message = mapper.writeValueAsString(request)
        logger.info("Wrote $request")
        val response = makeRequest(message)
        val filledResponse = response.copy(assignedID = request.assignedID)
        logger.info("Received $filledResponse")
        return filledResponse
    }

    @Deprecated("Old protocol")
    override fun makeRequest(exec: String?, eval: String?, store: String?, description: String) {
        val request = mutableMapOf<String, String>()
        if (exec != null) request += "exec" to exec
        if (store != null) request += "store" to store
        if (eval != null) request += "eval" to eval
        val message = mapper.writeValueAsString(request)
        makeRequest(message)
        logger.info("Wrote $description")
    }

    override fun registerHandle(handle: Any, assignedID: String) {
        val ref = HandlePhantomReference(handle, HandleReferenceQueue.refqueue, assignedID)
        HandleReferenceQueue.references += ref
    }
}
