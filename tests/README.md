# Protocol Test Assets

Language-neutral test assets shared by the 12 implementations. These are
reference materials for humans and external tooling — per-language codec
suites embed their own copies of the cases they exercise.

## protocol-vectors.json

Canonical codec vectors, keyed by case name. Each value is the exact expected
frame as a hex string, so every language can assert byte-level compatibility
against shared data instead of its own assumptions. Current cases:

| Case | What it pins down |
|---|---|
| `hello_go` | The worked example from PROTOCOL.md section 2.9, byte for byte |
| `hello_chinese` | Multilingual UTF-8 payload encoding |
| `random_negative_one` | Signed i64 boundary (-1) in two's complement |
| `invalid_truncated_string` | Malformed frame: declared string length exceeds payload |

## reference_client.py

A minimal reference peer built on the Python codec
(`hello-websocket-python/common`). Point it at any running server — native or
container — to verify wire compatibility end to end:

```bash
./hello-websocket-java/scripts/run-server.sh   # terminal 1: any language server
python3 tests/reference_client.py              # terminal 2: conformance check
```

The client sends HELLO, an ECHO_REQUEST with a multilingual payload, and a
RANDOM_NUMBER of -1, then verifies BONJOUR, answers PING with PONG, checks
the ECHO_RESPONSE, and validates the HASH_RESPONSE as the first 10 hex chars
of the SHA-256 of `-1`. A successful run prints `reference conformance
passed`; anything else raises an assertion naming the missing exchanges.

Honors `WS_SERVER`, `WS_PORT`, and `WS_PATH`, like every implementation.
