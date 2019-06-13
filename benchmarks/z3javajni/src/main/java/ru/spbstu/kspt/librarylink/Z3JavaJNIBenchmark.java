/*
 * Copyright (c) 2014, Oracle America, Inc.
 */

package ru.spbstu.kspt.librarylink;

import com.microsoft.z3.Context;
import com.microsoft.z3.Log;
import com.microsoft.z3.Symbol;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.HashMap;

public class Z3JavaJNIBenchmark {
    static Blackhole blackhole;

    @Benchmark
    public void testMethod(Z3JavaJNIBenchmarkState state, Blackhole blackhole) {
//        Z3JavaJNIBenchmark.blackhole = blackhole;
//
//        JavaExample p = new JavaExample();
//        Log.open("/dev/null");

//        HashMap<String, String> cfg = new HashMap<String, String>();
////        cfg.put("proof", "true");
//        Context ctx = new Context(cfg);

//        p.findModelExample2(ctx);
//        p.proveExample1(ctx);
        blackhole.consume("");
    }
}
