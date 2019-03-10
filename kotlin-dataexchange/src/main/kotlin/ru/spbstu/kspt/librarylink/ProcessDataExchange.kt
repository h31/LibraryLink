package ru.spbstu.kspt.librarylink

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import jnr.unixsocket.UnixSocketAddress
import jnr.unixsocket.UnixSocketChannel
import org.slf4j.LoggerFactory
import java.io.*
import java.lang.StringBuilder
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.AbstractList
import kotlin.concurrent.thread
import kotlin.reflect.KProperty


fun mkfifo(path: String) {
    val exitCode = Runtime.getRuntime().exec(arrayOf("mkfifo", path)).waitFor()
    check(exitCode == 0)
}

object LibraryLink {
    lateinit var runner: ReceiverRunner
    var defaultExchange: ProcessDataExchange? = null
    var exchange: ProcessDataExchange by lazy {
        val default = defaultExchange
        if (default != null) {
            default
        } else {
            SimpleTextProcessDataExchange(runner)
        }
    }
}

private operator fun <T> Lazy<T>.setValue(libraryLink: LibraryLink, property: KProperty<*>, processDataExchange: ProcessDataExchange) {
    libraryLink.defaultExchange = processDataExchange
}

interface ForeignChannelManager {
    data class BidirectionalChannel(val inputStream: InputStream,
                                    val outputStream: OutputStream)

    fun getBidirectionalChannel(): BidirectionalChannel
    fun getBidirectionalChannel(subchannel: String): BidirectionalChannel
}

interface ReceiverRunner {
    val isMultiThreaded: Boolean
    val foreignChannelManager: ForeignChannelManager
    val requestGenerator: RequestGenerator
    val callbackDataExchange: CallbackDataExchange
    fun defaultRequestGenerator(): RequestGenerator = if (isMultiThreaded) {
        ThreadLocalRequestGenerator(foreignChannelManager, callbackDataExchange)
    } else {
        BlockingRequestGenerator(foreignChannelManager, callbackDataExchange)
    }

    fun stop()
}

open class Python3Runner(pathToScript: String, channelPrefix: String) : ReceiverRunner {
    private val pythonProcess: Process = ProcessBuilder("python3", System.getProperty("user.dir") + "/" + pathToScript, channelPrefix) // TODO
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()

    final override val isMultiThreaded = false
    final override val foreignChannelManager = UnixSocketChannelManager(channelPrefix)
    override val callbackDataExchange = CallbackDataExchange()
    override val requestGenerator = this.defaultRequestGenerator()

    val logger = LoggerFactory.getLogger(Python3Runner::class.java)

    override fun stop() {
        logger.info("Stop Python")
        pythonProcess.destroy()
    }
}

class DummyRunner(override val isMultiThreaded: Boolean = false,
                  channelPrefix: String) : ReceiverRunner {
    override val foreignChannelManager: ForeignChannelManager = UnixSocketChannelManager(channelPrefix)
    override val callbackDataExchange = CallbackDataExchange()
    override val requestGenerator: RequestGenerator = this.defaultRequestGenerator()

    override fun stop() = Unit // TODO: Use a callback
}

open class UnixSocketChannelManager(val channelPrefix: String) : ForeignChannelManager {
    val logger = LoggerFactory.getLogger(UnixSocketChannelManager::class.java)

    override fun getBidirectionalChannel(): ForeignChannelManager.BidirectionalChannel = connect()

    override fun getBidirectionalChannel(subchannel: String): ForeignChannelManager.BidirectionalChannel = connect()

    private fun connect(): ForeignChannelManager.BidirectionalChannel {
        val socketPath = File(channelPrefix)
        val address = UnixSocketAddress(socketPath)
        val channel = UnixSocketChannel.open(address)
        logger.info("connected to " + channel.remoteSocketAddress)

        val inputStream = Channels.newInputStream(channel)
        val outputStream = Channels.newOutputStream(channel)

        return ForeignChannelManager.BidirectionalChannel(inputStream, outputStream)
    }
}

enum class Tag(val code: Int) {
    REQUEST(0),
    RESPONSE(1),
    CALLBACK_REQUEST(2),
    CALLBACK_RESPONSE(3),
    OPEN_CHANNEL(4),
    DELETE_FROM_PERSISTENCE(5),
    START_BUFFERING(6),
    STOP_BUFFERING(7),
    STOP_RECEIVER(8);

