# Hello WebSocket Contributor Guide

## Scope

`hello-websocket` contains equivalent WebSocket clients and servers in 12 languages: C++, C#, Dart, Go, Java, Kotlin, Node.js, PHP, Python, Rust, Swift, and TypeScript. Each implementation interoperates through the canonical binary protocol in [`PROTOCOL.md`](PROTOCOL.md).

## Layout

- `PROTOCOL.md` — normative binary frame and message specification.
- `hello-websocket-<language>/` — a self-contained implementation, usually with `common/`, `client/`, `server/`, `scripts/`, and language-native tests.
- `scripts/` — repository-wide support scripts such as `format.sh` and version checks.
- `tests/` — protocol-level reference helpers.
- `docker/` — per-language container build, run, and smoke-test utilities.
- `docs/`, `diagram/`, and `PLAN.md` — supporting documentation and design material.

## Protocol Invariants

Treat `PROTOCOL.md` as the source of truth. All implementations must use the default endpoint `ws://<host>:9898/ws` unless overridden by `WS_SERVER`, `WS_PORT`, or `WS_PATH`.

- Every binary WebSocket message contains exactly one protocol frame.
- The frame header is 8 bytes: magic `0x48`, version `0x01`, message type, flags, and a big-endian `uint32` payload length.
- Reject invalid magic, unsupported versions, malformed lengths, trailing bytes, and messages over 1 MiB according to the protocol error rules.
- Primitive values use the specified big-endian and UTF-8 encodings. Do not substitute JSON or host-native integer layouts.
- Preserve all message types and their directions, including HELLO/BONJOUR, echo, heartbeat, time notification, random-number hashing, disconnect, and error frames.

When a protocol change is necessary, update `PROTOCOL.md` first, then change every affected codec and add interoperability-oriented tests. Do not silently change one language implementation's wire format.

## Development Workflow

1. Work in one `hello-websocket-<language>/` directory and follow its existing build file and source style.
2. Keep reusable encoding/decoding code in that implementation's `common/` area; keep WebSocket transport behavior in `client/` and `server/`.
3. Use the language directory's `scripts/build.sh` for its normal build/test flow. For example:

   ```sh
   ./hello-websocket-go/scripts/build.sh
   ./hello-websocket-python/scripts/build.sh
   ./hello-websocket-java/scripts/build.sh
   ```

4. For an interoperability smoke test, start a server and a client in separate terminals; implementations may be mixed because they share `/ws` and the binary protocol:

   ```sh
   ./hello-websocket-python/scripts/run-server.sh
   ./hello-websocket-go/scripts/run-client.sh
   ```

5. Run only the formatter and tests for the language you changed. `scripts/format.sh` can modify multiple implementations, so do not use it for a focused change unless broad formatting is intentional.

### Container Runtime on macOS

On Apple-silicon macOS, Apple's `container` CLI can replace Docker for this repository's container workflows. The scripts `docker/build_image.sh`, `docker/run_container.sh`, `docker/push_image.sh`, and `docker/smoke_test_all.sh` use `docker/container_runtime.sh` to select the runtime automatically.

- On arm64 macOS with Apple `container` installed, runtime auto-detection selects `container`.
- On other supported hosts, runtime auto-detection selects Docker.
- Override selection with `CONTAINER_RUNTIME=docker` or `CONTAINER_RUNTIME=container`.
- Apple `container` is not Docker Compose-compatible; `docker/docker-compose*.yml` remains Docker-only.

Initialize Apple Container when needed:

```sh
container --version
container system start
container system status
container builder start
container builder status
```

The build script also starts the Apple Container system service and builder when they are not running. To build all 12 language implementations' server and client images with bounded concurrency:

```sh
CONTAINER_RUNTIME=container ./docker/build_image.sh \\
  --all --component both --batch-size 6 --continue
```

Use `--batch-size 1` for a fully serial build on a resource-constrained Mac. Avoid `--batch-size 0` unless the machine has sufficient memory.

For Apple Container clients to reach a server port published on the Mac, configure this one-time host DNS mapping:

```sh
sudo container system dns create host.container.internal --localhost 203.0.113.113
```

The run and smoke-test scripts use `host.container.internal` with Apple Container and `host.docker.internal` with Docker.

## Code and Test Expectations

- Follow the idioms, formatter, dependency manifest, and lockfile already used by the selected language directory.
- Keep frame parsing defensive: check remaining bytes and collection counts before allocation, and do not trust peer-supplied timestamps or metadata.
- Add or update codec tests for all changed message types and malformed-frame paths. Maintain coverage for the worked example, integer boundaries, and UTF-8 values.
- Do not commit build artifacts, vendored dependencies, local environment files, or container logs.

## Security Note

These examples use plain `ws://` and treat handshake `userId` metadata as untrusted. Do not present them as production authentication or TLS implementations. Production deployments need TLS termination, upgrade authentication, origin controls, and rate limiting outside this teaching project.

## JDK Conventions

The two JVM languages in this repo do NOT share a single JDK version. The pinned versions are intentional and authoritative:

| Language | Build base image | Runtime image | Bytecode target |
|:---------|:-----------------|:--------------|:----------------|
| Java (`hello-websocket-java/`) | `maven:3-eclipse-temurin-25` | `eclipse-temurin:25-jre-alpine` | `maven.compiler.source/target = 17` (per `pom.xml`) |
| Kotlin (`hello-websocket-kotlin/`) | `gradle:8.14-jdk21` | `eclipse-temurin:21-jre-alpine` | `sourceCompatibility = "21"`, `jvmTarget = JVM_21` (per `build.gradle.kts`) |

Rules:

- Do **not** "unify" the two JDK pins. Java ships on JDK 25 and Kotlin on JDK 21 because each implementation is independently pinned to the version it was developed and tested against.
- When changing a Docker base image, update both `docker/Dockerfile.<lang>` and the matching row in `PLAN.md` so the spec and the implementation stay aligned.
- The bytecode target columns (`17` for Java, `21` for Kotlin) are the language level the source compiles TO — separate from the JDK used to build and run it. Bumping the JDK does not require bumping the bytecode target.
- Before editing any of these values, load `PLAN.md` to confirm the current spec; the spec wins, and the Dockerfiles + AGENTS.md must follow it.
