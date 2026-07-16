# Hello WebSocket - Go Implementation

Go implementation of the Hello WebSocket protocol using [gorilla/websocket](https://github.com/gorilla/websocket).

## Project Structure

```
hello-websocket-go/
├── go.mod                  # Go module definition
├── common/
│   ├── codec.go            # Binary protocol codec
│   └── codec_test.go       # Codec unit tests
├── server/
│   └── main.go             # WebSocket server
├── client/
│   └── main.go             # WebSocket client
├── scripts/
│   ├── build.sh            # Build and test
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **Go** 1.23+

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
go build ./...
go test ./...
```

## Run Server

```bash
./scripts/run-server.sh
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