    companion object {
        private val reverseValues: Map<Int, Tag> = values().associate { it.code to it }
        fun valueFrom(i: Int): Tag = reverseValues[i]!!
    }
}

interface Framing {
    fun write(tag: Tag, message: ByteArray)
    fun read(): Pair<Tag, ByteArray>
    var buffering: Boolean
}

//open class BorderFraming : Framing {
//    override fun write(os: OutputStream, message: ByteArray) {
//        os.write(message)
//        os.write(0)
//        os.flush()
//    }
//
//    override fun writeMultiple(writer: OutputStream, messages: ByteArray) {
//        val sb = StringBuffer()
//        for (message in messages) {
//            sb.append(message)
//            sb.append(String(ByteArray(1) { num -> 0 }))
//        }
//        writer.write(sb.toString())
//        writer.flush()
//    }
//
//    override fun read(reader: Reader): String {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//}

open class SimpleTextFraming(private val writer: Writer, private val reader: Reader) : Framing {
    override fun write(tag: Tag, message: ByteArray) {
        val decodedMsg = message.toString()
        writer.write("%04d".format(decodedMsg.length))
        writer.write(decodedMsg)
        writer.flush()
    }

    override fun read(): Pair<Tag, ByteArray> {
        val lengthBuffer = CharArray(4)
        var receivedDataSize = reader.read(lengthBuffer)
        check(receivedDataSize == 4)
        val length = String(lengthBuffer).toInt()

        val actualData = CharArray(length)
        receivedDataSize = reader.read(actualData)
        check(receivedDataSize == length)
        return Pair(Tag.RESPONSE, String(actualData).toByteArray()) // TODO
    }

    override var buffering: Boolean = false
        set(value) {
            TODO()
        }
}

open class SimpleBinaryFraming(baseInputStream: InputStream, baseOutputStream: OutputStream) : Framing {
    val bufferSize = 16 * 1024
    val inputStream = BufferedInputStream(baseInputStream, bufferSize)
    val outputStream = BufferedOutputStream(baseOutputStream, bufferSize)

    override fun write(tag: Tag, message: ByteArray) {
        outputStream.write(message.size.toByteArray())
        outputStream.write(tag.code.toByteArray())
        outputStream.write(message)
        if (!buffering) {
            outputStream.flush()
        }
    }

    private fun readByteArray(length: Int): ByteArray {
        val buffer = ByteArray(length)
        val receivedDataSize = inputStream.read(buffer)
        check(receivedDataSize == length) {
            "receivedDataSize($receivedDataSize) != length($length)"
        }
        return buffer
    }

    override fun read(): Pair<Tag, ByteArray> {
        val length = readByteArray(4).toInt()
        val tagCode = readByteArray(4).toInt()
        val tag = Tag.valueFrom(tagCode)

        val actualData = readByteArray(length)
        return Pair(tag, actualData)
    }

    override var buffering: Boolean = false
        set(value) {
            field = value
            write(if (value) Tag.START_BUFFERING else Tag.STOP_BUFFERING, ByteArray(0))
            outputStream.flush()
        }
}

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(this).array()

fun ByteArray.toInt(): Int = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).int

interface RequestGenerator {
    fun sendRequest(requestMessage: ByteArray): ByteArray
    fun sendRequests(requestMessages: List<ByteArray>): List<ByteArray>
    val framing: Framing

    fun readResponse(framing: Framing, callbackDataExchange: CallbackDataExchange): ByteArray {
        while (true) {
            val response = framing.read()
            if (response.first == Tag.CALLBACK_REQUEST) {
                val callbackResponse = callbackDataExchange.handleRequest(response.second)
                framing.write(Tag.CALLBACK_RESPONSE, callbackResponse)
            } else {
                return response.second
            }
        }
    }
}

open class BlockingRequestGenerator(private val channelManager: ForeignChannelManager, val callbackDataExchange: CallbackDataExchange) : RequestGenerator, ForeignChannelManager by channelManager {
    override val framing by lazy {
        val channel = getBidirectionalChannel()
        SimpleBinaryFraming(channel.inputStream, channel.outputStream)
    }

