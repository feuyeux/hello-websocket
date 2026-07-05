# Cross-platform Swift implementation. Socket layer supports Windows (WinSDK),
# Linux (Glibc), and macOS (Darwin) via conditional compilation. Build with:
#   - Windows: any Swift toolchain that ships WinSDK
#   - Linux:   swift:6.2 (used here for Docker)
#   - macOS:   swift build on Darwin
FROM swift:6.3 AS build-base
COPY hello-websocket-swift /app/hello-websocket-swift
WORKDIR /app/hello-websocket-swift
RUN swift build -c release

FROM swift:6.3-slim AS server
WORKDIR /app
COPY --from=build-base /app/hello-websocket-swift/.build/release/ws-server /app/
ENTRYPOINT ["./ws-server"]

FROM swift:6.3-slim AS client
WORKDIR /app
COPY --from=build-base /app/hello-websocket-swift/.build/release/ws-client /app/
ENTRYPOINT ["./ws-client"]
