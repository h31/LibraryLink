import java.io.File
import java.io.Reader
import java.io.Writer

fun main(args: Array<String>) {
    val requests = Requests()
    requests.get("https://api.github.com/user")
    requests.stopPython()
}

class Requests {
    val output: Writer
    val input: Reader
    private val pythonProcess: Process

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

    fun printResponse() {
        val buffer = CharArray(1024)
        val size = input.read(buffer)
        val actualData = buffer.copyOfRange(0, size)
        println(actualData)
    }

    fun get(url: String) {
        output.write("import requests; r = requests.get('https://api.github.com/user');; r.status_code\n")
        output.flush()
        println("Wrote request")
        printResponse()
    }
}