    override fun sendRequest(requestMessage: ByteArray): ByteArray {
        return synchronized(framing) {
            framing.write(Tag.REQUEST, requestMessage)
            readResponse(framing, callbackDataExchange)
        }
    }

    override fun sendRequests(requestMessages: List<ByteArray>): List<ByteArray> {
        return synchronized(framing) {
            framing.buffering = true
            for (message in requestMessages) {
                framing.write(Tag.REQUEST, message)
            }
            framing.buffering = false
            val responses = mutableListOf<ByteArray>()
            for (message in requestMessages) {
                responses += readResponse(framing, callbackDataExchange)
            }
            responses
        }
    }
}

open class ThreadLocalRequestGenerator(private val channelManager: ForeignChannelManager, val callbackDataExchange: CallbackDataExchange) : RequestGenerator, ForeignChannelManager by channelManager {
    private val logger = LoggerFactory.getLogger(ThreadLocalRequestGenerator::class.java)

    private val localFraming: ThreadLocal<Framing> = ThreadLocal.withInitial {
        val channelID = Thread.currentThread().id.toString()
        logger.info("Open new channel $channelID")
        val channel = channelManager.getBidirectionalChannel(channelID)
        SimpleBinaryFraming(channel.inputStream, channel.outputStream)
    }

    override val framing: Framing
        get() = localFraming.get()

    override fun sendRequest(requestMessage: ByteArray): ByteArray {
        val framing = localFraming.get()
        framing.write(Tag.REQUEST, requestMessage)
        return readResponse(framing, callbackDataExchange)
    }

    override fun sendRequests(requestMessages: List<ByteArray>): List<ByteArray> {
        framing.buffering = true
        for (message in requestMessages) {
            framing.write(Tag.REQUEST, message)
        }
        framing.buffering = false
        val responses = mutableListOf<ByteArray>()
        for (message in requestMessages) {
            responses += readResponse(framing, callbackDataExchange)
        }
        return responses
    }
}

interface Request {
    var assignedID: String
}

open class IdentifiableRequest : Request {
    override var assignedID: String = ""
    fun withID(assignedID: String): IdentifiableRequest {
        this.assignedID = assignedID
        return this
    }
}

data class MethodCallRequest @JvmOverloads constructor(
        val methodName: String,
        val objectID: String = "",
        val type: String = "",
        var args: List<Argument> = listOf(),
        @JsonProperty("static")
        val isStatic: Boolean = false,
        val doGetReturnValue: Boolean = false,
        @JsonProperty("property")
        val isProperty: Boolean = false) : IdentifiableRequest()

data class ConstructorRequest(
        val className: String,
        var args: List<Argument> = listOf()
) : IdentifiableRequest()

data class ImportRequest(
        val importedName: String
) : IdentifiableRequest()

data class EvalRequest(
        val executedCode: String,
        var args: List<Argument> = listOf(),
        val doGetReturnValue: Boolean = false
) : IdentifiableRequest()

data class DynamicInheritRequest(
        val importName: String,
        val automatonName: String,
        val inherits: String,
        val methodArguments: Map<String, List<Argument>>
) : IdentifiableRequest()

data class DynamicCallbackRequest(
        val arguments: List<Argument>
) : IdentifiableRequest()

data class PersistenceFetchRequest(
        val key: String
) : IdentifiableRequest()

//@JsonTypeInfo(
//        use = JsonTypeInfo.Id.NAME,
//        include = JsonTypeInfo.As.PROPERTY,
//        property = "type"
//)
//@JsonTypeIdResolver(ArgumentIdResolver::class)
class Argument {
    val type: String
    val value: Any?
    val key: String?

    @JvmOverloads
    constructor(obj: Handle, key: String? = null) {
        this.type = "persistence"
        this.value = obj.assignedID
        this.key = key
    }

    @JvmOverloads
    constructor(obj: Class<out Handle>, key: String? = null) {
        this.type = "persistence"
        this.value = "class_${obj.canonicalName}" // TODO
        this.key = key
    }

    @JvmOverloads
    constructor(obj: Number, key: String? = null) {
        this.type = "inplace"
        this.value = obj
        this.key = key
    }

    @JvmOverloads
    constructor(obj: Boolean, key: String? = null) {
        this.type = "inplace"
        this.value = obj
        this.key = key
    }

