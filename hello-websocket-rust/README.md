# Hello WebSocket - Rust Implementation

Rust implementation of the Hello WebSocket protocol using [tokio-tungstenite](https://crates.io/crates/tokio-tungstenite).

## Project Structure

```
hello-websocket-rust/
├── Cargo.toml              # Cargo package definition
├── common/
│   └── lib.rs              # Binary protocol codec
├── server/
│   └── main.rs             # WebSocket server
├── client/
│   └── main.rs             # WebSocket client
├── scripts/
│   ├── build.sh            # Build and test
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **Rust** 1.81+ (with Cargo)

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
cargo build --release
cargo test --release
```

## Run Server

```bash
./scripts/run-server.sh
```

Environment variables:
- `WS_SERVER_PORT` — Port to listen on (default: 9898)

## Run Client

```bash
./scripts/run-client.sh
```

Environment variables:
- `WS_SERVER_HOST` — Server host (default: 127.0.0.1)
- `WS_SERVER_PORT` — Server port (default: 9898)

## Protocol

See [../PROTOCOL.md](../PROTOCOL.md) for the canonical protocol specification.
