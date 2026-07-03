# Hello WebSocket Protocol Specification

This is the canonical binary protocol implemented by all 12 language subprojects.
It is the WebSocket analog of `hello-grpc/proto/landing.proto`.

---

## 1. Design Goals

1. **Language-agnostic** — encodable/decodable in all 12 languages with no external schema compiler.
2. **Self-delimiting** — every message carries its own length for safe parsing from a single WebSocket binary frame.
3. **Extensible** — a version byte and reserved flags byte allow future additions without breaking existing parsers.
4. **Symmetric** — the same envelope and primitive encodings are used for every message type, in both directions.

---

## 2. Frame Envelope

Every WebSocket binary frame carries exactly one message wrapped in this 8-byte header plus payload:

```
Offset  Size  Field        Description
------  ----  -----------  -------------------------------------------------
0       1     MAGIC        Constant 0x48 ('H'). Reject frames that do not start with this.
1       1     VERSION      Constant 0x01. Parsers MUST check and reject unknown versions.
2       1     MSG_TYPE     Message type discriminator (see section 4).
3       1     FLAGS        Reserved for future use. MUST be 0x00. Parsers MUST ignore unknown bits.
4       4     PAYLOAD_LEN  uint32, big-endian. Number of bytes in the payload (may be 0).
8       N     PAYLOAD      N = PAYLOAD_LEN bytes, encoded per section 3 and the message definition.
```

- Total fixed header = 8 bytes.
- A frame with MSG_TYPE not in the registry is a protocol error: send ERROR (0x7F) with code 0x02.
- PAYLOAD_LEN larger than the remaining frame bytes is a protocol error (code 0x03).

---

## 3. Primitive Encodings

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

---

## 4. Message Type Registry

| Code | Constant | Name | Direction | Payload ref |
|:-----|:---------|:-----|:----------|:------------|
| 0x01 | MSG_HELLO | Hello | Client → Server | 5.1 |
| 0x02 | MSG_BONJOUR | Bonjour | Server → Client | 5.2 |
| 0x03 | MSG_ECHO_REQUEST | Echo Request | Client → Server | 5.3 |
| 0x04 | MSG_ECHO_RESPONSE | Echo Response | Server → Client | 5.4 |
| 0x05 | MSG_KISS_REQUEST | Kiss Request | Server → Client | 5.5 |
| 0x06 | MSG_KISS_RESPONSE | Kiss Response | Client → Server | 5.6 |
| 0x07 | MSG_PING | Ping | Server → Client | 5.7 |
| 0x08 | MSG_PONG | Pong | Client → Server | 5.8 |
| 0x09 | MSG_TIME_NOTIFICATION | Time Notification | Server → Client | 5.9 |
| 0x0A | MSG_RANDOM_NUMBER | Random Number | Client → Server | 5.10 |
| 0x0B | MSG_HASH_RESPONSE | Hash Response | Server → Client | 5.11 |
| 0x0C | MSG_DISCONNECT | Disconnect | Client → Server | 5.12 |
| 0x7F | MSG_ERROR | Error | Both | 5.13 |

---

## 5. Payload Definitions

### 5.1 HELLO (0x01) — Client → Server

| Field | Type | Notes |
|:------|:-----|:------|
| client_language | string | Human-readable language name, e.g. "Go", "Rust", "Java" |

### 5.2 BONJOUR (0x02) — Server → Client

| Field | Type | Notes |
|:------|:-----|:------|
| server_language | string | Server implementation language |

### 5.3 ECHO_REQUEST (0x03) — Client → Server

| Field | Type | Notes |
|:------|:-----|:------|
| id | i64 | Request id (correlates request/response) |
| meta | string | Client-side language tag |
| data | string | Arbitrary request payload |

### 5.4 ECHO_RESPONSE (0x04) — Server → Client

| Field | Type | Notes |
|:------|:-----|:------|
| status | i32 | HTTP-style status: 200 OK, 500 error |
| results | array of EchoResult | Zero or more results |

EchoResult layout:

| Field | Type | Notes |
|:------|:-----|:------|
| idx | i64 | Timestamp / index |
| type | u8 | 0 = OK, 1 = ERROR |
| kv | kv (map of string to string) | Key-value pairs (e.g. id, idx, data, meta) |

### 5.5 KISS_REQUEST (0x05) — Server → Client

| Field | Type | Notes |
|:------|:-----|:------|
| os_name | string | e.g. "Linux", "Darwin", "Windows" |
| os_version | string | e.g. "10.0.19042" |
| os_release | string | e.g. "10" |
| os_architecture | string | e.g. "AMD64", "arm64" |

### 5.6 KISS_RESPONSE (0x06) — Client → Server

