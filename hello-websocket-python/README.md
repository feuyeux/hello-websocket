# Hello WebSocket - Python Implementation

Python implementation of the Hello WebSocket protocol using [websockets](https://pypi.org/project/websockets/).

## Project Structure

```
hello-websocket-python/
├── requirements.txt        # pip dependencies
├── common/
│   ├── __init__.py
│   ├── codec.py            # Binary protocol codec
│   └── codec_test.py       # Codec unit tests
├── server/
│   └── ws_server.py        # WebSocket server
├── client/
│   └── ws_client.py        # WebSocket client
├── scripts/
│   ├── build.sh            # Install and test
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **Python** 3.11+

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
pip install -r requirements.txt
python -m pytest common/codec_test.py -v
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
