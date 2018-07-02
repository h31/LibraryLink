import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.io.Reader
import java.io.Writer
import java.util.*
import org.slf4j.LoggerFactory


fun main(args: Array<String>) {
    val requests = Requests()
    val resp = requests.get("https://api.github.com/user")
    println(resp.statusCode())
    println(String(resp.content()))
    requests.stopPython()
}

fun mkfifo(path: String) {
    val exitCode = Runtime.getRuntime().exec(arrayOf("mkfifo", path)).waitFor()
    check(exitCode == 0)
}

class Requests {
    val logger = LoggerFactory.getLogger(Requests::class.java)

    val output: Writer
    val input: Reader
    private val pythonProcess: Process

    val mapper = ObjectMapper().registerModule(KotlinModule())

    init {
        pythonProcess = ProcessBuilder("python3", "/home/artyom/Projects/HTTPClientKotlin/src/main/python/main.py")
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

    fun printResponse(): Map<String, Any> {
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

    private fun makeRequest(requestMessage: Map<String, String>) {
        val message = mapper.writeValueAsString(requestMessage)
        output.write("%04d".format(message.length))
        output.write(message)
        output.flush()
    }

    private fun makeRequest(exec: String? = null, eval: String? = null, store: String? = null) {
        val request = mutableMapOf<String, String>()
        if (exec != null) request += "exec" to exec
        if (store != null) request += "store" to store
        if (eval != null) request += "eval" to eval
        makeRequest(request)
    }

    fun get(url: String): Response {
        makeRequest(exec = "import requests; r = requests.get('$url')",
                store = "r")
        logger.info("Wrote get")
        printResponse()
        return Response("r")
    }

    inner class Response(private val storedName: String) {
        fun statusCode(): Int {
            makeRequest(eval = "$storedName.status_code")
            logger.info("Wrote statusCode")
            val responseText = printResponse()
            val returnValue = responseText["return_value"]
            if (returnValue != null && returnValue is Int) {
                return returnValue
            } else {
                throw IllegalArgumentException()
            }
        }

        fun content(): ByteArray {
            makeRequest(eval = "$storedName.content")
            logger.info("Wrote content")
            val responseText = printResponse()
            val returnValue = responseText["return_value"]
            if (returnValue != null && returnValue is String) {
                return Base64.getDecoder().decode(returnValue)
            } else {
                throw IllegalArgumentException()
            }
        }
    }
}