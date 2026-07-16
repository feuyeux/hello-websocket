# Hello WebSocket

Equivalent WebSocket server and client implementations in 12 programming
languages. Every implementation uses the binary protocol in
[PROTOCOL.md](PROTOCOL.md).

## Protocol

The canonical endpoint is:

```text
ws://<host>:9898/ws
```

Each binary WebSocket message contains one protocol frame:

```text
Offset  Size  Field
0       1     MAGIC        0x48 ('H')
1       1     VERSION      0x01
2       1     MSG_TYPE
3       1     FLAGS
4       4     PAYLOAD_LEN  uint32 big-endian
8       N     PAYLOAD
```

The protocol supports HELLO/BONJOUR, echo, application heartbeat,
time notification, OS/locale exchange, random-number hashing, disconnect,
and typed errors. Frames are limited to 1 MiB.

## Implementations

| Language | WebSocket implementation | Build tool |
|---|---|---|
| Java | Java-WebSocket | Maven |
| Kotlin | Ktor WebSockets | Gradle |
| Python | websockets | pip |
| Go | gorilla/websocket | Go modules |
| Rust | tokio-tungstenite | Cargo |
| C++ | dependency-free RFC 6455 layer | CMake |
| C# | System.Net.WebSockets | dotnet |
| Dart | dart:io WebSocket | Dart pub |
| PHP | Ratchet server + Pawl client | Composer |
| Swift | dependency-free socket/RFC 6455 layer | SwiftPM |
| Node.js | ws | npm |
| TypeScript | ws | npm |

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `WS_SERVER` | `127.0.0.1` | Client target host |
| `WS_PORT` | `9898` | Server/client port |
| `WS_PATH` | `/ws` | WebSocket endpoint path |

## Build and Test

Each language directory contains `scripts/build.sh`. Examples:

```bash
./hello-websocket-go/scripts/build.sh
./hello-websocket-python/scripts/build.sh
./hello-websocket-java/scripts/build.sh
./hello-websocket-rust/scripts/build.sh
```

The CI workflow builds and tests every implementation. Codec tests cover the
worked example, all message types, malformed frames, signed integer boundaries,
and multilingual UTF-8. Docker smoke tests verify HELLO/BONJOUR and PING/PONG.

Dependency updates are managed across all ecosystems by Dependabot, dependency
review, OSV scanning, and guarded auto-merge. See
[docs/DEPENDENCY_AUTOMATION.md](docs/DEPENDENCY_AUTOMATION.md).

## Run Locally

Start a server and client in separate terminals:

```bash
./hello-websocket-python/scripts/run-server.sh
./hello-websocket-go/scripts/run-client.sh
```

All clients use `/ws`, so different language implementations can be combined.
Set `WS_SERVER`, `WS_PORT`, or `WS_PATH` when required.

## Docker

```bash
cd docker
./build_image.sh --language java
./run_container.sh --language java --component server
./run_container.sh --language go --component client
./smoke_test_all.sh --server java
```

Set `IMAGE_TAG` to build, run, or push a tag other than `1.0.0`.

## Security Scope

This repository is an interoperability and teaching project. The included
servers use plain `ws://`; they do not implement authentication or
authorization, and `userId` is untrusted display metadata.

For deployment outside a trusted network, terminate TLS at a reverse proxy,
authenticate the upgrade request, restrict browser Origins, rate-limit
connections/messages, and run containers with an appropriate non-root policy.
