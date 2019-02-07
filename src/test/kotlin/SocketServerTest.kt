import org.junit.Test
import ru.spbstu.kspt.librarylink.DummyRunner
import ru.spbstu.kspt.librarylink.LibraryLink
import ru.spbstu.kspt.librarylink.SocketServerWrapper
import ru.spbstu.kspt.librarylink.Tuple

class SocketServerTest {
    @Test
    fun runServer() {
        LibraryLink.runner = DummyRunner(true, "/tmp/linktest")
        val wrapper = SocketServerWrapper()
        val addr = Tuple(listOf("127.0.0.1", 3915))
        val myHandler = Handler()
        val server = SocketServerWrapper.TCPServer(addr, myHandler)
        server.allow_reuse_address(true)
        server.serve_forever()
    }
}

class Handler : SocketServerWrapper.BaseRequestHandler() {
    override fun handle() {
        val data = request().recv(10)
        println(data[0])
    }
}