# Cross-platform Swift implementation. Socket layer supports Windows (WinSDK),
# Linux (Glibc), and macOS (Darwin) via conditional compilation. Build with:
#   - Windows: any Swift toolchain that ships WinSDK
#   - Linux:   swift:6.3 (used here for Docker)
#   - macOS:   swift build on Darwin
FROM swift:6.3 AS build-base
COPY hello-websocket-swift /app/hello-websocket-swift
WORKDIR /app/hello-websocket-swift
RUN swift build -c release

FROM swift:6.3-slim AS server
RUN useradd --system --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-websocket-swift/.build/release/ws-server /app/
USER app
ENTRYPOINT ["./ws-server"]

FROM swift:6.3-slim AS client
RUN useradd --system --create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build-base --chown=app:app /app/hello-websocket-swift/.build/release/ws-client /app/
USER app
ENTRYPOINT ["./ws-client"]
