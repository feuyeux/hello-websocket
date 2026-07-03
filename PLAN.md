# Hello Websocket - Complete Implementation Plan

Benchmark: `/d/coding/hello-grpc/` (12-language gRPC reference project)
Target: `/d/coding/hello-websocket/` - 12 programming languages, server AND client, complete WebSocket functionality, fully dockerized.

---

## 0. Executive Summary

This plan rebuilds `hello-websocket` to mirror the structure, maturity, and Docker tooling of `hello-grpc`, but for the WebSocket protocol instead of gRPC. The reference project (`hello-grpc`) demonstrates how to ship an identical contract across 12 languages with per-language multi-stage Dockerfiles, `build_image.sh` / `run_container.sh` / `push_image.sh`, and per-language CI. We adopt that exact playbook and add a `docker-compose.yml` layer for one-command orchestration.

Current state of `hello-websocket`: 6 inconsistent subprojects (Java/Netty+SpringBoot, Go, Rust, Python, Node.js, Flutter) with mismatched ports (9898 vs 9800), mismatched protocols (custom binary vs JSON vs plain text), no client for Node.js, and ZERO Dockerfiles. The `docker/` and `diagram/` directories are empty. Two conflicting specs live in the repo (`prompt.md` vs `README.md`).

End state: 12 language subprojects, each implementing the same canonical binary WebSocket protocol with both server and client, each with a multi-stage Dockerfile, build/run scripts, and CI; plus a root `docker-compose.yml` (all-up) and `docker-compose.<lang>.yml` (per-language); plus a canonical `PROTOCOL.md` contract shared across all 12 languages (analogous to `hello-grpc/proto/landing.proto`).

---

## 1. Locked Decisions

These decisions were confirmed during brainstorming and govern the entire plan.

| Decision | Choice | Rationale |
|:---------|:-------|:----------|
| Canonical protocol | README protocol, formalized into PROTOCOL.md | Single shared contract all 12 languages implement; mirrors hello-grpc/proto/landing.proto |
| Languages | hello-grpc exact 12: Java, Go, Rust, C++, C#, Python, Node.js, Dart, Kotlin, Swift, PHP, TypeScript | Maximum parity with the sibling reference project |
| Port | 9898 | Matches the majority of existing implementations; clean and memorable |
| Wire format | Binary frames, custom codec | Compact, closer to a real protocol; the existing Java/Python binary codecs inform the canonical design |
| Message set | Full protocol: handshake (hello/bonjour) + echo + kiss (OS to locale) + ping/pong + time broadcast + random to hash | Demonstrates the full README flow in every language |
| Dockerization | Full mirror of hello-grpc docker system PLUS docker-compose | Per-language multi-stage Dockerfiles, build_image.sh, run_container.sh, push_image.sh, image naming feuyeux/ws_server_<lang>:1.0.0 / feuyeux/ws_client_<lang>:1.0.0, plus docker-compose.yml (all-up) and docker-compose.<lang>.yml (per-language) |

### 1.1 Language to Library Matrix

Mirrors the hello-grpc README matrix table. Each language uses its idiomatic WebSocket library.

| # | Language | WebSocket Library | Build System | Docker Base Image |
|:--|:---------|:------------------|:-------------|:------------------|
| 1 | Java | Netty 4.2 (netty-codec-http, netty-handler) | Maven | maven:3.9-eclipse-temurin-25 then eclipse-temurin:25-jre-alpine |
| 2 | Go | gorilla/websocket v1.5 | Go Modules | golang:1.23-alpine |
| 3 | Rust | tokio-tungstenite 0.29 | Cargo | rust:1.81-alpine3.20 |
| 4 | C++ | uWebSockets (or websocketpp fallback) | CMake | debian:12-slim with build deps |
| 5 | C# | System.Net.WebSockets (ASP.NET Core server + ClientWebSocket) | dotnet / NuGet | mcr.microsoft.com/dotnet/sdk:8.0 then runtime:8.0 |
| 6 | Python | websockets >=12.0 | pip / venv | python:3.11-slim |
| 7 | Node.js | ws ^8.14 | npm | node:21-alpine |
| 8 | TypeScript | ws + @types/ws, compiled with tsc | npm + TSC | node:21-alpine |
| 9 | Dart | web_socket_channel (shelf_web_socket for server) | pub | dart:3 |
| 10 | Kotlin | ktor 3.x (ktor-server-websockets, ktor-client-websockets) | Gradle | gradle:8-jdk-21 then eclipse-temurin:21-jre-alpine |
| 11 | Swift | Vapor 4 (server WebSocket) + URLSessionWebSocketTask (client) | SPM | swift:6.0.1-slim |
| 12 | PHP | Ratchet (server, ReactPHP) + textalk/websocket (client) | Composer | composer:2.8 then php:8.3-cli |


