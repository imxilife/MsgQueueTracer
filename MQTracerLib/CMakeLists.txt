
cmake_minimum_required(VERSION 3.4.1)

project(XStackHook)

include_directories(src/main/cpp)

aux_source_directory(src/main/cpp/art SRC_DIR_ART)
include_directories(src/main/cpp/art)

aux_source_directory(src/main/cpp/sliver SRC_DIR_SLIVER)
include_directories(src/main/cpp/sliver)
include_directories(src/main/cpp/utils)

set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_SOURCE_DIR}/jnilib/${ANDROID_ABI})


add_library(
        stackHook
        SHARED
        ${SRC_DIR_ART}
        ${SRC_DIR_SLIVER}
        src/main/cpp/Sliver.cpp
)


find_library(
        log-lib
        log)

find_package(xdl REQUIRED CONFIG)

target_link_libraries(
        stackHook
        ${log-lib}
        xdl::xdl
        )