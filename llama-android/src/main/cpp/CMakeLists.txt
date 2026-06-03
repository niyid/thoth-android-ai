cmake_minimum_required(VERSION 3.22.1)
project("llama_android" CXX C)

if(NOT DEFINED LLAMA_SOURCE_DIR)
    message(FATAL_ERROR
        "LLAMA_SOURCE_DIR not set. Add to build.gradle.kts cmake arguments:\n"
        "  \"-DLLAMA_SOURCE_DIR=\${projectDir}/../third_party/llama.cpp\""
    )
endif()

set(LLAMA_SRC "${LLAMA_SOURCE_DIR}")

macro(add_if_exists LIST FILE)
    if(EXISTS "${FILE}")
        list(APPEND ${LIST} "${FILE}")
    endif()
endmacro()

# ── ggml sources ──────────────────────────────────────────────────────────────
set(GGML_SOURCES "")

# Core ggml (required)
foreach(_f
    "${LLAMA_SRC}/ggml/src/ggml.c"
    "${LLAMA_SRC}/ggml/src/ggml-alloc.c"
    "${LLAMA_SRC}/ggml/src/ggml-backend.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-backend-reg.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-threading.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu.c"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/ggml-cpu.cpp"
)
    if(NOT EXISTS "${_f}")
        message(FATAL_ERROR "Required source missing: ${_f}\nRun: git submodule update --init --recursive")
    endif()
    list(APPEND GGML_SOURCES "${_f}")
endforeach()

# CPU backend — actual filenames from this revision of llama.cpp
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-cpu/quants.c")
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-cpu/traits.cpp")
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-cpu/vec.cpp")
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-cpu/ops.cpp")
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-cpu/hbm.cpp")
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-cpu/repack.cpp")
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-cpu/binary-ops.cpp")
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-cpu/unary-ops.cpp")

# Optional top-level ggml extras
add_if_exists(GGML_SOURCES "${LLAMA_SRC}/ggml/src/ggml-opt.cpp")

add_library(ggml STATIC ${GGML_SOURCES})

# ── Inject GGML_VERSION / GGML_COMMIT as -D defines ─────────────────────────
# This revision has no ggml-version.h.in; ggml.c expects them as preprocessor
# macros (-DGGML_VERSION="..."), NOT as a header.
execute_process(
    COMMAND git -C "${LLAMA_SRC}" describe --tags --abbrev=8
    OUTPUT_VARIABLE _GGML_GIT_DESC
    OUTPUT_STRIP_TRAILING_WHITESPACE
    ERROR_QUIET
)
if(NOT _GGML_GIT_DESC)
    execute_process(
        COMMAND git -C "${LLAMA_SRC}" rev-parse --short=8 HEAD
        OUTPUT_VARIABLE _GGML_GIT_DESC
        OUTPUT_STRIP_TRAILING_WHITESPACE
        ERROR_QUIET
    )
endif()
if(NOT _GGML_GIT_DESC)
    set(_GGML_GIT_DESC "unknown")
endif()

target_include_directories(ggml PUBLIC
    "${LLAMA_SRC}/ggml/include"
    "${LLAMA_SRC}/ggml/src"
    "${LLAMA_SRC}/ggml/src/ggml-cpu"
)

target_compile_definitions(ggml PRIVATE
    GGML_USE_CPU
    GGML_VERSION="${_GGML_GIT_DESC}"
    GGML_COMMIT="${_GGML_GIT_DESC}"
)

# ── llama_core sources ────────────────────────────────────────────────────────
file(GLOB _ALL_LLAMA_SRC "${LLAMA_SRC}/src/*.cpp")
list(FILTER _ALL_LLAMA_SRC EXCLUDE REGEX ".*(server|cli|main)[^/]*\\.cpp$")

if(NOT _ALL_LLAMA_SRC)
    message(FATAL_ERROR "No llama.cpp sources found under ${LLAMA_SRC}/src/\nRun: git submodule update --init --recursive")
endif()

add_library(llama_core STATIC ${_ALL_LLAMA_SRC})

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

target_link_libraries(llama_android PRIVATE
    llama_core
    ggml
    android
    log
    atomic
    m
)

target_compile_options(llama_android PRIVATE -std=c++17 -O3)
