import org.junit.Test
import ru.spbstu.kspt.librarylink.*

class SocketServerTest {
    @Test
    fun runServer() {
        LibraryLink.runner = DummyRunner(true, "/tmp/linktest")
        val wrapper = SocketServerWrapper()
        val addr = Tuple(listOf("127.0.0.1", 3920))
        val server = SocketServerWrapper.TCPServer(addr, ClassDecl(MyHandler::class.java), bind_and_activate = false)
        server.allow_reuse_address(true)
        server.server_bind()
        server.server_activate()
        server.serve_forever()
    }
}

class MyHandler : SocketServerWrapper.BaseRequestHandler() {
    override fun handle() {
        val data = request().recv(10)
        for (x in 0 until len(data)) {
            println(data[x].toChar())
        }
    }
}