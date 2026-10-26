# Hello WebSocket - Node.js Implementation

Node.js implementation of the Hello WebSocket protocol using [ws](https://www.npmjs.com/package/ws).

## Project Structure

```
hello-websocket-nodejs/
├── package.json            # npm package definition
├── common/
│   ├── codec.js            # Binary protocol codec
│   └── codec.test.js       # Codec unit tests
├── server/
│   └── ws_server.js        # WebSocket server
├── client/
│   └── ws_client.js        # WebSocket client
├── scripts/
│   ├── build.sh            # Install and test
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **Node.js** 18+

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
npm install
npm test
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