---

## 2. Canonical Protocol Specification (PROTOCOL.md)

This is the single contract every one of the 12 languages implements. It lives at repo root as `PROTOCOL.md` and is the WebSocket analog of `hello-grpc/proto/landing.proto`. The design merges the README.md protocol (EchoRequest/EchoResponse + KissRequest/KissResponse) with prompt.md interaction flows (handshake, ping/pong, time broadcast, random to hash), all carried over a length-prefixed binary wire format.

### 2.1 Design Goals

1. Language-agnostic - encodable/decodable in all 12 languages with no external schema compiler (no protobuf step; each language ships its own codec against this doc).
2. Self-delimiting - every message carries its own length so it can be parsed safely from a single WebSocket binary frame.
3. Extensible - a version byte and a reserved flags byte leave room for future additions (compression, new message types) without breaking existing parsers.
4. Symmetric - the same envelope and primitive encodings are used for every message type, in both directions.

### 2.2 Frame Envelope

Every WebSocket binary frame carries exactly one message wrapped in this 8-byte header plus payload:

```
Offset  Size  Field        Description
------  ----  -----------  -------------------------------------------------
0       1     MAGIC        Constant 0x48 ('H'). Reject frames that do not start with this.
1       1     VERSION      Constant 0x01. Parsers MUST check and reject unknown versions.
2       1     MSG_TYPE     Message type discriminator (see section 2.4).
3       1     FLAGS        Reserved for future use. MUST be 0x00. Parsers MUST ignore unknown bits.
4       4     PAYLOAD_LEN  uint32, big-endian. Number of bytes in the payload (may be 0).
8       N     PAYLOAD      N = PAYLOAD_LEN bytes, encoded per section 2.3 and the message definition.
```

- Total fixed header = 8 bytes.
- A frame with MSG_TYPE not in the registry (section 2.4) is a protocol error: the receiver SHOULD send an ERROR message (section 2.5.13) with code 0x02 (UNKNOWN_MESSAGE_TYPE) and MAY close the connection.
- PAYLOAD_LEN larger than the remaining frame bytes is a protocol error (code 0x03, TRUNCATED_PAYLOAD).

### 2.3 Primitive Encodings

All multi-byte integers are big-endian, unsigned unless stated.

| Primitive | Encoding |
|:----------|:---------|
| u8  | 1 byte, unsigned |
| u16 | 2 bytes, big-endian, unsigned |
| u32 | 4 bytes, big-endian, unsigned |
| u64 | 8 bytes, big-endian, unsigned |
| i32 | 4 bytes, big-endian, two's complement, signed |
| i64 | 8 bytes, big-endian, two's complement, signed |
| string | u32 byte-length (big-endian) followed by that many UTF-8 bytes. Empty string = length 0. |
| kv (map of string to string) | u32 entry-count (big-endian), then for each entry: a string key followed by a string value. Zero entries = count 0. |
| array of T | u32 element-count (big-endian), then that many T elements laid out consecutively. |


### 2.4 Message Type Registry

