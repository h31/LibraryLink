package ru.spbstu.kspt.librarylink;

import com.microsoft.z3.Context;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.HashMap;

@State(Scope.Benchmark)
public class Z3JavaJNIBenchmarkState {
    Context ctx;
    @Setup
    public void setup() {
        HashMap<String, String> cfg = new HashMap<String, String>();
        ctx = new Context(cfg);
    }
}
