use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use futures_util::{SinkExt, StreamExt};
use hello_websocket::*;
use tokio::net::TcpListener;
use tokio::sync::mpsc;
use tokio_tungstenite::accept_async;
use tokio_tungstenite::tungstenite::Message as WsMessage;
use uuid::Uuid;

#[tokio::main]
async fn main() {
    let port = std::env::var("WS_PORT").ok().and_then(|p| p.parse().ok()).unwrap_or(PORT);
    let addr = format!("0.0.0.0:{}", port);
    log("ws-server", &format!("Starting Rust WebSocket server on port {}", port));

    let listener = TcpListener::bind(&addr).await.expect("Failed to bind");

    loop {
        let (stream, addr) = listener.accept().await.expect("Failed to accept");
        tokio::spawn(handle_connection(stream, addr));
    }
}

async fn handle_connection(stream: tokio::net::TcpStream, _addr: SocketAddr) {
    let ws_stream = match accept_async(stream).await {
        Ok(ws) => ws,
        Err(e) => { log("ws-server", &format!("Accept error: {}", e)); return; }
    };

    let user_id = format!("rust-{}", Uuid::new_v4().to_string()[..8].to_string());
    let session_id = Uuid::new_v4().to_string();
    let last_pong = Arc::new(Mutex::new(now_ms()));
    let mut client_language = "unknown".to_string();

    log("ws-server", &format!("[{}] session+", user_id));

    let (mut ws_sender, mut ws_receiver) = ws_stream.split();
    let (tx, mut rx) = mpsc::channel::<Vec<u8>>(100);

    // Sender task
    let sender_task = tokio::spawn(async move {
        while let Some(data) = rx.recv().await {
            if ws_sender.send(WsMessage::Binary(data)).await.is_err() {
                break;
            }
        }
    });

    // Background: ping
    let tx_ping = tx.clone();
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_millis(PING_INTERVAL_MS)).await;
            let ping = Message::Ping { timestamp_ms: now_ms() };
            if tx_ping.send(ping.encode()).await.is_err() { break; }
        }
    });

    // Background: time
    let tx_time = tx.clone();
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_millis(TIME_INTERVAL_MS)).await;
            let tn = Message::TimeNotification { timestamp_ms: now_ms(), iso8601: now_iso() };
            if tx_time.send(tn.encode()).await.is_err() { break; }
        }
    });

    // Background: kiss
    let tx_kiss = tx.clone();
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_millis(KISS_INTERVAL_MS)).await;
            let kr = Message::KissRequest {
                os_name: std::env::consts::OS.to_string(),
                os_version: "unknown".to_string(),
                os_release: "unknown".to_string(),
                os_architecture: std::env::consts::ARCH.to_string(),
            };
            if tx_kiss.send(kr.encode()).await.is_err() { break; }
        }
    });

    // Background: timeout check
    let tx_timeout = tx.clone();
    let last_pong_timeout = last_pong.clone();
    let user_id_timeout = user_id.clone();
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(Duration::from_secs(5)).await;
            let last = *last_pong_timeout.lock().unwrap();
            if now_ms() - last > SESSION_TIMEOUT_MS as i64 {
                log("ws-server", &format!("[{}] session timeout", user_id_timeout));
                let _ = tx_timeout.send(vec![]).await; // signal close
                break;
            }
        }
    });

    // Receive loop
    while let Some(msg_result) = ws_receiver.next().await {
        let msg = match msg_result {
            Ok(m) => m,
            Err(_) => break,
        };

        let data = match msg {
            tokio_tungstenite::tungstenite::Message::Binary(d) => d,
            tokio_tungstenite::tungstenite::Message::Close(_) => break,
            _ => continue,
        };

        let decoded = match Message::decode(&data) {
            Ok(m) => m,
            Err(e) => {
                log("ws-server", &format!("Decode error: {}", e));
                let err = Message::Error { code: ERR_DECODE, message: e };
                let _ = tx.send(err.encode()).await;
                continue;
            }
        };

        match decoded {
            Message::Hello { client_language: cl } => {
                client_language = cl.clone();
                log("ws-server", &format!("HELLO from {}, session={}, time={}", cl, session_id, now_ms()));
                let bonjour = Message::Bonjour { server_language: SERVER_LANG.to_string() };
                let _ = tx.send(bonjour.encode()).await;
            }
            Message::EchoRequest { id, meta, data } => {
                log("ws-server", &format!("ECHO_REQUEST id={} meta={} data={}", id, meta, data));
                let mut kv = HashMap::new();
                kv.insert("id".to_string(), id.to_string());
                kv.insert("idx".to_string(), data.clone());
                kv.insert("data".to_string(), data);
                kv.insert("meta".to_string(), client_language.clone());
                let resp = Message::EchoResponse {
                    status: 200,
                    results: vec![EchoResult { idx: now_ms(), type_: 0, kv }],
                };
                let _ = tx.send(resp.encode()).await;
            }
            Message::KissResponse { language, encoding, time_zone } => {
                log("ws-server", &format!("KISS_RESPONSE lang={} enc={} tz={}", language, encoding, time_zone));
            }
            Message::Pong { timestamp_ms } => {
                *last_pong.lock().unwrap() = timestamp_ms;
                log("ws-server", &format!("PONG ts={}", timestamp_ms));
            }
            Message::RandomNumber { id, number } => {
                log("ws-server", &format!("RANDOM_NUMBER id={} number={}", id, number));
                let hash = hash_number(number);
                let resp = Message::HashResponse { id, hash_hex: hash.clone() };
                let _ = tx.send(resp.encode()).await;
                log("ws-server", &format!("HASH_RESPONSE id={} hash={}", id, hash));
            }
            Message::Disconnect { reason } => {
                log("ws-server", &format!("DISCONNECT reason={}", reason));
                break;
            }
            Message::Error { code, message } => {
                log("ws-server", &format!("ERROR code={} msg={}", code, message));
            }
            _ => {
                log("ws-server", "Unknown message type");
            }
        }
    }

    drop(tx);
    let _ = sender_task.await;
    log("ws-server", &format!("[{}] session-", user_id));
}
