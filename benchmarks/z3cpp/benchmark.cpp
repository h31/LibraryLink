//
// Created by artyom on 23.05.19.
//

#include <chrono>
#include <iostream>
#include <benchmark/benchmark.h>
#include "benchmark.h"

void simple_benchmark_demorgan() {
    std::chrono::steady_clock::time_point begin = std::chrono::steady_clock::now();
    std::chrono::steady_clock::time_point end = std::chrono::steady_clock::now();

    demorgan();

    std::cout << "Time difference micro = " << std::chrono::duration_cast<std::chrono::microseconds>(end - begin).count() <<std::endl;
    std::cout << "Time difference nano  = " << std::chrono::duration_cast<std::chrono::nanoseconds> (end - begin).count() <<std::endl;
}

static void googlebenchmark_demorgan(benchmark::State& state) {
    for (auto _ : state) {
        demorgan();
    }
}

BENCHMARK(googlebenchmark_demorgan);

BENCHMARK_MAIN();