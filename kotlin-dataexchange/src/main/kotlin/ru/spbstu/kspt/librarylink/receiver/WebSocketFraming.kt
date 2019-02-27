package ru.spbstu.kspt.librarylink.receiver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.javalin.Javalin
import org.slf4j.LoggerFactory
import ru.spbstu.kspt.librarylink.ChannelResponse
import ru.spbstu.kspt.librarylink.Exchange
import ru.spbstu.kspt.librarylink.MethodCallRequest

class Receiver {
    private val mapper = ObjectMapper().registerModule(KotlinModule())

    private val logger = LoggerFactory.getLogger(Receiver::class.java)

    fun processRequest(request: MethodCallRequest): ChannelResponse {
        return ChannelResponse(123)
    }

    fun processRequest(message: ByteArray): ByteArray {
        val request = Exchange.Request.parseFrom(message)
        logger.info("Received callback request $request")
//        val returnValue = processRequest(decodedRequest)
        val response = Exchange.ChannelResponse.newBuilder().build()
        val message = response.toByteArray()
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