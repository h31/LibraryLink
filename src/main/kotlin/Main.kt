import java.io.File
import java.nio.CharBuffer

fun main(args: Array<String>) {
    val output = File("/tmp/wrapperfifo_input").writer()
    println("Output opened!")
    val input = File("/tmp/wrapperfifo_output").reader()
    println("Input opened!")
//    val size = input.readLine()
//    val data = input.readLine()
//    println(size)
//    println(buffer)
//    println(data)
//    output.write("Hi!\n")
    output.write("open(\"/proc/swaps\").read()\n")
    output.flush()
    output.close()
    val buffer = CharArray(1024)
    val size = input.read(buffer)
    val actualData = buffer.copyOfRange(0, size)
    println(actualData)
    input.close()
}