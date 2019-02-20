package benchmark

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import ru.spbstu.kspt.librarylink.*
import java.util.concurrent.TimeUnit

/**
 * https://habr.com/post/349914/
 */
@State(Scope.Benchmark)
open class WrapperState {
    lateinit var resp: Requests.Response

    @Setup
    fun setup() {
        LibraryLink.runner = DummyRunner(true, "/tmp/linktest")
        LibraryLink.exchange = ProtoBufDataExchange()
        val requests = Requests()
//        val headers = requests.getHeaders()
//        headers.update("X-Test", "Value")
        resp = requests.get("https://api.github.com/user")
    }
}

open class StatusCodeBenchmark {
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 100, time = 1, timeUnit = TimeUnit.SECONDS)
    @Fork(value = 1)
    @Benchmark
    fun statusCode(state: WrapperState, blackhole: Blackhole) {
        val resp = state.resp
        val statusCode = resp.statusCode()
        blackhole.consume(statusCode)
    }
}