package benchmark

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import ru.spbstu.kspt.librarylink.*
import java.util.concurrent.TimeUnit

/**
 * https://habr.com/post/349914/
 */
@State(Scope.Benchmark)
open class BytesState {
    lateinit var bytes: SocketServerWrapper.Bytes

    @Setup
    fun setup() {
        LibraryLink.runner = DummyRunner(false, "/tmp/linktest")
        LibraryLink.exchange = ProtoBufDataExchange()
        val testString = "0".repeat(10000)
        bytes = SocketServerWrapper.Bytes(testString)
    }
}

open class Bytes {
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 100, time = 1, timeUnit = TimeUnit.SECONDS)
    @Fork(value = 1)
//    @Benchmark
    fun statusCode(state: BytesState, blackhole: Blackhole) {
        val bytes = state.bytes
        val statusCode = bytes[0]
        blackhole.consume(statusCode)
    }
}
