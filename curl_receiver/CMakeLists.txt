cmake_minimum_required(VERSION 3.12)
project(CurlReceiver)

set(CMAKE_CXX_STANDARD 17)
find_package(CURL REQUIRED)
find_package(FMT REQUIRED)
find_package(Threads REQUIRED)

add_executable(CurlReceiver main.cpp)
target_link_libraries(CurlReceiver ${CURL_LIBRARIES})
target_link_libraries(CurlReceiver fmt::fmt)
target_link_libraries(CurlReceiver Threads::Threads)