import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Reader
import java.io.Writer
import java.util.concurrent.atomic.AtomicInteger

fun mkfifo(path: String) {
    val exitCode = Runtime.getRuntime().exec(arrayOf("mkfifo", path)).waitFor()
    check(exitCode == 0)
}

interface ForeignChannelRunner {
    val output: Writer
    val input: Reader

    fun stopChannelInteration()
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

    override fun stopChannelInteration() = stopPython()

    fun stopPython() {
        logger.info("Stop Python")
        output.close()
        input.close()
        pythonProcess.destroy()
    }
}

open class ProcessDataExchange : PythonChannelRunner() {
    val mapper = ObjectMapper().registerModule(KotlinModule())

    object AssignedIDCounter {
        val counter = AtomicInteger()
    }

    data class Request(val import: String,
                       val objectID: String,
                       val methodName: String, // TODO: Static
                       val args: List<String>,
                       val isStatic: Boolean = false,
                       val doGetReturnValue: Boolean = false,
                       val assignedID: String = "var" + AssignedIDCounter.counter.getAndIncrement().toString())

    data class Response(val returnValue: String)

    fun receiveResponse(): Map<String, Any> {
        val lengthBuffer = CharArray(4)
        var size = input.read(lengthBuffer)
        println(size)
        check(size == 4)
        val length = String(lengthBuffer).toInt()

        val actualData = CharArray(length)
        size = input.read(actualData)
        check(size == length)
        val response = mapper.readValue(String(actualData), Map::class.java)
        return response as Map<String, Any>
    }

    private fun makeRequest(requestMessage: String) {
        output.write("%04d".format(requestMessage.length))
        output.write(requestMessage)
        output.flush()
    }

    fun makeRequestSeparated(request: Request) {
        val message = mapper.writeValueAsString(request)
        makeRequest(message)
        logger.info("Wrote $request")
    }

    fun makeRequest(exec: String? = null, eval: String? = null, store: String? = null, description: String) {
        val request = mutableMapOf<String, String>()
        if (exec != null) request += "exec" to exec
        if (store != null) request += "store" to store
        if (eval != null) request += "eval" to eval
        val message = mapper.writeValueAsString(request)
        makeRequest(message)
        logger.info("Wrote $description")
    }
}