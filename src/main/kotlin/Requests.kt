import java.util.*

class Requests : ProcessDataExchange() {
    fun get(url: String): Response {
        makeRequestSeparated(Request(import = "requests", objectID = "requests",
                args = listOf(url), methodName = "get", doGetReturnValue = false))
//        makeRequest(exec = "import requests; r = requests.get('$url')",
//                store = "r", description = "get")
        logger.info("Wrote get")
        receiveResponse()
        return Response("var0")
    }

    inner class Response(private val storedName: String) {
        fun statusCode(): Int {
            makeRequest(eval = "$storedName.status_code", description = "statusCode")
            val responseText = receiveResponse()
            val returnValue = responseText["return_value"]
            if (returnValue != null && returnValue is Int) {
                return returnValue
            } else {
                throw IllegalArgumentException()
            }
        }

        fun content(): ByteArray {
            makeRequest(eval = "$storedName.content", description = "content")
            val responseText = receiveResponse()
            val returnValue = responseText["return_value"]
            if (returnValue != null && returnValue is String) {
                return Base64.getDecoder().decode(returnValue)
            } else {
                throw IllegalArgumentException()
            }
        }

        fun headers(): Map<String, String> {
            makeRequest(eval = "$storedName.headers", description = "headers")
            val responseText = receiveResponse()
            val returnValue = responseText["return_value"]
            if (returnValue != null && returnValue is Map<*, *>) {
                return returnValue as Map<String, String>
            } else {
                throw IllegalArgumentException()
            }
        }
    }
}