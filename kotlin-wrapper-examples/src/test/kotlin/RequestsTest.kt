import org.junit.Test
import ru.spbstu.kspt.librarylink.DummyRunner
import ru.spbstu.kspt.librarylink.LibraryLink
import ru.spbstu.kspt.librarylink.ProtoBufDataExchange
import ru.spbstu.kspt.librarylink.Requests

class RequestsTest {
    @Test
    fun getRequest() {
        LibraryLink.runner = DummyRunner(true, "/tmp/linktest")
        LibraryLink.exchange = ProtoBufDataExchange()
        val requests = Requests()
//        val headers = requests.getHeaders()
//        headers.update("X-Test", "Value")
        val resp = requests.get("https://api.github.com/user")
        for (i in 0..100) {
            println(resp.statusCode())
        }
    }
}