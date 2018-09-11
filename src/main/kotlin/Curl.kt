import org.slf4j.LoggerFactory

class CurlWrapper(runner: ReceiverRunner = LibraryLink.runner,
           private val exchange: ProcessDataExchange = SimpleTextProcessDataExchange(runner)) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(Curl::class.java)

    inner class Curl(val storedName: String) : Handle(storedName)

    inner class WriteDataHandler : CallbackReceiver {
        override fun invoke(request: Request): Any? {
            val arg_name = request.args[0].value as String
            val content = makeRequest(Request("__read_data", args = listOf(ReferenceArgument(arg_name))))
            println("Content is ${content.returnValue}")
            val size = (request.args[1].value as String).toInt()
            val nmemb = (request.args[2].value as String).toInt()
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
}