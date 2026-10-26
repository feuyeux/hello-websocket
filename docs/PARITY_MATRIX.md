# Feature Parity Matrix

Audited capability parity across the 12 WebSocket implementations. The
summary table is mirrored in [README.md](../README.md); this document records
what each capability means and how it is verified.

## Implementation Matrix

| Language   | Library                                   | Server | Client | Build System | Codec Tests | Docker | Compose | Smoke |
|:-----------|:------------------------------------------|:-------|:-------|:-------------|:------------|:-------|:--------|:------|
| C++        | dependency-free RFC 6455 layer            | ✅     | ✅     | CMake        | ✅          | ✅     | ✅      | ✅    |
| Rust       | tokio-tungstenite                         | ✅     | ✅     | Cargo        | ✅          | ✅     | ✅      | ✅    |
| Java       | Java-WebSocket                            | ✅     | ✅     | Maven        | ✅          | ✅     | ✅      | ✅    |
| Go         | gorilla/websocket                         | ✅     | ✅     | Go Modules   | ✅          | ✅     | ✅      | ✅    |
| C#         | System.Net.WebSockets                     | ✅     | ✅     | dotnet       | ✅          | ✅     | ✅      | ✅    |
| Python     | websockets                                | ✅     | ✅     | pip          | ✅          | ✅     | ✅      | ✅    |
| Node.js    | ws                                        | ✅     | ✅     | npm          | ✅          | ✅     | ✅      | ✅    |
| TypeScript | ws + tsc                                  | ✅     | ✅     | npm          | ✅          | ✅     | ✅      | ✅    |
| Dart       | dart:io WebSocket                         | ✅     | ✅     | Pub          | ✅          | ✅     | ✅      | ✅    |
| Kotlin     | Ktor WebSockets                           | ✅     | ✅     | Gradle       | ✅          | ✅     | ✅      | ✅    |
| Swift      | dependency-free socket / RFC 6455 layer   | ✅     | ✅     | SwiftPM      | ✅          | ✅     | ✅      | ✅    |
| PHP        | Ratchet server + Pawl client              | ✅     | ✅     | Composer     | ✅          | ✅     | ✅      | ✅    |

## Protocol Message Coverage

Every language codec implements all 13 message types from
[PROTOCOL.md](../PROTOCOL.md) and round-trips them against the canonical
vectors in [tests/protocol-vectors.json](../tests/protocol-vectors.json):

| Code | Constant | Direction |
|:-----|:---------|:----------|
| 0x01 | MSG_HELLO | Client → Server |
| 0x02 | MSG_BONJOUR | Server → Client |
| 0x03 | MSG_ECHO_REQUEST | Client → Server |
| 0x04 | MSG_ECHO_RESPONSE | Server → Client |
| 0x05 | MSG_KISS_REQUEST | Server → Client |
| 0x06 | MSG_KISS_RESPONSE | Client → Server |
| 0x07 | MSG_PING | Server → Client |
| 0x08 | MSG_PONG | Client → Server |
| 0x09 | MSG_TIME_NOTIFICATION | Server → Client |
| 0x0A | MSG_RANDOM_NUMBER | Client → Server |
| 0x0B | MSG_HASH_RESPONSE | Server → Client |
| 0x0C | MSG_DISCONNECT | Client → Server |
| 0x7F | MSG_ERROR | Both |

Codec unit tests in every language additionally cover:

- Byte-level reproduction of the worked example (PROTOCOL.md section 2.9)
- Negative cases: bad magic, bad version, truncated payload
- Signed integer boundaries (i32/i64)
- Multilingual UTF-8 payloads

## How Each Capability Is Verified

| Capability | Verified by |
|:-----------|:------------|
| Build | `.github/workflows/build-test.yml` — matrix build across all 12 languages |
| Codec tests | Per-language unit test suites run in `build-test.yml` |
| Docker | `docker/Dockerfile.<lang>` multi-stage builds; `.github/workflows/docker-build-push.yml` |
| Compose | `docker/docker-compose.yml` (all-up) and `docker/docker-compose.<lang>.yml` |
| Cross-language smoke | `docker/smoke_test_all.sh` — boots a server and runs clients against it, verifying HELLO/BONJOUR and PING/PONG |
| Dependency health | Dependabot, `dependency-review.yml`, `dependency-health.yml`, guarded auto-merge — see [DEPENDENCY_AUTOMATION.md](DEPENDENCY_AUTOMATION.md) |

## Intentionally Out of Scope

The following are excluded by design (see README Security Scope):

- TLS (`wss://`) — plain `ws://` only; terminate TLS at a reverse proxy for real deployments
- Authentication / authorization — `userId` is untrusted display metadata
- Browser Origin policy and connection/message rate limiting
