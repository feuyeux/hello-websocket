# Hello WebSocket - Swift Implementation

Swift implementation using the repository's dependency-free cross-platform socket and RFC 6455 layer.

## Project Structure

```
hello-websocket-swift/
├── Package.swift           # Swift Package Manager config
├── common/
│   ├── Codec.swift         # Binary protocol codec
│   ├── WebSocket.swift     # WebSocket transport layer
│   ├── TCPSocket.swift      # Cross-platform TCP socket
│   └── SHA1.swift           # SHA-1 implementation
├── server/
│   └── main.swift          # WebSocket server
├── client/
│   └── main.swift          # WebSocket client
├── Tests/
│   └── HelloWebSocketTests/CodecTests.swift
├── scripts/
│   ├── build.sh            # Build with SPM
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **Swift** 6.0+ (toolchain)

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
swift build
```

## Run Server

```bash
swift run
```

Environment variables:
- `WS_PORT` — Port to listen on (default: 9898)
- `WS_PATH` — WebSocket path (default: /ws)

## Run Client

```bash
./scripts/run-client.sh
```

Environment variables:
- `WS_SERVER` — Server host (default: 127.0.0.1)
- `WS_PORT` — Server port (default: 9898)
- `WS_PATH` — WebSocket path (default: /ws)

## Protocol

See [../PROTOCOL.md](../PROTOCOL.md) for the canonical protocol specification.
