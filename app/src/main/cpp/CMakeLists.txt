# Copyright 2026 PollNull

cmake_minimum_required(VERSION 3.22.1)
project(micutre_audio LANGUAGES CXX)

find_package(oboe REQUIRED CONFIG)

add_library(micutre-audio SHARED audio_engine.cpp)
target_compile_features(micutre-audio PRIVATE cxx_std_17)
target_compile_options(micutre-audio PRIVATE -Wall -Wextra -Werror=return-type)
target_link_options(micutre-audio PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384")
target_link_libraries(micutre-audio PRIVATE oboe::oboe log)
