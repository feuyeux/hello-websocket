FROM rust:1.85-slim-bookworm AS build-base
RUN mkdir -p $HOME/.cargo \
    && echo '[source.crates-io]\nreplace-with = "ustc"\n\n[source.ustc]\nregistry = "https://mirrors.ustc.edu.cn/crates.io-index"' > $HOME/.cargo/config
COPY hello-websocket-rust /app/hello-websocket-rust
WORKDIR /app/hello-websocket-rust
RUN cargo build --release

FROM debian:bookworm-slim AS server
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build-base /app/hello-websocket-rust/target/release/ws-server /app/
ENTRYPOINT ["./ws-server"]

FROM debian:bookworm-slim AS client
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build-base /app/hello-websocket-rust/target/release/ws-client /app/
ENTRYPOINT ["./ws-client"]
