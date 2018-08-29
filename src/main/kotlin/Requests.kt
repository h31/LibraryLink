import org.slf4j.LoggerFactory
import java.util.*

class Requests(private val localChannel: ThreadLocal<PythonChannelRunner> = ThreadLocal.withInitial { PythonChannelRunner() },
               private val exchange: ProcessDataExchange = SimpleTextProcessDataExchange(localChannel.get())) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(ProcessDataExchange::class.java)

    fun get(url: String): Response {
        val peResponse = makeRequest(Request(import = "requests", objectID = "requests",
                args = listOf(url), methodName = "get", doGetReturnValue = false))
        logger.info("Wrote get")
        val response = Response(peResponse.assignedID)
        return response
    }

    inner class Response(private val storedName: String): Handle(storedName) {
        fun statusCode(): Int {
            val response = makeRequest(Request(objectID = storedName, methodName = "status_code",
                    doGetReturnValue = true, import = "", args = listOf(), isProperty = true))
            return response.returnValue as? Int ?: throw IllegalArgumentException()
        }

        fun content(): ByteArray {
            val response = makeRequest(Request(objectID = storedName, methodName = "content",
                    doGetReturnValue = true, import = "", args = listOf(), isProperty = true))
            val returnValue = response.returnValue as? String ?: throw IllegalArgumentException()
            return Base64.getDecoder().decode(returnValue)
        }

        fun headers(): Map<String, String> {
            val response = makeRequest(Request(objectID = storedName, methodName = "headers",
                    doGetReturnValue = true, import = "", args = listOf(), isProperty = true))
            return response.returnValue as? Map<String, String> ?: throw IllegalArgumentException()
        }
    }
}