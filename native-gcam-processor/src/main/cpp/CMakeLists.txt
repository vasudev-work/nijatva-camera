cmake_minimum_required(VERSION 3.22)
project(gcamprocessor LANGUAGES CXX)

add_library(gcamprocessor SHARED processor.cpp)

find_library(log-lib log)
target_link_libraries(gcamprocessor ${log-lib})