    @JvmOverloads
    constructor(obj: String, key: String? = null) {
        this.type = "inplace"
        this.value = obj
        this.key = key
    }

    @JsonCreator
    constructor(@JsonProperty("type") type: String,
                @JsonProperty("value") value: Any?,
                @JsonProperty("key") key: String?) {
        this.type = type
        this.value = value
        this.key = key
    }
}

//public class ArgumentIdResolver : TypeIdResolverBase() {
//    lateinit var superType: JavaType
//
//    override fun init(baseType: JavaType) {
//        superType = baseType;
//    }
//
//    override fun getMechanism(): JsonTypeInfo.Id {
//        return JsonTypeInfo.Id.NAME
//    }
//
//    override fun idFromValueAndType(value: Any?, suggestedType: Class<*>?): String {
//        return (value as Argument).type
//    }
//
//    override fun idFromValue(value: Any?): String {
//        return (value as Argument).type
//    }
//
//    override fun typeFromId(context: DatabindContext, id: String): JavaType {
//        val argClass = when (id) {
//            "raw" -> PersistenceArgument::class.java
//            else -> PersistenceArgument::class.java
//        }
//        return context.constructSpecializedType(superType, PersistenceArgument::class.java)
//    }
//}

//data class InPlaceArgument(override val value: Any,
//                           override val key: String? = null) : Argument {
//    override val type = "inplace"
//}
//
//data class PersistenceArgument @JvmOverloads constructor(val handle: Handle,
//                                                         override val key: String? = null,
//                                                         override val value: String = handle.assignedID) : Argument {
//    override val type = "persistence"
//}
//
//data class ClassObjectArgument @JvmOverloads constructor(val clazz: Class<Handle>,
//                                                         override val key: String? = null,
//                                                         override val value: String) : Argument {
//    override val type = "persistence"
//}

object AssignedIDCounter {
    private val counter = AtomicInteger()

    fun getNextID() = "var" + AssignedIDCounter.counter.getAndIncrement().toString()
}

class ChannelResponse(
        @JsonProperty("return_value") val returnValue: Any? = null,
        @JsonProperty("exception_message") val exceptionMessage: String? = null
)

interface ProcessExchangeResponse {
//    val rawValue: Any?
    val assignedID: String

    fun <T> asInstanceOf(clazz: Class<T>): T

    fun <T : Handle> bindTo(handle: T): T {
        handle.assignedID = this.assignedID
        handle.registerReference()
        return handle
    }
}

inline fun <reified T> ProcessExchangeResponse.asInstanceOf(): T = this.asInstanceOf(T::class.java)

data class PrimitiveProcessExchangeResponse(val rawValue: Any?,
                                            override val assignedID: String) : ProcessExchangeResponse {
    override fun <T> asInstanceOf(clazz: Class<T>): T {
        return when (clazz) {
            Long::class.java -> rawValue as? T ?: throw IllegalArgumentException()
            Int::class.java, Integer::class.java -> (rawValue as Long).toInt() as T
            Char::class.java, Character::class.java -> (rawValue as Long).toChar() as T
            else -> TODO()
        }
    }
}

data class HandleProcessExchangeResponse(override val assignedID: String) : ProcessExchangeResponse {
    override fun <T> asInstanceOf(clazz: Class<T>): T {
        if (Handle::class.java.isAssignableFrom(clazz)) {
            val handle = clazz.getConstructor().newInstance() as Handle
            handle.assignedID = this.assignedID
            return handle as T
        } else {
            throw IllegalArgumentException()
        }
    }
}

data class EmptyProcessExchangeResponse(override val assignedID: String) : ProcessExchangeResponse {
    override fun <T> asInstanceOf(clazz: Class<T>): T = throw IllegalArgumentException()
}

interface Handle {
    var assignedID: String
    fun registerReference() {
        val ref = HandlePhantomReference(this, HandleReferenceQueue.refqueue, assignedID)
        HandleReferenceQueue.references += ref
    }
}

open class HandleAutoGenerate : Handle {
    override lateinit var assignedID: String
}

open class DelayedAssignmentHandle : Handle {
    override lateinit var assignedID: String
}

