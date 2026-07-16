FROM debian:bookworm-slim AS build-base
RUN apt-get update && apt-get install -y \
    build-essential \
    cmake \
    g++ \
    && rm -rf /var/lib/apt/lists/*
COPY hello-websocket-cpp /app/hello-websocket-cpp
WORKDIR /app/hello-websocket-cpp
RUN cmake -DCMAKE_BUILD_TYPE=Release . && make

FROM debian:bookworm-slim AS server
RUN apt-get update && apt-get install -y libstdc++6 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build-base /app/hello-websocket-cpp/ws_server /app/
ENTRYPOINT ["./ws_server"]

FROM debian:bookworm-slim AS client
RUN apt-get update && apt-get install -y libstdc++6 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build-base /app/hello-websocket-cpp/ws_client /app/
ENTRYPOINT ["./ws_client"]
