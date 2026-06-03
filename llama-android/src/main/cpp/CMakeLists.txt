cmake_minimum_required(VERSION 3.22.1)

# C must be listed alongside CXX so .c files get a compile rule.
# Without it, CMake silently skips ggml.c / ggml-alloc.c / ggml-cpu.c,
# leaving ggml_cpu_init, ggml_abort, ggml_graph_compute, etc. undefined.
project("llama_android" CXX C)

# ── Submodule path ────────────────────────────────────────────────────────────
# Passed in from build.gradle.kts: cmake { arguments "-DLLAMA_SOURCE_DIR=..." }
if(NOT DEFINED LLAMA_SOURCE_DIR)
    message(FATAL_ERROR
        "LLAMA_SOURCE_DIR not set.\n"
        "Add to build.gradle.kts externalNativeBuild cmake arguments:\n"
        "  \"-DLLAMA_SOURCE_DIR=\${projectDir}/../third_party/llama.cpp\"\n"
        "and run: git submodule update --init --recursive"
    )
endif()

set(LLAMA_SRC "${LLAMA_SOURCE_DIR}")

# ── ggml (tensor engine) ──────────────────────────────────────────────────────
# Core .c files (ggml.c, ggml-alloc.c) and the CPU backend .c/.cpp files all
# need to land in this target.  Using explicit paths so we don't accidentally
# pull in Vulkan/Metal/CUDA backends that won't compile under the NDK.
set(GGML_SOURCES
    "${LLAMA_SRC}/ggml/src/ggml.c"
    "${LLAMA_SRC}/ggml/src/ggml-alloc.c"
    "${LLAMA_SRC}/ggml/src/ggml-backend.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-backend-reg.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-threading.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu.c"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu-aarch64.cpp"
)

# ggml-cpu-quants may be .c or .cpp depending on the llama.cpp revision.
foreach(_ext c cpp)
    set(_quants "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu-quants.${_ext}")
    if(EXISTS "${_quants}")
        list(APPEND GGML_SOURCES "${_quants}")
        break()
    endif()
endforeach()

# Some revisions also have ggml-opt.cpp at the ggml/src level.
foreach(_opt
    "${LLAMA_SRC}/ggml/src/ggml-opt.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu-traits.cpp"
)
    if(EXISTS "${_opt}")
        list(APPEND GGML_SOURCES "${_opt}")
    endif()
endforeach()

add_library(ggml STATIC ${GGML_SOURCES})

target_include_directories(ggml PUBLIC
    "${LLAMA_SRC}/ggml/include"
    "${LLAMA_SRC}/ggml/src"
    "${LLAMA_SRC}/ggml/src/ggml-cpu"
)

target_compile_definitions(ggml PRIVATE
    GGML_USE_CPU
    # Suppress x86 feature symbols on arm64 — they're queried at runtime but
    # the stubs below keep the linker happy if the version doesn't guard them.
    GGML_CPU_HAS_NEON
)

# ── llama_core ────────────────────────────────────────────────────────────────
# Enumerate all .cpp files from llama.cpp/src/ that are needed for CPU
# inference.  New files are added between llama.cpp releases so we GLOB the
# whole directory and then exclude anything platform-specific.
file(GLOB _ALL_LLAMA_SRC "${LLAMA_SRC}/src/*.cpp")

# Exclude files that require non-NDK system headers or GPU runtimes.
list(FILTER _ALL_LLAMA_SRC EXCLUDE REGEX ".*llama-server.*")
list(FILTER _ALL_LLAMA_SRC EXCLUDE REGEX ".*llama-cli.*")

set(LLAMA_SOURCES ${_ALL_LLAMA_SRC})

add_library(llama_core STATIC ${LLAMA_SOURCES})

target_include_directories(llama_core PUBLIC
    "${LLAMA_SRC}/include"
    "${LLAMA_SRC}/src"
    "${LLAMA_SRC}/ggml/include"
)

target_link_libraries(llama_core PUBLIC ggml)

target_compile_options(llama_core PRIVATE -std=c++17 -O2)

# ── JNI bridge ────────────────────────────────────────────────────────────────
add_library(llama_android SHARED llama_jni.cpp)

target_include_directories(llama_android PRIVATE
    "${LLAMA_SRC}/include"
    "${LLAMA_SRC}/ggml/include"
)

# Link order matters with ld.lld + --no-undefined:
# llama_android → llama_core → ggml (each resolves symbols from the next).
target_link_libraries(llama_android PRIVATE
    llama_core
    ggml
    android
    log
    atomic
    m
)

target_compile_options(llama_android PRIVATE -std=c++17 -O3)
