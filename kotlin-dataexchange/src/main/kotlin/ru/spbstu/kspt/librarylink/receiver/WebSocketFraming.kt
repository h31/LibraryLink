package ru.spbstu.kspt.librarylink.receiver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.javalin.Javalin
import io.javalin.websocket.WsSession
import org.slf4j.LoggerFactory
import ru.spbstu.kspt.librarylink.*
import java.nio.ByteBuffer
import kotlin.concurrent.thread

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

fun main() {
    val app = Javalin.create().start(7000)
    LibraryLink.runner = DummyRunner(true, "/tmp/linktest")
    LibraryLink.exchange = ProtoBufDataExchange()

    val sessions = mutableSetOf<WsSession>()

    val channel = LibraryLink.runner.foreignChannelManager.getBidirectionalChannel()

    app.ws("/librarylink/:path") { ws ->
        ws.onConnect { session -> println("Connected") }
        ws.onMessage { session, msg, offset, length ->
            val actualMsg = msg.copyOfRange(offset, offset + length)
            channel.outputStream.write(length.toByteArray())
            channel.outputStream.write(actualMsg.toByteArray())
            if (session !in sessions) {
                sessions += session
                thread {
                    while (true) {
                        val msgLength = ByteArray(4)
                        channel.inputStream.read(msgLength)
                        val msgData = ByteArray(msgLength.toInt())
                        channel.inputStream.read(msgData)
                        println("Received: $msgData")
                        session.remote.sendBytes(ByteBuffer.wrap(msgData))
                    }
                }
            }
        }
        ws.onClose { session, statusCode, reason -> println("Closed") }
        ws.onError { session, throwable -> println("Errored") }
    }
}