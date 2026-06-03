cmake_minimum_required(VERSION 3.22.1)
project("llama_android" CXX)

# ── Path to the llama.cpp submodule ──────────────────────────────────────────
# Passed in from build.gradle.kts as -DLLAMA_SOURCE_DIR=<path>.
if(NOT DEFINED LLAMA_SOURCE_DIR)
    message(FATAL_ERROR
        "LLAMA_SOURCE_DIR not set.\n"
        "Run: git submodule update --init --recursive\n"
        "then rebuild."
    )
endif()

# ── llama.cpp core library ────────────────────────────────────────────────────
# We compile only the files needed for basic CPU inference.
# GPU / CUDA / Metal / Vulkan back-ends are excluded here to keep the build
# self-contained on the NDK.  Add GGML_VULKAN=ON etc. when the NDK supports it.

set(LLAMA_SRC "${LLAMA_SOURCE_DIR}")

# Gather the source files that make up the llama.cpp runtime.
# This mirrors the file list in llama.cpp's own CMakeLists for the cpu target.
file(GLOB GGML_SOURCES
    "${LLAMA_SRC}/ggml/src/ggml.c"
    "${LLAMA_SRC}/ggml/src/ggml-alloc.c"
    "${LLAMA_SRC}/ggml/src/ggml-backend.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-backend-reg.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu.c"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu-aarch64.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu-quants.c"
    "${LLAMA_SRC}/ggml/src/ggml-threading.cpp"
)

file(GLOB LLAMA_SOURCES
    "${LLAMA_SRC}/src/llama.cpp"
    "${LLAMA_SRC}/src/llama-arch.cpp"
    "${LLAMA_SRC}/src/llama-batch.cpp"
    "${LLAMA_SRC}/src/llama-chat.cpp"
    "${LLAMA_SRC}/src/llama-context.cpp"
    "${LLAMA_SRC}/src/llama-grammar.cpp"
    "${LLAMA_SRC}/src/llama-kv-cache.cpp"
    "${LLAMA_SRC}/src/llama-model.cpp"
    "${LLAMA_SRC}/src/llama-model-loader.cpp"
    "${LLAMA_SRC}/src/llama-sampling.cpp"
    "${LLAMA_SRC}/src/llama-vocab.cpp"
    "${LLAMA_SRC}/src/unicode.cpp"
    "${LLAMA_SRC}/src/unicode-data.cpp"
)

# Build ggml and llama as static libs so the JNI bridge links against them.
add_library(ggml STATIC ${GGML_SOURCES})
target_include_directories(ggml PUBLIC
    "${LLAMA_SRC}/ggml/include"
    "${LLAMA_SRC}/ggml/src"
    "${LLAMA_SRC}/ggml/src/ggml-cpu"
)
target_compile_definitions(ggml PRIVATE GGML_USE_CPU)

add_library(llama_core STATIC ${LLAMA_SOURCES})
target_include_directories(llama_core PUBLIC
    "${LLAMA_SRC}/include"
    "${LLAMA_SRC}/src"
    "${LLAMA_SRC}/ggml/include"
)
target_link_libraries(llama_core ggml)

# ── JNI bridge ───────────────────────────────────────────────────────────────
add_library(llama_android SHARED llama_jni.cpp)
target_include_directories(llama_android PRIVATE
    "${LLAMA_SRC}/include"
    "${LLAMA_SRC}/ggml/include"
)
target_link_libraries(llama_android
    llama_core
    ggml
    android
    log
)
target_compile_options(llama_android PRIVATE -std=c++17 -O3)
