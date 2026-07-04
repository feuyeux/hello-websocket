# Hello WebSocket - Dart Implementation

Dart implementation of the Hello WebSocket protocol using [shelf_web_socket](https://pub.dev/packages/shelf_web_socket) (server) and [web_socket_channel](https://pub.dev/packages/web_socket_channel) (client).

## Project Structure

```
hello-websocket-dart/
├── pubspec.yaml            # Dart package definition
├── common/
│   ├── codec.dart          # Binary protocol codec
│   └── codec_test.dart     # Codec unit tests
├── server/
│   └── ws_server.dart      # WebSocket server
├── client/
│   └── ws_client.dart      # WebSocket client
├── scripts/
│   ├── build.sh            # Build with pub
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **Dart SDK** 3.9+

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
dart pub get
dart compile exe server/ws_server.dart -o build/ws_server
dart compile exe client/ws_client.dart -o build/ws_client
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
