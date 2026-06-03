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
    "${LLAMA_SRC}/ggml/src/ggml-quants.c"           # _ref scalar impls called by ggml-cpu/quants.c
    "${LLAMA_SRC}/ggml/src/ggml-backend.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-backend-reg.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-backend-meta.cpp"   # ggml_backend_buffer_is_meta et al.
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

# arch/ — ARM-specific quantize_row_*_ref and ggml_vec_dot_* implementations
# GLOB recursively so we catch aarch64/, arm/, fallback/ etc. whatever this revision uses
file(GLOB_RECURSE _GGML_ARCH_SRCS
    "${LLAMA_SRC}/ggml/src/ggml-cpu/arch/*.c"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/arch/*.cpp"
)
list(APPEND GGML_SOURCES ${_GGML_ARCH_SRCS})

# llamafile/ — may contain additional scalar kernels referenced by quants.c
file(GLOB _GGML_LLAMAFILE_SRCS
    "${LLAMA_SRC}/ggml/src/ggml-cpu/llamafile/*.c"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/llamafile/*.cpp"
)
list(APPEND GGML_SOURCES ${_GGML_LLAMAFILE_SRCS})

# gguf — provides ALL gguf_* symbols (gguf_init_empty, gguf_free, gguf_find_key, etc.)
# This file is required; fail loudly if it is absent rather than silently producing
# a broken .so with hundreds of undefined symbols at link time.
if(EXISTS "${LLAMA_SRC}/ggml/src/gguf.cpp")
    list(APPEND GGML_SOURCES "${LLAMA_SRC}/ggml/src/gguf.cpp")
elseif(EXISTS "${LLAMA_SRC}/src/gguf.cpp")
    list(APPEND GGML_SOURCES "${LLAMA_SRC}/src/gguf.cpp")
else()
    message(FATAL_ERROR
        "gguf.cpp not found under ${LLAMA_SRC}.\n"
        "Run: git submodule update --init --recursive"
    )
endif()

# ggml-dl — provides dl_load_library / dl_get_sym / dl_error used by ggml-backend-reg.cpp
# The filename varies across revisions (ggml-dl.cpp, ggml-dynamic-loader.cpp, etc.)
# so we glob rather than hard-code.
file(GLOB _GGML_DL_SRCS
    "${LLAMA_SRC}/ggml/src/ggml-dl*.cpp"
    "${LLAMA_SRC}/ggml/src/ggml-dl*.c"
    "${LLAMA_SRC}/ggml/src/ggml-dynamic*.cpp"
)
if(_GGML_DL_SRCS)
    list(APPEND GGML_SOURCES ${_GGML_DL_SRCS})
else()
    message(WARNING
        "No ggml-dl*.cpp found in ${LLAMA_SRC}/ggml/src/. "
        "If the build fails with undefined dl_load_library / dl_get_sym / dl_error, "
        "locate the file providing those symbols and add it here."
    )
endif()

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
    "${LLAMA_SRC}/ggml/src/ggml-cpu/arch"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/arch/arm"
    "${LLAMA_SRC}/ggml/src/ggml-cpu/llamafile"
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
    dl
    m
)

target_compile_options(llama_android PRIVATE -std=c++17 -O3)
