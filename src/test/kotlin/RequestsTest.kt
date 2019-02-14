import org.junit.Test
import ru.spbstu.kspt.librarylink.DummyRunner
import ru.spbstu.kspt.librarylink.LibraryLink
import ru.spbstu.kspt.librarylink.ProtobufDataExchange
import ru.spbstu.kspt.librarylink.Requests

class RequestsTest {
    @Test
    fun getRequest() {
        LibraryLink.runner = DummyRunner(true, "/tmp/linktest")
        LibraryLink.exchange = ProtobufDataExchange()
        val requests = Requests()
//        val headers = requests.getHeaders()
//        headers.update("X-Test", "Value")
        val resp = requests.get("https://api.github.com/user")
        println(resp.statusCode())
    }
}