| Code | Constant | Name | Direction | Payload ref |
|:-----|:---------|:-----|:----------|:------------|
| 0x01 | MSG_HELLO | Hello | Client to Server | 2.5.1 |
| 0x02 | MSG_BONJOUR | Bonjour | Server to Client | 2.5.2 |
| 0x03 | MSG_ECHO_REQUEST | Echo Request | Client to Server | 2.5.3 |
| 0x04 | MSG_ECHO_RESPONSE | Echo Response | Server to Client | 2.5.4 |
| 0x05 | MSG_KISS_REQUEST | Kiss Request | Server to Client | 2.5.5 |
| 0x06 | MSG_KISS_RESPONSE | Kiss Response | Client to Server | 2.5.6 |
| 0x07 | MSG_PING | Ping | Server to Client | 2.5.7 |
| 0x08 | MSG_PONG | Pong | Client to Server | 2.5.8 |
| 0x09 | MSG_TIME_NOTIFICATION | Time Notification | Server to Client | 2.5.9 |
| 0x0A | MSG_RANDOM_NUMBER | Random Number | Client to Server | 2.5.10 |
| 0x0B | MSG_HASH_RESPONSE | Hash Response | Server to Client | 2.5.11 |
| 0x0C | MSG_DISCONNECT | Disconnect | Client to Server | 2.5.12 |
| 0x7F | MSG_ERROR | Error | Both directions | 2.5.13 |

### 2.5 Payload Definitions

#### 2.5.1 HELLO (0x01) - Client to Server
Sent by the client immediately after the WebSocket handshake completes. Announces the client implementation language.

| Field | Type | Notes |
|:------|:-----|:------|
| client_language | string | Human-readable language name, e.g. "Go", "Rust", "Java" |

#### 2.5.2 BONJOUR (0x02) - Server to Client
Sent by the server in response to HELLO, after logging the requester session id and request time.

| Field | Type | Notes |
|:------|:-----|:------|
| server_language | string | Server implementation language, e.g. "Java" |

#### 2.5.3 ECHO_REQUEST (0x03) - Client to Server
An upstream request carrying a client-side language tag and arbitrary data.

| Field | Type | Notes |
|:------|:-----|:------|
| id | i64 | Request id (correlates request/response) |
| meta | string | Client-side language tag |
| data | string | Arbitrary request payload |

#### 2.5.4 ECHO_RESPONSE (0x04) - Server to Client
The server response to an ECHO_REQUEST. Mirrors the README RESPONSE shape.

| Field | Type | Notes |
|:------|:-----|:------|
| status | i32 | HTTP-style status: 200 OK, 500 error |
| results | array of EchoResult | Zero or more results (see below) |

EchoResult layout:

| Field | Type | Notes |
|:------|:-----|:------|
| idx | i64 | Timestamp / index |
| type | u8 | 0 = OK, 1 = ERROR |
| kv | kv (map of string to string) | Arbitrary key-value pairs (e.g. id, idx, data, meta) |

#### 2.5.5 KISS_REQUEST (0x05) - Server to Client
A downstream request where the server asks the client for OS/environment info.

| Field | Type | Notes |
|:------|:-----|:------|
| os_name | string | e.g. "Linux", "Darwin", "Windows" |
| os_version | string | e.g. "10.0.19042" |
| os_release | string | e.g. "10" |
| os_architecture | string | e.g. "AMD64", "arm64" |

#### 2.5.6 KISS_RESPONSE (0x06) - Client to Server
The client reply to a KISS_REQUEST, carrying locale info.

| Field | Type | Notes |
|:------|:-----|:------|
| language | string | e.g. "en_US" |
| encoding | string | e.g. "UTF-8" |
| time_zone | string | e.g. "UTC", "Asia/Shanghai" |

#### 2.5.7 PING (0x07) - Server to Client
Heartbeat probe sent by the server on a fixed interval.

| Field | Type | Notes |
|:------|:-----|:------|
| timestamp_ms | i64 | Server time, milliseconds since Unix epoch |

#### 2.5.8 PONG (0x08) - Client to Server
Heartbeat reply. The client echoes the timestamp_ms from the PING it is responding to.

| Field | Type | Notes |
|:------|:-----|:------|
| timestamp_ms | i64 | Echoed server timestamp |

