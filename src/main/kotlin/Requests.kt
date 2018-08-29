import org.slf4j.LoggerFactory
import java.util.*

class Requests(private val localChannel: ThreadLocal<PythonChannelRunner> = ThreadLocal.withInitial { PythonChannelRunner() },
               private val exchange: ProcessDataExchange = SimpleTextProcessDataExchange(localChannel.get())) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(ProcessDataExchange::class.java)

    fun get(url: String): Response {
        val assignedID = makeRequestSeparated(Request(import = "requests", objectID = "requests",
                args = listOf(url), methodName = "get", doGetReturnValue = false))
        logger.info("Wrote get")
        receiveResponse()
        val response = Response(assignedID!!)
        return response
    }

    inner class Response(private val storedName: String): Handle(storedName) {
        fun statusCode(): Int {
            makeRequestSeparated(Request(objectID = storedName, methodName = "status_code",
                    doGetReturnValue = true, import = "", args = listOf(), isProperty = true))
            val responseText = receiveResponse()
            val returnValue = responseText.returnValue
            if (returnValue != null && returnValue is Int) {
                return returnValue
            } else {
                throw IllegalArgumentException()
            }
        }

        fun content(): ByteArray {
            makeRequestSeparated(Request(objectID = storedName, methodName = "content",
                    doGetReturnValue = true, import = "", args = listOf(), isProperty = true))
            val responseText = receiveResponse()
            val returnValue = responseText.returnValue
            if (returnValue != null && returnValue is String) {
                return Base64.getDecoder().decode(returnValue)
            } else {
                throw IllegalArgumentException()
            }
        }

        fun headers(): Map<String, String> {
            makeRequest(eval = "$storedName.headers", description = "headers")
            val responseText = receiveResponse()
            val returnValue = responseText.returnValue
            if (returnValue != null && returnValue is Map<*, *>) {
                return returnValue as Map<String, String>
            } else {
                throw IllegalArgumentException()
            }
        }
    }
}