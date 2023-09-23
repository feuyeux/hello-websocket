# Hello Websocket for Rust

## lib

[Tungstenite](https://crates.io/crates/tungstenite)(github:[tungstenite-rs](https://github.com/snapview/tungstenite-rs))

Lightweight stream-based WebSocket implementation for Rust

[tokio_tungstenite](https://docs.rs/tokio-tungstenite/latest/tokio_tungstenite/)(github:[tokio-tungstenite](https://github.com/snapview/tokio-tungstenite))

This library is an implementation of WebSocket handshakes and streams. It is based on the crate which implements all required WebSocket protocol logic. So this crate basically just brings tokio support / tokio integration to it.

## build

```sh
cargo build
```

## run

```sh
cargo run --bin hello-server 
```

```sh
cargo run --bin hello-client ws://127.0.0.1:8080
```
