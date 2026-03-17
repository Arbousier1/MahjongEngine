cmake_minimum_required(VERSION 3.21)

project(mahjongpaper_gb LANGUAGES CXX)

find_package(JNI REQUIRED)

add_library(mahjongpaper_gb SHARED
    src/gbmahjong_jni.cpp
    vendor/GB-Mahjong/mahjong/fan.cpp
    vendor/GB-Mahjong/mahjong/handtiles.cpp
    vendor/GB-Mahjong/mahjong/pack.cpp
    vendor/GB-Mahjong/mahjong/tile.cpp
)

target_include_directories(mahjongpaper_gb PRIVATE
    ${JNI_INCLUDE_DIRS}
    vendor/GB-Mahjong/mahjong
)
target_link_libraries(mahjongpaper_gb PRIVATE JNI::JNI)
target_compile_features(mahjongpaper_gb PRIVATE cxx_std_20)

set_target_properties(mahjongpaper_gb PROPERTIES
    OUTPUT_NAME "mahjongpaper_gb"
)

if (MSVC)
    target_compile_options(mahjongpaper_gb PRIVATE /utf-8)
endif()