| Field | Type | Notes |
|:------|:-----|:------|
| language | string | e.g. "en_US" |
| encoding | string | e.g. "UTF-8" |
| time_zone | string | e.g. "UTC", "Asia/Shanghai" |

### 5.7 PING (0x07) — Server → Client

| Field | Type | Notes |
|:------|:-----|:------|
| timestamp_ms | i64 | Server time, milliseconds since Unix epoch |

### 5.8 PONG (0x08) — Client → Server

| Field | Type | Notes |
|:------|:-----|:------|
| timestamp_ms | i64 | Echoed server timestamp |

### 5.9 TIME_NOTIFICATION (0x09) — Server → Client

| Field | Type | Notes |
|:------|:-----|:------|
| timestamp_ms | i64 | Server time, milliseconds since Unix epoch |
| iso8601 | string | Same time as ISO-8601 (e.g. "2026-07-03T14:22:01Z") |

### 5.10 RANDOM_NUMBER (0x0A) — Client → Server

| Field | Type | Notes |
|:------|:-----|:------|
| id | i64 | Correlation id (matches the HASH_RESPONSE) |
| number | i64 | The random number |

### 5.11 HASH_RESPONSE (0x0B) — Server → Client

| Field | Type | Notes |
|:------|:-----|:------|
| id | i64 | Correlation id (matches the originating RANDOM_NUMBER) |
| hash_hex | string | SHA-256 of ASCII decimal form of number, first 10 hex chars |

### 5.12 DISCONNECT (0x0C) — Client → Server

| Field | Type | Notes |
|:------|:-----|:------|
| reason | string | Optional human-readable reason (may be empty) |

### 5.13 ERROR (0x7F) — Both directions

| Field | Type | Notes |
|:------|:-----|:------|
| code | i32 | Error code (see section 7) |
| message | string | Human-readable error message |

---

## 6. Session Lifecycle

The server maintains a session per WebSocket connection.

Session fields (server-side, in-memory):
- **session_id** — UUID generated by the server on connect
- **user_id** — from the `userId` HTTP request header on the WS handshake; falls back to session_id
- **client_language** — from the HELLO message
- **connected_at** — i64, server time at connect
- **last_pong_ts** — i64, updated on every PONG; used for the 60s timeout

Lifecycle states:
1. **CONNECTING** — TCP/WS handshake in progress.
2. **CONNECTED** — WS handshake done. Server creates the session, logs `[userId] session+`, starts background tasks.
3. **HELLO → BONJOUR** — Client sends HELLO(client_language). Server logs session id and request time, sends BONJOUR(server_language).
4. **ACTIVE** — Steady-state bi-directional traffic per section 8.
5. **TIMEOUT / DISCONNECT** — No PONG within 60s, or DISCONNECT received, or TCP dropped → session removed.

---

## 7. Error Handling

| Code | Constant | Meaning |
|:-----|:---------|:--------|
| 0x01 | ERR_DECODE | Failed to decode a message payload |
| 0x02 | ERR_UNKNOWN_MESSAGE_TYPE | MSG_TYPE not in the registry |
| 0x03 | ERR_TRUNCATED_PAYLOAD | PAYLOAD_LEN exceeds remaining frame bytes |
| 0x04 | ERR_BAD_MAGIC | First byte was not 0x48 |
| 0x05 | ERR_BAD_VERSION | VERSION byte was not 0x01 |
| 0x06 | ERR_SESSION_NOT_FOUND | Operation referenced an unknown session |
| 0x07 | ERR_INTERNAL | Unexpected server-side error |

Recovery rules:
- **ERR_DECODE / ERR_BAD_MAGIC / ERR_BAD_VERSION**: log and close the connection.
- **ERR_UNKNOWN_MESSAGE_TYPE**: send ERROR frame and continue (forward-compatible).
- All other errors are application-level; the connection stays open.

---

## 8. Timing and Intervals

All intervals are constants in each language's common module and are configurable.

| Flow | Direction | Default | Notes |
|:-----|:----------|:--------|:------|
| PING → PONG | Server → Client → Server | 1 second | Server sends PING; client replies PONG |
| Session timeout | Server | 60 seconds | No PONG within 60s → session removed |
| TIME_NOTIFICATION | Server → Client | 5 seconds | Client logs server time |
| RANDOM_NUMBER → HASH_RESPONSE | Client → Server → Client | 5 seconds | Server logs number, replies with hash |
| KISS_REQUEST → KISS_RESPONSE | Server → Client → Server | 5 seconds | Server asks for OS info; client responds with locale |

---

## 9. Worked Example (byte-level)

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

14 bytes total: 8 header + 6 payload. Every language codec must reproduce this exactly.
