package ru.spbstu.kspt.librarylink

class SocketServerWrapper(private val exchange: ProcessDataExchange = LibraryLink.exchange) : ProcessDataExchange by exchange {
    init {
        makeRequest(ImportRequest("socketserver"))
    }

    open class BaseRequestHandler(existingID: String? = null, private val exchange: ProcessDataExchange = LibraryLink.exchange) : Handle() {
        var obtainedID = false

        init {
            if (existingID == null) {
                // TODO
//                val resp = exchange.makeRequest(ConstructorRequest(className = classID.assignedID))
//                registerReference(classID.assignedID)
                exchange.registerCallback("handle") { req, obj ->
                    (obj as BaseRequestHandler).handle()
                }
            } else {
                registerReference(existingID)
            }
        }

        fun request(): Request {
            val resp = exchange.makeRequest(MethodCallRequest(
                    methodName = "request",
                    objectID = assignedID,
                    isProperty = true
            ))
            val req = Request()
            req.registerReference(resp.assignedID)
            return req
        }

        open fun handle() {
            println("Default handler")
        }
    }

    class TCPServer(server_addr: Tuple, handler: ClassDecl<out BaseRequestHandler>, bind_and_activate: Boolean = true, private val exchange: ProcessDataExchange = LibraryLink.exchange) : Handle() {
        init {
            val resp = exchange.makeRequest(ConstructorRequest(
                    className = "socketserver.TCPServer",
                    args = listOf(Argument(server_addr), Argument(handler), Argument(bind_and_activate, key = "bind_and_activate"))))
            registerReference(resp.assignedID)
        }

        fun allow_reuse_address(value: Boolean) {
            val response = exchange.makeRequest(EvalRequest(executedCode = "{}.allow_reuse_address = {}",
                    doGetReturnValue = false, args = listOf(Argument(this), Argument(value))))
        }

        fun server_bind() {
            exchange.makeRequest(MethodCallRequest(
                    methodName = "server_bind",
                    objectID = assignedID
            ))
        }

        fun server_activate() {
            exchange.makeRequest(MethodCallRequest(
                    methodName = "server_activate",
                    objectID = assignedID
            ))
        }

        fun serve_forever() {
            exchange.makeRequest(MethodCallRequest(
                    methodName = "serve_forever",
                    objectID = assignedID
            ))
        }
    }

    class Request(private val exchange: ProcessDataExchange = LibraryLink.exchange) : Handle() {
        fun recv(size: Int): Bytes {
            val resp = exchange.makeRequest(MethodCallRequest(
                    methodName = "recv",
                    objectID = assignedID,
                    args = listOf(Argument(size))
            ))
            val bytes = Bytes()
            bytes.registerReference(resp.assignedID)
            return bytes
        }

//        fun sendall(data: Bytes) {
//
//        }
    }

    class Bytes(private val exchange: ProcessDataExchange = LibraryLink.exchange) : Handle() {
        operator fun get(i: Int): Byte {
            val resp = exchange.makeRequest(EvalRequest(
                    executedCode = "{}[{}]",
                    doGetReturnValue = true,
                    args = listOf(Argument(this), Argument(i))
            ))
            return (resp.returnValue as Int).toByte()
        }

        operator fun set(i: Int, value: Byte) {
            val resp = exchange.makeRequest(EvalRequest(
                    executedCode = "{}[{}] = {}",
                    doGetReturnValue = false,
                    args = listOf(Argument(this), Argument(i), Argument(value))
            ))
        }
    }
}

fun len(handle: Handle): Int {
    val resp = LibraryLink.exchange.makeRequest(MethodCallRequest(
            methodName = "len",
            args = listOf(Argument(handle)),
            doGetReturnValue = true
    ))
    return resp.returnValue as Int
}

class Tuple(values: List<Any?>, private val exchange: ProcessDataExchange = LibraryLink.exchange) : Handle() {
    init {
        val resp = exchange.makeRequest(EvalRequest(
                executedCode = generateSequence { "{}" }.take(values.size).joinToString(prefix = "(", postfix = ")"),
                args = values.map {
                    when (it) {
                        is Int -> Argument(it)
                        is String -> Argument(it)
                        is Handle -> Argument(it)
                        else -> TODO()
                    }
                },
                doGetReturnValue = true))
        registerReference(resp.assignedID)
    }
}