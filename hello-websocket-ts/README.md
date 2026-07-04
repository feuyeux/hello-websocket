# Hello WebSocket - TypeScript Implementation

TypeScript implementation of the Hello WebSocket protocol using [ws](https://www.npmjs.com/package/ws) with [@types/ws](https://www.npmjs.com/package/@types/ws).

## Project Structure

```
hello-websocket-ts/
├── package.json            # npm package definition
├── tsconfig.json           # TypeScript configuration
├── common/
│   ├── codec.ts            # Binary protocol codec
│   └── codec.test.ts       # Codec unit tests
├── server/
│   └── ws_server.ts        # WebSocket server
├── client/
│   └── ws_client.ts        # WebSocket client
├── scripts/
│   ├── build.sh            # Install and build
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **Node.js** 18+
- **npm** 10+

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
npm install
npm run build
npm test
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