open class ClassDecl<T : Handle>(clazz: Class<T>, importName: String, methodArguments: Map<String, List<Argument>>, exchange: ProcessDataExchange = LibraryLink.exchange) : Handle {
    override lateinit var assignedID: String

    init {
        exchange.makeRequest(DynamicInheritRequest(importName = importName,
                automatonName = clazz.simpleName, inherits = clazz.superclass.simpleName,
                methodArguments = methodArguments)).bindTo(this)

        exchange.registerConstructorCallback(clazz.simpleName) { req ->
            val instance = clazz.constructors.first { !it.isSynthetic }.newInstance() as DelayedAssignmentHandle  // TODO: Args
            instance.assignedID = req.objectID
            instance
        }
    }
}

//open class DataHandle(assignedID: String, val dataExchange: ProcessDataExchange) : HandleImpl(assignedID) {
//    fun asString(): String {
//        val content = dataExchange.createByRequest(MethodCallRequest("__read_data", args = listOf(Argument(this)))) // TODO
//        return content.returnValue as String
//    }
//}

inline fun <reified T> makeArray(size: Int): ArrayHandle<T> = ArrayHandle(size, T::class.java)
inline fun <reified T> Collection<T>.toArrayHandle(): ArrayHandle<T> = ArrayHandle(this, T::class.java)
fun <T> Collection<T>.toArrayHandle(clazz: Class<T>): ArrayHandle<T> = ArrayHandle(this, clazz)
inline fun <reified T> Array<T>.toArrayHandle(): ArrayHandle<T> = ArrayHandle(listOf(*this), T::class.java)

open class ArrayHandle<T>(final override var size: Int = 0, val clazz: Class<T>) : Handle, AbstractList<T>() {
    override lateinit var assignedID: String

    private val exchange = LibraryLink.exchange
    private val typeName = calculatePrimitiveTypeName(clazz)

    init {
        if (size != 0) {
            allocate()
        }
    }

    constructor(src: Collection<T>, clazz: Class<T>) : this(src.size, clazz) {
        src.withIndex().forEach { elem ->
            set(elem.index, elem.value)
        }
    }

    private fun calculatePrimitiveTypeName(clazz: Class<T>): String = when (clazz) {  // TODO: Very hacky
        Char::class.java, Character::class.java -> "char"
        Int::class.java, Integer::class.java -> "int"
        else -> clazz.simpleName
    }

    override operator fun get(index: Int): T {
        val resp = exchange.makeRequest(MethodCallRequest(
                methodName = "get<$typeName>",
                type = "$typeName[]",
                args = listOf(Argument(index)),
                objectID = assignedID,
                doGetReturnValue = true)) // TODO
        return resp.asInstanceOf(clazz)
    }

    fun allocate() {
        exchange.makeRequest(MethodCallRequest(
                methodName = "mem_alloc<$typeName>",
                type = "$typeName[]",
                args = listOf(Argument(size)))).bindTo(this) // TODO: Type parameter
    }

    operator fun set(index: Int, element: T): T {
        val previousValue: T = get(index)
        val valueArgument = when (element) {
            is Handle -> Argument(element)
            is Number -> Argument(element)
            is Char -> Argument(element.toInt()) // TODO
            else -> TODO()
        }
        exchange.makeRequest(MethodCallRequest(
                methodName = "set<$typeName>",
                type = "$typeName[]",
                args = listOf(Argument(index), valueArgument),
                objectID = assignedID,
                doGetReturnValue = false))
        return previousValue
    }
}

class CharArrayHandle(size: Int = 0) : ArrayHandle<Char>(size, Char::class.java)

fun ArrayHandle<Char>.strlen(): Int = LibraryLink.exchange
        .makeRequest(MethodCallRequest(
                methodName = "strlen",
                type = "char[]",
                args = listOf(),
                objectID = assignedID,
                doGetReturnValue = true))
        .asInstanceOf()

fun ArrayHandle<Char>.strlen_test(): Int {
    for (i in 0..100) {
        val char = get(i)
        if (char.toInt() == 0) {
            return i
        }
    }
    throw IllegalArgumentException()
}

fun ArrayHandle<Char>.toStringOfSize(sizeSource: (ArrayHandle<Char>) -> Int = { this.strlen() }): String { // TODO: Very hacky
    val size = sizeSource(this)
    val sb = StringBuilder(size)
    for (i in 0 until size) {
        val char = get(i)
        sb.append(char)
    }
    return sb.toString()
}

