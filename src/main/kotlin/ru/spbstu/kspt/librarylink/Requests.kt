package ru.spbstu.kspt.librarylink

import org.slf4j.LoggerFactory
import java.util.*

class Requests(private val exchange: ProcessDataExchange = LibraryLink.exchange) : ProcessDataExchange by exchange {
    val logger = LoggerFactory.getLogger(Requests::class.java)

    fun get(url: String, headers: Headers? = null): Response {
        val args = mutableListOf<Argument>(InPlaceArgument(url))
        if (headers != null) {
            args += PersistenceArgument(headers.storedName, key = "headers")
        }
        val peResponse = makeRequest(MethodCallRequest(objectID = "requests",
                args = args, methodName = "get", doGetReturnValue = false))
        logger.info("Wrote get")
        val response = Response(peResponse.assignedID)
        return response
    }

    inner class Response(private val storedName: String): ru.spbstu.kspt.librarylink.Handle(storedName) {
        fun statusCode(): Int {
            val response = makeRequest(MethodCallRequest(objectID = storedName, methodName = "status_code",
                    doGetReturnValue = true, args = listOf(), isProperty = true))
            return response.returnValue as? Int ?: throw IllegalArgumentException()
        }

        fun content(): ByteArray {
            val response = makeRequest(MethodCallRequest(objectID = storedName, methodName = "content",
                    doGetReturnValue = true, args = listOf(), isProperty = true))
            val returnValue = response.returnValue as? String ?: throw IllegalArgumentException()
            return Base64.getDecoder().decode(returnValue)
        }

        fun headers(): Map<String, String> {
            val response = makeRequest(MethodCallRequest(objectID = storedName, methodName = "headers",
                    doGetReturnValue = true, args = listOf(), isProperty = true))
            return response.returnValue as? Map<String, String> ?: throw IllegalArgumentException()
        }
    }
}

class Headers(private val exchange: ProcessDataExchange = LibraryLink.exchange): ru.spbstu.kspt.librarylink.Handle(), ProcessDataExchange by exchange {
    val storedName: String
    init {
        val response = makeRequest(MethodCallRequest(methodName = "dict", args = listOf()))
        storedName = response.assignedID
        registerReference(storedName)
    }

    fun update(key: String, value: String) {
        makeRequest(EvalRequest( executedCode = "%s.update{%s: %s}", args = listOf(PersistenceArgument(storedName), InPlaceArgument(key), InPlaceArgument(value))))
    }
}