#### 2.5.9 TIME_NOTIFICATION (0x09) - Server to Client
A one-way push, on a fixed interval, of the server current time. The client logs it.

| Field | Type | Notes |
|:------|:-----|:------|
| timestamp_ms | i64 | Server time, milliseconds since Unix epoch |
| iso8601 | string | Same time formatted as ISO-8601 (e.g. "2026-07-03T14:22:01Z") |

#### 2.5.10 RANDOM_NUMBER (0x0A) - Client to Server
A one-way push, on a fixed interval, of a random number generated by the client. The server logs it and replies with HASH_RESPONSE.

| Field | Type | Notes |
|:------|:-----|:------|
| id | i64 | Correlation id (matches the HASH_RESPONSE) |
| number | i64 | The random number |

#### 2.5.11 HASH_RESPONSE (0x0B) - Server to Client
The server hash of the random number it received. The client logs the hash.

| Field | Type | Notes |
|:------|:-----|:------|
| id | i64 | Correlation id (matches the originating RANDOM_NUMBER) |
| hash_hex | string | Hex digest. Servers SHOULD use SHA-256 of the ASCII decimal form of number, then take the first 10 hex chars (matches prompt.md "7688b6ef5" example shape) |

#### 2.5.12 DISCONNECT (0x0C) - Client to Server
Optional graceful disconnect signal. The server removes the session and closes the connection.

| Field | Type | Notes |
|:------|:-----|:------|
| reason | string | Optional human-readable reason (may be empty) |

#### 2.5.13 ERROR (0x7F) - Both directions
A generic error frame. Used for protocol violations and application errors.

| Field | Type | Notes |
|:------|:-----|:------|
| code | i32 | Error code (see section 2.8) |
| message | string | Human-readable error message |


### 2.6 Session Lifecycle

The server maintains a session per WebSocket connection. A session is the unit of presence tracking and timeout.

