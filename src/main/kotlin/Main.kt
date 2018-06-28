import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.io.Reader
import java.io.Writer

fun main(args: Array<String>) {
    val requests = Requests()
    val resp = requests.get("https://api.github.com/user")
    println(resp.statusCode())
    requests.stopPython()
}

class Requests {
    val output: Writer
    val input: Reader
    private val pythonProcess: Process

    val mapper = ObjectMapper().registerModule(KotlinModule())

    init {
        pythonProcess = ProcessBuilder("python3", "/home/artyom/Projects/HTTPClientKotlin/src/main/python/main.py")
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()
        runPython()
        output = File("/tmp/wrapperfifo_input").writer()
        println("Output opened!")
        input = File("/tmp/wrapperfifo_output").reader()
        println("Input opened!")
    }

    fun runPython() {
        val greeting = pythonProcess.inputStream.bufferedReader().readLine()
        check(greeting == "Done")
        println("Greeting received")

        //    val size = input.readLine()
        //    val data = input.readLine()
        //    println(size)
        //    println(buffer)
        //    println(data)
        //    output.write("Hi!\n")
        //    output.write("open(\"/proc/swaps\").read()\n")
    }

    fun stopPython() {
        println("Stop Python")
        output.close()
        input.close()
        pythonProcess.destroy()
    }

    fun printResponse(): Map<String, String> {
        val lengthBuffer = CharArray(4)
        var size = input.read(lengthBuffer)
        val length = String(lengthBuffer).toInt()

        val actualData = CharArray(length)
        size = input.read(actualData)
        val response = mapper.readValue(String(actualData), Map::class.java)
        return response as Map<String, String>
    }

    private fun makeRequest(requestMessage: Map<String, Any>) {
        val message = mapper.writeValueAsString(requestMessage)
        output.write("%04d".format(message.length))
        output.write(message)
        output.flush()
    }

    fun get(url: String): Response {
        makeRequest(mapOf("exec" to "import requests; r = requests.get('https://api.github.com/user')",
                "store" to "r"))
        println("Wrote request")
        printResponse()
        return Response("r")
    }

    inner class Response(private val storedName: String) {
        fun statusCode(): Int {
            makeRequest(mapOf("exec" to "",
                    "eval" to "r.status_code"))
            println("Wrote request")
            val responseText = printResponse()
            return responseText["return_value"]!!.toInt()
        }
    }
}