import ru.spbstu.kspt.librarylink.LibraryLink
import ru.spbstu.kspt.librarylink.Python3Runner

fun main() {
    doRequestsWrapper()
    while (true) {
        System.gc()
        Thread.sleep(1000)
    }
}

fun doRequestsWrapper() {
    LibraryLink.runner = Python3Runner("src/main/python/main.py", "/tmp/wrapperfifo")
    val requests = RequestsWrapper().Requests()
    val resp = requests.get("https://api.github.com/user")
    println("Status code is ${resp.status_code()}")
}
