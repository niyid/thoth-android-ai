cmake_minimum_required(VERSION 3.22.1)
project("llama_android")

# ── Source discovery ───────────────────────────────────────────────────────────
# llama.cpp sources should be present in this directory.
# Add via git submodule:
#   git submodule add https://github.com/ggml-org/llama.cpp \
#       llama-android/src/main/cpp/llama.cpp
# Then update CMakeLists.txt to include the subdirectory.

# Locate the JNI bridge implementation
file(GLOB JNI_SOURCES "${CMAKE_CURRENT_SOURCE_DIR}/llama_jni.cpp")

add_library(
    llama_android
    SHARED
    ${JNI_SOURCES}
)

# Link against Android log library (used in JNI code for __android_log_print)
find_library(log-lib log)
target_link_libraries(
    llama_android
    ${log-lib}
)

# C++17 required by llama.cpp
target_compile_features(llama_android PRIVATE cxx_std_17)
