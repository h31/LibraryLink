import ru.spbstu.kspt.librarylink.Headers
import ru.spbstu.kspt.librarylink.LibraryLink
import ru.spbstu.kspt.librarylink.Python3Runner
import ru.spbstu.kspt.librarylink.Requests

fun main(args: Array<String>) {
    doRequests()
    LibraryLink.runner.stop()
    while (true) {
        System.gc()
        Thread.sleep(1000)
    }
}

fun doRequests() {
    LibraryLink.runner = Python3Runner("src/main/python/main.py", "/tmp/wrapperfifo")
    val requests = Requests()
    Thread.sleep(1000) // TODO: Another way?
    val headers = Headers()
    headers.update("X-Test", "Value")
    val resp = requests.get("https://api.github.com/user", headers)
    println("Status code is ${resp.statusCode()}")
    println("Content is ${String(resp.content())}")
    println("Headers are ${resp.headers()}")
//    requests.stopPython()
}
