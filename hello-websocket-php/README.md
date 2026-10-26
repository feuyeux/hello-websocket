# Hello WebSocket - PHP Implementation

PHP implementation using [Ratchet](https://github.com/ratchetphp/Ratchet) for the server and [Pawl](https://github.com/ratchetphp/Pawl) for the client.

## Project Structure

```
hello-websocket-php/
├── composer.json           # Composer package definition
├── composer.lock           # Locked dependencies
├── phpunit.xml.dist        # PHPUnit configuration
├── common/
│   └── Codec.php           # Binary protocol codec
├── server/
│   └── ws_server.php       # WebSocket server
├── client/
│   └── ws_client.php       # WebSocket client
├── tests/
│   ├── CodecTest.php       # Codec unit tests
│   └── run_tests.php       # Test runner
├── scripts/
│   ├── build.sh            # Install and test
│   ├── run-server.sh       # Run server
│   └── run-client.sh       # Run client
└── README.md
```

## Prerequisites

- **PHP** 8.2+ with sockets extension
- **Composer** 2.8+

## Build

```bash
./scripts/build.sh
```

Or manually:
```bash
composer install --no-interaction --prefer-dist
composer test
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