fun String.toArrayHandle(): ArrayHandle<Char> = ArrayHandle(this.toList(), Char::class.java)

object HandleReferenceQueue {
    val refqueue = ReferenceQueue<Any>()
    val references: MutableSet<HandlePhantomReference<Any>> = mutableSetOf()
}

class HandlePhantomReference<T>(referent: T, q: ReferenceQueue<T>, val assignedID: String) : PhantomReference<T>(referent, q)

interface CallbackRegistrable {
    fun registerCallback(methodName: String, type: String, receiver: CallbackReceiver) {
        registerCallback(methodName, type) { req, _ -> receiver(req) to null }
    }
    fun registerCallback(methodName: String, type: String, receiver: (request: MethodCallRequest, handle: Handle?) -> Any?)
    fun registerConstructorCallback(className: String, receiver: (request: MethodCallRequest) -> Handle)
}

interface ProcessDataExchange : CallbackRegistrable {
    fun makeRequest(request: Request): ProcessExchangeResponse
    fun makeRequests(requests: List<Request>): List<ProcessExchangeResponse>
}

interface CallbackReceiver {
    operator fun invoke(request: MethodCallRequest): Any?
}

open class CallbackDataExchange : CallbackRegistrable {
    private val logger = LoggerFactory.getLogger(CallbackDataExchange::class.java)

    private val callbackReceiversMap: MutableMap<Pair<String, String>, (request: MethodCallRequest, handle: Handle?) -> Any?> = mutableMapOf()

    private val callbackConstructorsMap: MutableMap<String, (request: MethodCallRequest) -> Handle> = mutableMapOf()

    private val localPersistence: MutableMap<String, Handle?> = mutableMapOf()

    override fun registerCallback(methodName: String, type: String, receiver: (request: MethodCallRequest, handle: Handle?) -> Any?) {
        callbackReceiversMap += Pair(methodName, type) to receiver
    }

    override fun registerConstructorCallback(className: String, receiver: (request: MethodCallRequest) -> Handle) {
        callbackConstructorsMap += className to receiver
    }

    fun handleRequest(callbackRequest: ByteArray): ByteArray {
        val requestWrapped = Exchange.Request.parseFrom(callbackRequest)
        if (!requestWrapped.hasMethodCall()) {
            TODO()
        }
        val request = requestWrapped.methodCall.toMethodCallRequest()
        logger.info("Received callback request $request")
        val existingObject = localPersistence[request.assignedID]
        val obj = existingObject ?: callbackConstructorsMap[request.objectID]?.invoke(request)
        if (existingObject == null) {
            localPersistence[request.assignedID] = obj
        }
        val callback = callbackReceiversMap[request.methodName to request.type] ?: TODO()
        val returnValue = callback(request, obj) // TODO
        val valueBuilder = Exchange.Value.newBuilder()
        when (returnValue) {
            is Number -> valueBuilder.intValue = returnValue.toLong()
            is Char -> valueBuilder.intValue = returnValue.toLong()
            is String -> valueBuilder.stringValue = returnValue
        }
        val response = Exchange.ChannelResponse.newBuilder()
                .setAssignedId(request.assignedID)
                .setReturnValue(valueBuilder)
                .build()
        logger.info("Responded with ${response}")
        return response.toByteArray()
    }

    private fun Exchange.MethodCallRequest.toMethodCallRequest(): MethodCallRequest = MethodCallRequest(
            methodName = this.methodName,
            objectID = this.objectId,
            type = this.type,
            args = listOf(),
            isStatic = this.static,
            doGetReturnValue = this.doGetReturnValue,
            isProperty = this.property
    )
}

class LibraryLinkException(message: String) : Exception(message)

