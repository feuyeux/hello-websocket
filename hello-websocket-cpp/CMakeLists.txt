cmake_minimum_required(VERSION 3.14)
project(hello-websocket-cpp CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Build type
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Warnings
if(MSVC)
    add_compile_options(/W4 /permissive-)
else()
    add_compile_options(-Wall -Wextra -Wpedantic)
endif()

# ─── Server ───────────────────────────────────────────────────────────────

add_executable(ws_server
    server/ws_server.cpp
)
target_include_directories(ws_server PRIVATE common)

# Link Winsock on Windows
if(WIN32)
    target_link_libraries(ws_server PRIVATE ws2_32)
endif()

# ─── Client ───────────────────────────────────────────────────────────────

add_executable(ws_client
    client/ws_client.cpp
)
target_include_directories(ws_client PRIVATE common)

if(WIN32)
    target_link_libraries(ws_client PRIVATE ws2_32)
endif()

# ─── Tests ─────────────────────────────────────────────────────────────────

add_executable(test_codec
    test/test_codec.cpp
)
target_include_directories(test_codec PRIVATE common)

if(WIN32)
    target_link_libraries(test_codec PRIVATE ws2_32)
endif()

enable_testing()
add_test(NAME test_codec COMMAND test_codec)

# ─── Install ───────────────────────────────────────────────────────────────

install(TARGETS ws_server ws_client test_codec
    RUNTIME DESTINATION bin
)
