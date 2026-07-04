# Docker — Hello WebSocket

Multi-stage Dockerfiles for all 12 language implementations, plus build/run/push scripts and docker-compose orchestration.

## Image Naming

- Server images: `feuyeux/ws_server_<lang>:1.0.0`
- Client images: `feuyeux/ws_client_<lang>:1.0.0`

> **Note:** For Node.js, the image tag uses `node` instead of `nodejs` (e.g. `feuyeux/ws_server_node:1.0.0`).

## Dockerfiles

| Language | Dockerfile |
|---|---|
| C++ | `cpp_ws.dockerfile` |
| Rust | `rust_ws.dockerfile` |
| Java | `java_ws.dockerfile` |
| Go | `go_ws.dockerfile` |
| C# | `csharp_ws.dockerfile` |
| Python | `python_ws.dockerfile` |
| Node.js | `node_ws.dockerfile` |
| Dart | `dart_ws.dockerfile` |
| Kotlin | `kotlin_ws.dockerfile` |
| Swift | `swift_ws.dockerfile` |
| PHP | `php_ws.dockerfile` |
| TypeScript | `ts_ws.dockerfile` |

Each Dockerfile has three stages:
1. **build-base** — compiles the common/, server/, and client/ modules
2. **server** — copies the server artifact, exposes port 9898
3. **client** — copies the client artifact, connects via `WS_SERVER` and `WS_PORT` env vars

## Scripts

### build_image.sh
Build Docker images for one or all languages.

```bash
./build_image.sh --all                    # Build all language images
./build_image.sh --language java          # Build Java server + client
./build_image.sh --language go --component server  # Build only Go server
./build_image.sh --all --parallel         # Build all in parallel
```

### run_container.sh
Run a server or client container.

```bash
./run_container.sh --language java --component server
./run_container.sh --language go --component client
```

### push_image.sh
Push images to the Docker registry.

```bash
./push_image.sh --all                     # Push all language images
./push_image.sh --language java           # Push Java images only
./push_image.sh --language go --component server  # Push only Go server
```

### smoke_test_all.sh
Cross-language smoke test: start a server, run each client briefly, verify HELLO/BONJOUR exchange and at least one PING/PONG round-trip.

```bash
./smoke_test_all.sh                       # Test all 12 clients against a Java server
./smoke_test_all.sh --server python       # Use Python server instead
```

## docker-compose

### All-up
```bash
docker-compose -f docker-compose.yml up
```
Starts one server (default: Java) + 11 clients in distinct languages on a shared `ws_network`.

### Per-language
```bash
docker-compose -f docker-compose.<lang>.yml up
```
Starts one server + one client for a single language. Available for all 12 languages.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `WS_SERVER_PORT` | `9898` | Server listen port |
| `WS_SERVER` | `host.docker.internal` | Server host (client containers) |
| `WS_PORT` | `9898` | Server port (client containers) |
