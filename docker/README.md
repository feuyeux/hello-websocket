# Docker — Hello WebSocket

Multi-stage Dockerfiles for all 12 language implementations, plus build/run/push scripts and docker-compose orchestration.

## Container Runtime

`build_image.sh`, `run_container.sh`, `push_image.sh`, and `smoke_test_all.sh` automatically select a runtime:

| Host | Runtime |
|---|---|
| Apple-silicon macOS with Apple `container` installed | Apple `container` |
| Other supported hosts | Docker |

Set `WS_CONTAINER_RUNTIME=docker` or `WS_CONTAINER_RUNTIME=container` to override selection. The `container` runtime is valid only on Apple-silicon macOS. Its system service is started automatically when needed.
The build script also starts Apple `container builder` when it is not already running.

Before an Apple `container` client can reach a server port published on the Mac, configure this one-time host DNS mapping:

```bash
sudo container system dns create host.container.internal --localhost 203.0.113.113
```

The run and smoke-test scripts check this mapping and use `host.container.internal`; Docker continues to use `host.docker.internal`. Docker Compose remains Docker-only because Apple `container` has no Compose-compatible interface.

## Image Naming

- Server images: `feuyeux/ws_server_<lang>:1.0.0`
- Client images: `feuyeux/ws_client_<lang>:1.0.0`

> **Note:** For Node.js, the image tag uses `node` instead of `nodejs` (e.g. `feuyeux/ws_server_node:1.0.0`).

## Dockerfiles

| Language | Dockerfile |
|---|---|
| C++ | `Dockerfile.cpp` |
| Rust | `Dockerfile.rust` |
| Java | `Dockerfile.java` |
| Go | `Dockerfile.go` |
| C# | `Dockerfile.csharp` |
| Python | `Dockerfile.python` |
| Node.js | `Dockerfile.node` |
| Dart | `Dockerfile.dart` |
| Kotlin | `Dockerfile.kotlin` |
| Swift | `Dockerfile.swift` |
| PHP | `Dockerfile.php` |
| TypeScript | `Dockerfile.ts` |

Each Dockerfile has three stages:
1. **build-base** — compiles the common/, server/, and client/ modules
2. **server** — copies the server artifact, exposes port 9898
3. **client** — copies the client artifact, connects via `WS_SERVER` and `WS_PORT` env vars

## Scripts

### build_image.sh
Build Docker images for one or all languages.

```bash
./build_image.sh --all                          # Build all language images (default: 6 per batch)
./build_image.sh --language java                # Build Java server + client
./build_image.sh --language go --component server  # Build only Go server
./build_image.sh --all --batch-size 1           # Build all languages fully serially
./build_image.sh --all --batch-size 0           # Build all languages in one fully-parallel group
./build_image.sh --all --continue               # Build all, skip past failures, report which failed at the end
WS_CONTAINER_RUNTIME=container ./build_image.sh --language java --component server
```

Options:

| Flag | Description |
|---|---|
| `-l, --language LANG` | Build one language. Valid: `cpp`, `rust`, `java`, `go`, `csharp`, `python`, `nodejs`, `dart`, `kotlin`, `swift`, `php`, `ts`. |
| `-c, --component TYPE` | `server`, `client`, or `both` (default). |
| `-a, --all` | Build every language. |
| `-b, --batch-size N` | Languages per concurrent group; groups run serially. Default `6`. `N=0` puts every language in a single group (fully parallel); `N=1` is fully serial. Clamped to the total language count when `N > total`. |
| `-k, --continue` | Keep going past per-language failures under `--all` and print a failure summary at the end. Without this, the first failure aborts the whole build. |
| `-v, --verbose` | Verbose shell tracing. |
| `-h, --help` | Show usage. |

Only `--batch-size` and `--continue` are honored under `--all`; single `--language` builds always run alone and exit 1 on failure.

### run_container.sh
Run a server or client container.

```bash
./run_container.sh --language java --component server
./run_container.sh --language go --component client
WS_CONTAINER_RUNTIME=docker ./run_container.sh --language java --component server
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
| `WS_PORT` | `9898` | Server and client port |
| `WS_PATH` | `/ws` | WebSocket endpoint path |
| `WS_SERVER` | `host.docker.internal` | Server host (client containers) |
| `WS_PORT` | `9898` | Server port (client containers) |
