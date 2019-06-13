package ru.spbstu.kspt.librarylink;

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole

@State(Scope.Benchmark)
open class Z3State {
    lateinit var ctx: Z3Kotlin.Z3_context

    @Setup
    fun setup() {
        LibraryLink.runner = DummyRunner(false, "/tmp/linktest")
        LibraryLink.exchange = ProtoBufDataExchange()
        val cfg = Z3Kotlin.Z3_config();
        ctx = cfg.Z3_mk_context();
    }
}

open class Z3JavaJNIBenchmark {
    @Benchmark
    public fun testMethod(state: Z3State, blackhole: Blackhole) {
        blackhole.consume(state.ctx.Z3_mk_string_symbol("abc".toArrayHandle()))
    }
}
