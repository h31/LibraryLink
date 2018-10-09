import org.slf4j.LoggerFactory
import ru.spbstu.kspt.librarylink.*

class CurlWrapper(runner: ReceiverRunner = LibraryLink.runner,
                  private val exchange: ProcessDataExchange = SimpleTextProcessDataExchange(runner)) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(Curl::class.java)

    inner class Curl(val storedName: String) : Handle(storedName)

    inner class WriteDataHandler : CallbackReceiver {
        override fun invoke(request: Request): Any? {
            val contents = CharStar(request.args[0].value as String, exchange)
            val size = (request.args[1].value as String).toLong()
            val nmemb = (request.args[2].value as String).toLong()
            return writeData(contents, size, nmemb)
        }

        fun writeData(contents: CharStar, size: Long, nmemb: Long): Long {
            println("Content is ${contents.asString()}")
            return size * nmemb
        }
    }

    init {
        exchange.registerCallback("write_callback", WriteDataHandler())
    }

    fun curl_easy_init(): Curl {
        val response = makeRequest(Request(methodName = "curl_easy_init"))
        return Curl(response.assignedID)
    }

    fun curl_easy_setopt(handle: Curl, option: String, parameter: String): Int {
        val response = makeRequest(Request(methodName = "curl_easy_setopt",
                args = listOf(ReferenceArgument(handle.storedName), NumArgument(option), StringArgument(parameter))))
        return response.returnValue as Int
    }

    fun curl_easy_perform(handle: Curl): Int {
        val response = makeRequest(Request(methodName = "curl_easy_perform",
                args = listOf(ReferenceArgument(handle.storedName))))
        return response.returnValue as Int
    }

    inner class CharStar(storedName: String, dataExchange: ProcessDataExchange) : DataHandle(storedName, dataExchange)
}