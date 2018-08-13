import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.Reader
import java.io.Writer

fun mkfifo(path: String) {
    val exitCode = Runtime.getRuntime().exec(arrayOf("mkfifo", path)).waitFor()
    check(exitCode == 0)
}

interface ForeignChannelRunner {
    val output: Writer
    val input: Reader
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

    fun stopPython() {
        logger.info("Stop Python")
        output.close()
        input.close()
        pythonProcess.destroy()
    }
}

open class ProcessDataExchange : PythonChannelRunner() {
    val mapper = ObjectMapper().registerModule(KotlinModule())

    fun receiveResponse(): Map<String, Any> {
        val lengthBuffer = CharArray(4)
        var size = input.read(lengthBuffer)
        check(size == 4)
        val length = String(lengthBuffer).toInt()

        val actualData = CharArray(length)
        size = input.read(actualData)
        check(size == length)
        val response = mapper.readValue(String(actualData), Map::class.java)
        return response as Map<String, Any>
    }

    private fun makeRequest(requestMessage: Map<String, Any>) {
        val message = mapper.writeValueAsString(requestMessage)
        output.write("%04d".format(message.length))
        output.write(message)
        output.flush()
    }

    fun makeRequestSeparated(methodName: String, objectID: String, args: List<String>, description: String) {
        val request = mutableMapOf("methodName" to methodName, "objectID" to objectID, "args" to args)
        makeRequest(request)
        logger.info("Wrote $description")
    }

    fun makeRequest(exec: String? = null, eval: String? = null, store: String? = null, description: String) {
        val request = mutableMapOf<String, String>()
        if (exec != null) request += "exec" to exec
        if (store != null) request += "store" to store
        if (eval != null) request += "eval" to eval
        makeRequest(request)
        logger.info("Wrote $description")
    }
}