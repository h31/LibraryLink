import org.slf4j.LoggerFactory

class CurlWrapper(runner: ReceiverRunner = LibraryLink.runner,
           private val exchange: ProcessDataExchange = SimpleTextProcessDataExchange(runner)) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(Curl::class.java)

    inner class Curl(val storedName: String) : Handle(storedName)

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

    fun read_data(): String {
        val response = makeRequest(Request(methodName = "__read_data",
                args = listOf()))
        return response.returnValue as String
    }
}