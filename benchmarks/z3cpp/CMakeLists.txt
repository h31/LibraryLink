cmake_minimum_required(VERSION 3.13)
project(Z3Example C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

#set(Z3_DIR /home/artyom/Tools/z3-4.8.4.d6df51951f4c-x64-ubuntu-16.04)

find_package(Z3
        REQUIRED
        CONFIG
        )

include_directories(${PROJECT_SOURCE_DIR})
add_executable(Z3Example main.c)
add_executable(Z3ExampleBenchmark main.c benchmark.cpp)

target_include_directories(Z3Example PRIVATE ${Z3_C_INCLUDE_DIRS})
target_link_libraries(Z3Example PRIVATE ${Z3_LIBRARIES})

target_include_directories(Z3ExampleBenchmark PRIVATE ${Z3_C_INCLUDE_DIRS})
target_link_libraries(Z3ExampleBenchmark PRIVATE ${Z3_LIBRARIES})