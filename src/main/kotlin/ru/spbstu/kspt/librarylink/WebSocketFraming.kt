package ru.spbstu.kspt.librarylink

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.javalin.Javalin
import org.slf4j.LoggerFactory

class Receiver {
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    private val logger = LoggerFactory.getLogger(Receiver::class.java)

    fun processRequest(request: MethodCallRequest): ChannelResponse {
        return ChannelResponse(123)
    }

    fun processRequest(request: ByteArray): ByteArray {
        val decodedRequest = mapper.readValue(request, MethodCallRequest::class.java)
        logger.info("Received callback request $request")
        val returnValue = processRequest(decodedRequest)
        val response = ChannelResponse(returnValue)
        val message = mapper.writeValueAsBytes(response)
        return message
    }
}

fun main(args: Array<String>) {
    val app = Javalin.create().start(7000)

    app.ws("/websocket/:path") { ws ->
        ws.onConnect { session -> println("Connected") }
        ws.onMessage { session, message ->
            println("Received: $message")
            session.remote.sendString("Echo: $message")
        }
        ws.onClose { session, statusCode, reason -> println("Closed") }
        ws.onError { session, throwable -> println("Errored") }
    }
}