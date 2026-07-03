# Hello WebSocket - C++ Implementation

C++17 implementation of the Hello WebSocket protocol using a custom, dependency-free WebSocket layer (RFC 6455).

## Features

- **Zero external dependencies** — WebSocket transport, SHA-1, Base64, and binary codec are all implemented from scratch in header-only files
- **Cross-platform** — Works on Windows (Winsock2) and Linux/macOS (POSIX sockets)
- **Full protocol** — Implements all 13 message types from PROTOCOL.md
- **Thread-safe** — Background tasks (ping, time, kiss) run in separate threads with mutex-protected sends

## Project Structure

```
hello-websocket-cpp/
├── CMakeLists.txt           # Build configuration
├── common/
│   ├── codec.hpp            # Binary protocol codec (frame, messages, SHA-256)
│   └── ws.hpp               # WebSocket transport (sockets, SHA-1, Base64, framing)
├── server/
│   └── ws_server.cpp        # WebSocket server with background tasks
├── client/
│   └── ws_client.cpp        # WebSocket client
├── test/
│   └── test_codec.cpp       # 16 codec unit tests
├── scripts/
│   ├── build.sh             # Build script (Linux/macOS)
│   ├── build.ps1            # Build script (Windows)
│   ├── run-server.sh        # Run server (Linux/macOS)
│   ├── run-server.ps1       # Run server (Windows)
│   ├── run-client.sh        # Run client (Linux/macOS)
│   └── run-client.ps1       # Run client (Windows)
└── README.md
```

## Prerequisites

- **C++17** compiler (MSVC 2019+, GCC 9+, or Clang 10+)
- **CMake** 3.14+
- On Windows: Windows SDK (Winsock2 is included)
- On Linux: standard POSIX libraries

## Build

### Linux / macOS

```bash
./scripts/build.sh
```

Or manually:
```bash
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release
```

### Windows (PowerShell)

```powershell
.\scripts\build.ps1
```

Or manually:
```powershell
mkdir build; cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --config Release
```

## Run Tests

```bash
./build/test_codec         # Linux/macOS
.\build\Release\test_codec.exe   # Windows
```

## Run Server

```bash
./scripts/run-server.sh    # Linux/macOS
.\scripts\run-server.ps1   # Windows
```

Environment variables:
- `WS_PORT` — Port to listen on (default: 9898)

## Run Client

```bash
./scripts/run-client.sh    # Linux/macOS
.\scripts\run-client.ps1   # Windows
```

Environment variables:
- `WS_SERVER` — Server host (default: 127.0.0.1)
- `WS_PORT` — Server port (default: 9898)

## Protocol

See [../PROTOCOL.md](../PROTOCOL.md) for the canonical protocol specification.
