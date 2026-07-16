# Hello WebSocket - Kotlin Implementation

Kotlin implementation of the Hello WebSocket protocol using [Ktor](https://ktor.io/) Server-WS and Client-WS.

## Project Structure

```
hello-websocket-kotlin/
├── build.gradle.kts        # Root Gradle config
├── settings.gradle.kts     # Gradle settings
├── common/
│   ├── build.gradle.kts
│   └── src/main/kotlin/org/feuyeux/ws/common/Codec.kt
├── server/
│   ├── build.gradle.kts
│   └── src/main/kotlin/org/feuyeux/ws/server/WsServer.kt
├── client/
│   ├── build.gradle.kts
│   └── src/main/kotlin/org/feuyeux/ws/client/WsClient.kt
├── scripts/
│   ├── build.sh            # Build with Gradle
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **JDK** 21+
- **Gradle** 9.3+ (or use the included wrapper)

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
./gradlew clean build -x test
```

## Run Server

```bash
./gradlew :server:installDist
./server/build/install/server/bin/server
```

Environment variables:
- `WS_PORT` — Port to listen on (default: 9898)
- `WS_PATH` — WebSocket path (default: /ws)

## Run Client

```bash
./gradlew :client:installDist
./client/build/install/client/bin/client
```

Environment variables:
- `WS_SERVER` — Server host (default: 127.0.0.1)
- `WS_PORT` — Server port (default: 9898)
- `WS_PATH` — WebSocket path (default: /ws)

## Protocol

See [../PROTOCOL.md](../PROTOCOL.md) for the canonical protocol specification.
