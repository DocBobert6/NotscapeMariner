cmake_minimum_required(VERSION 3.22.1)
project(notscape_retro)

# Oboe (audio) is fetched and hash-pinned by CI into
# ${OBOE_DIR} - see .github/workflows/android.yml. It is
# built from source there and never vendored into this
# repository.
if(NOT DEFINED OBOE_DIR)
    message(FATAL_ERROR "OBOE_DIR is not set (the CI fetch step provides it)")
endif()
add_subdirectory(${OBOE_DIR} oboe-build)

# The libretro frontend shim: hosts a core .so loaded by
# path at runtime (cores are separate libraries, fetched
# and hash-pinned by CI into jniLibs, never linked here).
add_library(notscape_retro SHARED retro_shim.cpp)
target_compile_features(notscape_retro PRIVATE cxx_std_17)
target_include_directories(notscape_retro PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(notscape_retro
    oboe
    EGL
    GLESv2
    android
    log
    dl
)
