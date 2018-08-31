import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    doCurl()
}

class CurlWrapper(runner: ReceiverRunner = LibraryLink.runner,
           private val exchange: ProcessDataExchange = SimpleTextProcessDataExchange(runner)) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(Curl::class.java)

    inner class Curl(val storedName: String) : Handle(storedName)

    fun curl_easy_init(): Curl {
        val response = makeRequest(Request(methodName = "curl_easy_init"))
        return Curl(response.assignedID)
    }

    fun curl_easy_setopt(handle: Curl, option: String, parameter: String): Curl {
        val response = makeRequest(Request(methodName = "curl_easy_setopt",
                args = listOf(ReferenceArgument(handle.storedName), NumArgument(option), StringArgument(parameter))))
        return Curl(response.assignedID)
    }
}