open class ProtoBufDataExchange(val runner: ReceiverRunner = LibraryLink.runner,
                                val requestGenerator: RequestGenerator = runner.requestGenerator) : ProcessDataExchange,
        RequestGenerator by requestGenerator, CallbackRegistrable by runner.callbackDataExchange {
    override fun makeRequest(request: Request): ProcessExchangeResponse {
        val requestBinary = request.toProtobuf()
        val responseBinary: ByteArray = requestGenerator.sendRequest(requestBinary)
        val channelResponse = Exchange.ChannelResponse.parseFrom(responseBinary)
        return channelResponse.toProcessExchangeResponse()
    }

    override fun makeRequests(requests: List<Request>): List<ProcessExchangeResponse> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun Exchange.ChannelResponse.toProcessExchangeResponse(): ProcessExchangeResponse {
        return when (this.resultCase) {
            Exchange.ChannelResponse.ResultCase.RETURN_VALUE ->
                when (this.returnValue.valueCase) {
                    Exchange.Value.ValueCase.INT_VALUE -> PrimitiveProcessExchangeResponse(rawValue = this.returnValue.intValue, assignedID = this.assignedId)
                    Exchange.Value.ValueCase.UINT_VALUE -> PrimitiveProcessExchangeResponse(rawValue = this.returnValue.uintValue, assignedID = this.assignedId)
                    Exchange.Value.ValueCase.STRING_VALUE -> PrimitiveProcessExchangeResponse(rawValue = this.returnValue.stringValue, assignedID = this.assignedId)
                    Exchange.Value.ValueCase.VALUE_NOT_SET, null -> throw IllegalArgumentException()
                }
            Exchange.ChannelResponse.ResultCase.RESULT_NOT_SET,
            null ->
                HandleProcessExchangeResponse(assignedID = this.assignedId)
            Exchange.ChannelResponse.ResultCase.EXCEPTION_MESSAGE ->
                throw LibraryLinkException(this.exceptionMessage)
        }
    }

    private fun Request.toProtobuf(): ByteArray {
        val builder = Exchange.Request.newBuilder()
        builder.assignedId = this.assignedID
        when (this) {
            is MethodCallRequest -> builder.methodCall = this.toProtobuf()
            is ImportRequest -> builder.importation = this.toProtobuf()
            is ConstructorRequest -> builder.constructor = this.toProtobuf()
            else -> TODO()
        }
        return builder.build().toByteArray()
    }

    private fun MethodCallRequest.toProtobuf(): Exchange.MethodCallRequest {
        val builder = Exchange.MethodCallRequest.newBuilder()
        builder.methodName = this.methodName
        builder.type = this.type
        builder.objectId = this.objectID
        builder.addAllArg(this.args.map { it.toProtobuf() })
        builder.static = this.isStatic
        builder.doGetReturnValue = this.doGetReturnValue
        builder.property = this.isProperty
        return builder.build()
    }

    private fun ImportRequest.toProtobuf(): Exchange.ImportRequest =
            Exchange.ImportRequest.newBuilder()
                    .setImportedName(this.importedName)
                    .build()

    private fun ConstructorRequest.toProtobuf(): Exchange.ConstructorRequest =
            Exchange.ConstructorRequest.newBuilder()
                    .setClassName(this.className)
                    .addAllArg(this.args.map { it.toProtobuf() })
                    .build()

    private fun Argument.toProtobuf(): Exchange.Argument {
        val argBuilder = Exchange.Argument.newBuilder()
        argBuilder.type = when (this.type) {
            "persistence" -> Exchange.Argument.ArgumentType.PERSISTENCE
            "inplace" -> Exchange.Argument.ArgumentType.INPLACE
            else -> throw IllegalArgumentException("arg.type == ${this.type}")
        }
        val valueBuilder = Exchange.Value.newBuilder()
        when (this.value) {
            is Number -> valueBuilder.intValue = this.value.toLong()
            is Char -> valueBuilder.intValue = this.value.toLong()
            is String -> valueBuilder.stringValue = this.value
        }
        argBuilder.value = valueBuilder.build()
        argBuilder.key = this.key ?: ""
        return argBuilder.build()
    }
}

