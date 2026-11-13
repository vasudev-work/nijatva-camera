cmake_minimum_required(VERSION 3.22)
project(gcamprocessor LANGUAGES CXX)

add_library(gcamprocessor SHARED
        processor.cpp
        raw_develop.cpp
        pipeline_core.cpp
        pipeline_jni.cpp)

target_include_directories(gcamprocessor PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

find_library(log-lib log)
target_link_libraries(gcamprocessor ${log-lib})