Session fields (server-side, in-memory):
- session_id - string, a UUID generated by the server on connect
- user_id - string, taken from the userId HTTP request header on the WS handshake (per prompt.md requirement #1). Falls back to session_id if absent.
- client_language - string, from the HELLO message
- connected_at - i64, server time at connect
- last_pong_ts - i64, updated on every PONG; used for the 60s timeout

Lifecycle states:

1. CONNECTING - TCP/WS handshake in progress.
2. CONNECTED - WS handshake done. Server creates the session (session+), logs `[userId] session+`. Server starts the per-session background tasks (ping, time broadcast, kiss) and waits for HELLO.
3. HELLO then BONJOUR - Client sends HELLO(client_language). Server logs the requester session id and request time, then sends BONJOUR(server_language). Client logs bonjour.
4. ACTIVE - Steady state. Bi-directional traffic per section 2.7.
5. TIMEOUT or DISCONNECT - If no PONG is received within 60s, or a DISCONNECT is received, or the TCP connection drops, the server removes the session (session-) and closes the connection.

Client-side lifecycle mirrors this: connect, send HELLO, receive BONJOUR (log), active loop (handle PING to PONG, TIME_NOTIFICATION log, send RANDOM_NUMBER then receive HASH_RESPONSE log, handle KISS_REQUEST then send KISS_RESPONSE, optional ECHO_REQUEST and ECHO_RESPONSE), send DISCONNECT or drop.

### 2.7 Timing and Intervals

All intervals are defined as constants in each language shared common module and are configurable. Canonical defaults:

| Flow | Direction | Interval | Notes |
|:-----|:----------|:---------|:------|
| PING then PONG | Server to Client to Server | 1 second | Server sends PING; client replies PONG. Both log. |
| Session timeout | Server | 60 seconds | No PONG within 60s means session removed, connection closed. |
| TIME_NOTIFICATION | Server to Client | 5 seconds | Independent of the receive thread. Client logs server time. |
| RANDOM_NUMBER then HASH_RESPONSE | Client to Server to Client | 5 seconds | Independent of the receive thread. Server logs the number and replies with its hash. Client logs the hash. |
| KISS_REQUEST then KISS_RESPONSE | Server to Client to Server | 5 seconds | Downstream. Server asks for OS info; client responds with locale info. |

Note: README.md Mermaid diagram shows ping every 10s and kiss every 5s; prompt.md specifies ping every 1s. This plan adopts prompt.md 1s ping (more visible for demos) and the 5s kiss. Both are constants and trivially tunable.

### 2.8 Error Handling

Error codes (carried in ERROR.code):

| Code | Constant | Meaning |
|:-----|:---------|:--------|
| 0x01 | ERR_DECODE | Failed to decode a message payload (malformed bytes). |
| 0x02 | ERR_UNKNOWN_MESSAGE_TYPE | MSG_TYPE not in the registry. |
| 0x03 | ERR_TRUNCATED_PAYLOAD | PAYLOAD_LEN exceeds remaining frame bytes. |
| 0x04 | ERR_BAD_MAGIC | First byte was not 0x48. |
| 0x05 | ERR_BAD_VERSION | VERSION byte was not 0x01. |
| 0x06 | ERR_SESSION_NOT_FOUND | Operation referenced an unknown session. |
| 0x07 | ERR_INTERNAL | Unexpected server-side error. |

Recovery rules:
- On ERR_DECODE / ERR_BAD_MAGIC / ERR_BAD_VERSION: the receiver logs the error and closes the connection (these indicate a fundamentally broken peer).
- On ERR_UNKNOWN_MESSAGE_TYPE: the receiver sends an ERROR frame and continues (forward-compatible).
- All other errors are application-level; the connection stays open.

### 2.9 Worked Example (byte-level)

A client sending HELLO with client_language = "Go":

```
48 01 01 00  00 00 00 06  00 00 00 02  47 6F
|  |  |  |   |           |           |  |
|  |  |  |   |           |           +-- 'G' 'o'
|  |  |  |   |           +-- string length = 2
|  |  |  |   +-- PAYLOAD_LEN = 6
|  |  |  +-- FLAGS = 0x00
|  |  +-- MSG_TYPE = 0x01 (HELLO)
|  +-- VERSION = 0x01
+-- MAGIC = 0x48
```

(14 bytes total: 8 header + 6 payload.) This byte-level precision is what every language codec must reproduce.


---

## 3. Repository Layout

```
hello-websocket/
  PROTOCOL.md                         # Canonical binary protocol (section 2). The "proto".
  README.md                           # Overview, 12-language matrix, quickstart, links.
  LICENSE
  .gitignore                          # Maven, Go, Rust, node, python venv, etc.
  .dockerignore
  .github/
    dependabot.yml
    workflows/
      build-test.yml                  # Matrix build across 12 languages.
      docker-build-push.yml           # Build and push all Docker images.
  docker/
    README.md                         # Docker build guide (mirrors hello-grpc/docker/README.md).
    build_image.sh                    # Build per-language images.
    run_container.sh                  # Run per-language containers.
    push_image.sh                     # Push per-language images to registry.
    docker-compose.yml                # All-up: one server + 11 clients.
    docker-compose.<lang>.yml         # Per-language: server + client (12 files).
    tls/                              # TLS certs for secure mode.
    smoke_test_all.sh                 # Cross-language smoke test.
    <lang>_ws.dockerfile              # Per-language multi-stage Dockerfile (12 files).
  diagram/
    hello-websocket.svg               # Architecture diagram.
    protocol-sequence.svg             # Sequence diagram of the canonical protocol.
  scripts/
    check-versions.sh                 # Check library versions across languages.
    format.sh                         # Code formatting helpers.
  hello-websocket-java/               # Per-language subproject (see section 4).
  hello-websocket-go/
  hello-websocket-rust/
  hello-websocket-cpp/
  hello-websocket-csharp/
  hello-websocket-python/
  hello-websocket-nodejs/
  hello-websocket-dart/
  hello-websocket-kotlin/
  hello-websocket-swift/
  hello-websocket-ts/
```

The existing `hello-websocket-flutter-client/` is removed (it was a client-only stub and not part of the 12-language set). The existing 6 subprojects are rewritten to conform to PROTOCOL.md.


---

## 4. Per-Language Subproject Structure

Every language subproject follows the same shape, mirroring `hello-grpc-<lang>`:

```
hello-websocket-<lang>/
  README.md                # How to build and run this language.
  <build-manifest>         # pom.xml / go.mod / Cargo.toml / package.json / etc.
  common/                  # Shared codec + protocol types + constants (the "proto" implementation).
  server/                  # WebSocket server entry point + handlers.
  client/                  # WebSocket client entry point + handlers.
  scripts/                 # build.sh, server_start.sh, client_start.sh (+ .ps1 variants).
  logs/                    # Runtime logs (.gitkeep).
```

### 4.1 The common/ Module (codec)

Each language ships a `common/` module containing:

1. Constants - port 9898, magic 0x48, version 0x01, all message type codes, all error codes, intervals (PING_INTERVAL=1s, SESSION_TIMEOUT=60s, TIME_INTERVAL=5s, RANDOM_INTERVAL=5s, KISS_INTERVAL=5s).
2. Primitive encoders/decoders - write_u32, read_u32, write_string, read_string, write_kv, read_kv, write_array, read_array, all big-endian.
3. Message types - one struct/class per message type (Hello, Bonjour, EchoRequest, EchoResponse, EchoResult, KissRequest, KissResponse, Ping, Pong, TimeNotification, RandomNumber, HashResponse, Disconnect, Error), each with encode() to bytes and decode(bytes) to Self.
4. Frame codec - encode_frame(msg_type, payload) to bytes (prepends the 8-byte header) and decode_frame(bytes) to (msg_type, payload_bytes) (validates magic/version/length).
5. A top-level Message enum/dispatch - decode_message(bytes) to Message that reads the frame, reads the msg_type, and dispatches to the right payload decoder.

This module is what makes the protocol implementable identically across 12 languages.


### 4.2 Server Responsibilities (every language)

1. Bind TCP on port 9898, accept WebSocket upgrade requests.
2. Read the userId HTTP header from the upgrade request (fall back to session_id).
3. On connect: create a session (UUID), log [userId] session+, start background tasks.
4. Background task A (1s): send PING with current server timestamp_ms; expect PONG; update last_pong_ts. Log ping/pong.
5. Background task B (5s): send TIME_NOTIFICATION with current server timestamp_ms and ISO-8601 string. Client logs.
6. Background task C (5s): send KISS_REQUEST with server OS info; expect KISS_RESPONSE with client locale. (Downstream kiss.)
7. Receive loop dispatch on msg_type:
   - HELLO -> log requester session id and request time, send BONJOUR(server_language). Client logs bonjour.
   - ECHO_REQUEST -> build EchoResponse with one EchoResult (kv: id, idx, data, meta), send it.
   - KISS_RESPONSE -> log client locale.
   - PONG -> update last_pong_ts, log pong.
   - RANDOM_NUMBER -> log the number, compute SHA-256 hash, send HASH_RESPONSE with first 10 hex chars. Client logs the hash.
   - DISCONNECT -> remove session, close.
   - Unknown -> send ERROR(0x02), continue.
8. Session timeout task: if now - last_pong_ts > 60s, remove session (session-), close.
9. On TCP disconnect: remove session, log [userId] session-.

### 4.3 Client Responsibilities (every language)

1. Connect WebSocket to ws://host:9898, passing userId header.
2. Send HELLO(client_language). Receive BONJOUR(server_language). Log bonjour.
3. Receive loop dispatch on msg_type:
   - PING -> send PONG with the same timestamp_ms. Log ping/pong.
   - TIME_NOTIFICATION -> log server time.
   - KISS_REQUEST -> build KISS_RESPONSE with client locale (language, encoding, time_zone). Send it.
   - ECHO_RESPONSE -> log echo response.
   - HASH_RESPONSE -> log hash.
   - ERROR -> log error.
4. Background task (5s): send RANDOM_NUMBER(id, random i64). Receive HASH_RESPONSE. Log hash.
5. Optional ECHO_REQUEST: send periodically or on user input.
6. On shutdown: send DISCONNECT, then close.


---

## 5. Docker System (mirrors hello-grpc/docker)

### 5.1 Image Naming

- Server images: `feuyeux/ws_server_<lang>:1.0.0`
- Client images: `feuyeux/ws_client_<lang>:1.0.0`

Examples: `feuyeux/ws_server_java:1.0.0`, `feuyeux/ws_client_go:1.0.0`.

### 5.2 Per-Language Multi-Stage Dockerfile

Each `<lang>_ws.dockerfile` has stages:

1. build-base - contains all tools to compile the application (Maven/Go/Cargo/npm/etc.). Compiles the common/, server/, and client/ modules.
2. server stage - copies the server artifact, exposes port 9898, sets ENTRYPOINT.
3. client stage - copies the client artifact, sets ENTRYPOINT (server address via env var WS_SERVER and WS_PORT).

Example (docker/java_ws.dockerfile, modeled on docker/java_grpc.dockerfile):

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build-base
WORKDIR /app/hello-websocket
COPY hello-websocket-java/pom.xml /app/hello-websocket/hello-websocket-java/pom.xml
COPY hello-websocket-java/src /app/hello-websocket/hello-websocket-java/src
WORKDIR /app/hello-websocket/hello-websocket-java
RUN mvn clean package -DskipTests -Pserver
RUN mvn clean package -DskipTests -Pclient

FROM eclipse-temurin:25-jre-alpine AS server
WORKDIR /app
COPY --from=build-base /app/hello-websocket/hello-websocket-java/target/hello-websocket-server.jar /app/
EXPOSE 9898
ENTRYPOINT ["java", "-jar", "hello-websocket-server.jar"]

FROM eclipse-temurin:25-jre-alpine AS client
WORKDIR /app
COPY --from=build-base /app/hello-websocket/hello-websocket-java/target/hello-websocket-client.jar /app/
ENTRYPOINT ["java", "-jar", "hello-websocket-client.jar"]
```


### 5.3 docker-compose

- `docker/docker-compose.yml` (all-up): one server (default: Java) + 11 clients in distinct languages, all on a shared `ws_network`. Server publishes 9898. Clients connect to the server service name. A `CLIENT_LANG` env var can swap which client language runs.
- `docker/docker-compose.<lang>.yml` (per-language, 12 files): one server + one client for a single language. Enables `docker-compose -f docker/docker-compose.go.yml up` for a quick language-specific run.

Example all-up compose:

```yaml
version: "3.8"
services:
  ws-server-java:
    image: feuyeux/ws_server_java:1.0.0
    ports: ["9898:9898"]
    networks: [ws_network]
  ws-client-go:
    image: feuyeux/ws_client_go:1.0.0
    environment: [WS_SERVER=ws-server-java, WS_PORT=9898]
    depends_on: [ws-server-java]
    networks: [ws_network]
  # ... 10 more clients in other languages
networks:
  ws_network:
    driver: bridge
```

### 5.4 Scripts

- `docker/build_image.sh` - mirrors hello-grpc build_image.sh: `--language <lang> --component server|client|both [--all] [--parallel]`.
- `docker/run_container.sh` - mirrors run_container.sh: `--language <lang> --component server|client [--secure] [--cross]`.
- `docker/push_image.sh` - mirrors push_image.sh: push per-language or all images.
- `docker/smoke_test_all.sh` - cross-language smoke test: start a server, run each client briefly, verify HELLO/BONJOUR exchange and at least one PING/PONG and one RANDOM/HASH round-trip.

---

## 6. CI / GitHub Workflows (mirrors hello-grpc/.github)

- `.github/workflows/build-test.yml` - matrix build across all 12 languages; runs unit tests for the codec in each language.
- `.github/workflows/docker-build-push.yml` - builds and pushes all Docker images on release tags.
- `.github/dependabot.yml` - dependency updates for Maven, Go mod, Cargo, npm, pub, SPM, Composer, NuGet.

Per-language codec unit tests (every language must have these):
- Encode each message type, decode it back, assert equality.
- Round-trip a full HELLO frame at the byte level against the worked example in section 2.9.
- Negative tests: bad magic, bad version, truncated payload.


---

## 7. Implementation Order (Phased)

### Phase 0: Protocol contract + repo skeleton (do first)
- Write `PROTOCOL.md` (section 2 content) at repo root.
- Update root `README.md` with the 12-language matrix table.
- Create the directory skeleton in section 3 (empty subprojects).
- Remove `hello-websocket-flutter-client/`.

### Phase 1: Reference implementations (Java + Go + Rust) - establish the pattern
These three are rewritten first because they already exist and Java/Netty is the most mature. They prove the protocol is implementable.

- `hello-websocket-java/` (Netty server + client, common codec, codec tests).
- `hello-websocket-go/` (gorilla server + client, common codec, codec tests).
- `hello-websocket-rust/` (tokio-tungstenite server + client, common codec, codec tests).

### Phase 2: Rewrite the remaining existing subprojects to PROTOCOL.md
- `hello-websocket-python/` (websockets, fix port to 9898, implement full protocol, client already exists).
- `hello-websocket-nodejs/` (ws, add a client, rename package from "flutter_websockets", implement full protocol).

### Phase 3: New languages (C++, C#, Dart, Kotlin, Swift, PHP, TypeScript)
Each gets the full section 4 treatment: common codec, server, client, scripts, codec tests.

### Phase 4: Docker system
- Write 12 `<lang>_ws.dockerfile` files.
- Write `build_image.sh`, `run_container.sh`, `push_image.sh`, `smoke_test_all.sh`.
- Write `docker-compose.yml` (all-up) and 12 `docker-compose.<lang>.yml`.
- Write `docker/README.md`.

### Phase 5: CI + diagrams + polish
- Add `.github/workflows/build-test.yml` and `docker-build-push.yml`.
- Add `.github/dependabot.yml`.
- Generate `diagram/hello-websocket.svg` and `diagram/protocol-sequence.svg`.
- Final pass on all 12 README.md files.

---

## 8. Acceptance Criteria

The plan is complete when ALL of the following hold:

1. **12 language subprojects** exist at repo root: java, go, rust, cpp, csharp, python, nodejs, dart, kotlin, swift, php, ts. Each contains `common/` (codec), `server/`, `client/`, `scripts/`, `README.md`, and a build manifest.
2. **Every language implements the full canonical protocol** from `PROTOCOL.md`: handshake (HELLO/BONJOUR), echo (ECHO_REQUEST/ECHO_RESPONSE), kiss (KISS_REQUEST/KISS_RESPONSE), ping/pong (1s interval, 60s session timeout), time broadcast (TIME_NOTIFICATION every 5s), random/hash (RANDOM_NUMBER every 5s, HASH_RESPONSE with SHA-256 first 10 hex chars).
3. **Cross-language interop**: any language server can talk to any language client and complete a full protocol exchange. Verified by `docker/smoke_test_all.sh`.
4. **Every language has codec unit tests** that pass: round-trip encode/decode for every message type, byte-level HELLO frame validation against section 2.9, negative tests for bad magic/version/truncated payload.
5. **Dockerization complete**: 12 `<lang>_ws.dockerfile` multi-stage builds, `build_image.sh`, `run_container.sh`, `push_image.sh`, images named `feuyeux/ws_server_<lang>:1.0.0` and `feuyeux/ws_client_<lang>:1.0.0`.
6. **docker-compose orchestration**: root `docker-compose.yml` (all-up: 1 server + 11 clients) and 12 per-language `docker-compose.<lang>.yml` files.
7. **CI workflows**: `.github/workflows/build-test.yml` (12-language matrix) and `docker-build-push.yml`, plus `dependabot.yml`.
8. **Diagrams**: `diagram/hello-websocket.svg` (architecture) and `diagram/protocol-sequence.svg`.
9. **Root README.md** updated with the 12-language matrix table, quickstart, and links to each subproject.
10. **PROTOCOL.md** exists at repo root as the canonical contract.

