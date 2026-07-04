# NOTE: The current Swift implementation uses WinSDK (Windows-only).
# This Dockerfile uses the Linux Swift image. The code will need to be
# ported to use Glibc/POSIX sockets for Linux compatibility.
FROM swift:6.1 AS build-base
COPY hello-websocket-swift /app/hello-websocket-swift
WORKDIR /app/hello-websocket-swift
RUN swift build -c release

FROM swift:6.1-slim AS server
WORKDIR /app
COPY --from=build-base /app/hello-websocket-swift/.build/release/ws-server /app/
ENTRYPOINT ["./ws-server"]

FROM swift:6.1-slim AS client
WORKDIR /app
COPY --from=build-base /app/hello-websocket-swift/.build/release/ws-client /app/
ENTRYPOINT ["./ws-client"]