open class SimpleTextProcessDataExchange(val runner: ReceiverRunner = LibraryLink.runner,
                                         val requestGenerator: RequestGenerator = runner.requestGenerator) : ProcessDataExchange,
        RequestGenerator by requestGenerator, CallbackRegistrable by runner.callbackDataExchange {
    override fun makeRequests(requests: List<Request>): List<ProcessExchangeResponse> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    val mapper = ObjectMapper().registerModule(KotlinModule())

    val logger = LoggerFactory.getLogger(ProcessDataExchange::class.java)

    private val logFileWriter = File("link.log").writer()

    init {
        thread {
            logger.info("Waiting to ReferenceQueue elements")
            while (true) {
                val ref = HandleReferenceQueue.refqueue.remove() as HandlePhantomReference
                logger.info("${ref.assignedID} was deleted, sending a message")
                val message = mapper.writeValueAsString(mapOf("delete" to ref.assignedID))
                makeChannelRequest(message) // TODO
            }
        }
    }

    private fun logMessage(requestMessage: String) {
        logFileWriter.write(requestMessage + "\n")
        logFileWriter.flush()
    }

    private fun makeChannelRequest(requestMessage: String): ChannelResponse {
        logMessage(requestMessage)
        val responseText = sendRequest(requestMessage.toByteArray())
        val response = mapper.readValue(responseText, ChannelResponse::class.java)
        return response
    }

    override fun makeRequest(request: Request): ProcessExchangeResponse {
        val message = mapper.writeValueAsString(request)
        logger.info("Wrote $request")
        val channelResponse = makeChannelRequest(message)
        if (channelResponse.exceptionMessage != null) {
            throw LibraryLinkException(channelResponse.exceptionMessage)
        }
        val response = PrimitiveProcessExchangeResponse(rawValue = channelResponse.returnValue, assignedID = "") // TODO
        logger.info("Received $response")
        return response
    }

    @Deprecated("Old protocol")
    fun makeRequest(exec: String?, eval: String?, store: String?, description: String) {
        val request = mutableMapOf<String, String>()
        if (exec != null) request += "exec" to exec
        if (store != null) request += "store" to store
        if (eval != null) request += "eval" to eval
        val message = mapper.writeValueAsString(request)
        makeChannelRequest(message)
        logger.info("Wrote $description")
    }
}

class CachingProcessDataExchange(val backend: ProcessDataExchange, val cacheManager: CacheManager) : ProcessDataExchange by backend {
    override fun makeRequest(request: Request): ProcessExchangeResponse {
        val cached = currentSequence.poll()
        val response: ProcessExchangeResponse = if (cached != null && cached.first == request) {
            cached.second
        } else {
            if (currentSequence.isNotEmpty()) currentSequence.clear()
            val list = cacheManager.getPrefetchRequests(request)
            if (list.isEmpty()) {
                backend.makeRequest(request)
            } else {
                val newElements = backend.makeRequests(list)
                list.zip(newElements).toCollection(currentSequence)
                currentSequence.poll().second
            }
        }
        return response
    }

    private val currentSequence = ArrayDeque<Pair<Request, ProcessExchangeResponse>>()
}

interface CacheManager {
    fun getPrefetchRequests(request: Request): List<Request>
}

class DynamicCacheManager(fileName: String) : CacheManager {
    private val allowedMethodCallSequences = mutableSetOf<Pair<String, String>>()
    private var lastRequest: Request? = null
    private val currentSequence = mutableListOf<MethodCallRequest>()
    private val requestSequences = mutableMapOf<Request, List<Request>>()
    private final val MAX_SIZE = 5

    init {
        File(fileName).forEachLine { line ->
            val elements = line.split(" ")
            check(elements.size == 2)
            allowedMethodCallSequences += elements.first() to elements.last()
        }
    }

    private fun addToSequence(request: Request): List<Request> {
        if (request !is MethodCallRequest) {
            return emptyList()
        }
        if (currentSequence.isEmpty()) {
            currentSequence += request
            return emptyList()
        }
        val name1 = currentSequence.last().methodName
        val name2 = request.methodName
        if (name1 to name2 in allowedMethodCallSequences && currentSequence.size < MAX_SIZE) {
            currentSequence += request
            return emptyList()
        } else {
            if (currentSequence.size > 1) {
                requestSequences += currentSequence.first() to currentSequence.toList()
            }
            currentSequence.clear()
        }
        currentSequence += request
        return emptyList()
    }

    override fun getPrefetchRequests(request: Request): List<Request> {
//        return requestSequences[request] ?: addToSequence(request)
        return emptyList()
    }
}

class NoopCacheManager : CacheManager {
    override fun getPrefetchRequests(request: Request): List<Request> = emptyList()
}