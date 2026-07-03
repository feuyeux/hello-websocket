use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use hello_websocket::*;
use tokio_tungstenite::connect_async;
use tokio_tungstenite::tungstenite::Message as WsMessage;

#[tokio::main]
async fn main() {
    let host = std::env::var("WS_SERVER").unwrap_or_else(|_| "127.0.0.1".to_string());
    let port = std::env::var("WS_PORT").ok().and_then(|p| p.parse().ok()).unwrap_or(PORT);
    log("ws-client", "Starting Rust WebSocket client [version: 1.0.0]");
    log("ws-client", &format!("Connecting to ws://{}:{}", host, port));

    let url = format!("ws://{}:{}", host, port);

    for attempt in 1..=3 {
        log("ws-client", &format!("Connection attempt {}/3 to {}", url, attempt));
        match try_connect(&url).await {
            Ok(()) => return,
            Err(e) => {
                log("ws-client", &format!("Error: {}", e));
                if attempt < 3 { tokio::time::sleep(Duration::from_secs(2)).await; }
            }
        }
    }
    log("ws-client", "Failed to connect after 3 attempts");
    std::process::exit(1);
}

async fn try_connect(url: &str) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let (ws_stream, _) = connect_async(url).await?;
    log("ws-client", "Connected");

    let (mut ws_sender, mut ws_receiver) = ws_stream.split();

    // Send HELLO
    let hello = Message::Hello { client_language: CLIENT_LANG.to_string() };
    ws_sender.send(WsMessage::Binary(hello.encode())).await?;

    let sender = std::sync::Arc::new(tokio::sync::Mutex::new(ws_sender));
    let sender_clone = sender.clone();

    // Random number background task
    let random_task = tokio::spawn(async move {
        let mut id: i64 = 1;
        loop {
            tokio::time::sleep(Duration::from_millis(RANDOM_INTERVAL_MS)).await;
            let num: i64 = rand::random();
            let rn = Message::RandomNumber { id, number: num };
            let mut s = sender_clone.lock().await;
            if s.send(WsMessage::Binary(rn.encode())).await.is_err() { break; }
            log("ws-client", &format!("RANDOM_NUMBER id={} number={}", id, num));
            id += 1;
        }
    });

    // Receive loop
    while let Some(msg_result) = ws_receiver.next().await {
        let msg = match msg_result {
            Ok(m) => m,
            Err(_) => break,
        };

        let data = match msg {
            WsMessage::Binary(d) => d,
            WsMessage::Close(_) => break,
            _ => continue,
        };

        let decoded = match Message::decode(&data) {
            Ok(m) => m,
            Err(e) => { log("ws-client", &format!("Decode error: {}", e)); continue; }
        };

        match decoded {
            Message::Bonjour { server_language } => {
                log("ws-client", &format!("BONJOUR server_language={}", server_language));
            }
            Message::Ping { timestamp_ms } => {
                log("ws-client", &format!("PING ts={}", timestamp_ms));
                let pong = Message::Pong { timestamp_ms };
                let mut s = sender.lock().await;
                let _ = s.send(WsMessage::Binary(pong.encode())).await;
                log("ws-client", &format!("PONG ts={}", timestamp_ms));
            }
            Message::TimeNotification { timestamp_ms, iso8601 } => {
                log("ws-client", &format!("TIME_NOTIFICATION ts={} iso={}", timestamp_ms, iso8601));
            }
            Message::KissRequest { os_name, os_version, os_release, os_architecture } => {
                log("ws-client", &format!("KISS_REQUEST os={} ver={} rel={} arch={}", os_name, os_version, os_release, os_architecture));
                let resp = Message::KissResponse {
                    language: "en_US".to_string(),
                    encoding: "UTF-8".to_string(),
                    time_zone: "UTC".to_string(),
                };
                let mut s = sender.lock().await;
                let _ = s.send(WsMessage::Binary(resp.encode())).await;
                log("ws-client", "KISS_RESPONSE sent");
            }
            Message::EchoResponse { status, results } => {
                log("ws-client", &format!("ECHO_RESPONSE status={} results={}", status, results.len()));
                for (i, r) in results.iter().enumerate() {
                    log("ws-client", &format!("  Result #{}: idx={} type={} kv={:?}", i + 1, r.idx, r.type_, r.kv));
                }
            }
            Message::HashResponse { id, hash_hex } => {
                log("ws-client", &format!("HASH_RESPONSE id={} hash={}", id, hash_hex));
            }
            Message::Error { code, message } => {
                log("ws-client", &format!("ERROR code={} msg={}", code, message));
            }
            _ => {
                log("ws-client", "Unknown message type");
            }
        }
    }

    random_task.abort();
    // Send DISCONNECT
    let disconnect = Message::Disconnect { reason: "client shutdown".to_string() };
    let mut s = sender.lock().await;
    let _ = s.send(WsMessage::Binary(disconnect.encode())).await;

    Ok(())
}
