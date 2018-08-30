import org.slf4j.LoggerFactory
import java.util.*

class Requests(private val localChannel: ThreadLocal<PythonChannelRunner> = ThreadLocal.withInitial { PythonChannelRunner() },
               private val exchange: ProcessDataExchange = SimpleTextProcessDataExchange(localChannel.get())) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(ProcessDataExchange::class.java)

    fun get(url: String, headers: Headers? = null): Response {
        val args: List<String>
        if (headers == null) {
            args = listOf(url)
        } else {
            args = listOf(url, "__headers = " + headers.storedName)
        }
        val peResponse = makeRequest(Request(import = "requests", objectID = "requests",
                args = args, methodName = "get", doGetReturnValue = false))
        logger.info("Wrote get")
        val response = Response(peResponse.assignedID)
        return response
    }

    fun getHeaders(): Headers = Headers()

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

    inner class Headers() {
        private val handle: Handle
        val storedName: String
        init {
            val response = makeRequest(Request(import = "", objectID = "", methodName = "dict", args = listOf()))
            storedName = response.assignedID
            handle = Handle(storedName)
        }

        fun update(key: String, value: String) {
            makeRequest(Request(import = "", objectID = storedName, methodName = "update", args = listOf("__{\"$key\": \"$value\"}")))
        }
    }
}