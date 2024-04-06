mod hello_common;

use std::env;
use std::time::Duration;
use tokio::time::interval;
use crate::hello_common::{CLIENT_NAME_HEAD, WS_ADDRESS};
use futures_util::{future, pin_mut, StreamExt};
use tokio::io::{AsyncReadExt,AsyncWriteExt};
use tokio_tungstenite::{connect_async, tungstenite::protocol::Message};
use tungstenite::client::IntoClientRequest;
use uuid::Uuid;

#[tokio::main]
async fn main() {
    // standard input sender & receiver
    let (tx, rx) = futures_channel::mpsc::unbounded();
    tokio::spawn(read_stdin(tx.clone()));
    let unbounded_sender = tx.clone();
    // Send a message every 5 seconds
    let mut interval = interval(Duration::from_secs(5));
    tokio::spawn(async move {
        loop {
            let _ = interval.tick().await;
            let uuid = Uuid::new_v4();
            let msg_id = uuid.to_string();
            let msg_data = chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string();
            let msg = format!("{{\"msg_id\": \"{}\", \"msg_data\": \"{}\"}}", msg_id, msg_data);
            if unbounded_sender.unbounded_send(Message::Text(msg)).is_err() {
                break;
            }
        }
    });

    let mut request = WS_ADDRESS.into_client_request().unwrap();
    // headers
    let headers = request.headers_mut();
    let client_name = env::args().nth(1).unwrap_or_else(|| "C".to_string());
    headers.insert(CLIENT_NAME_HEAD, client_name.parse().unwrap());

    let (ws_stream, _) = connect_async(request).await.expect("Failed to connect");
    println!("WebSocket handshake has been successfully completed");
    let (write, read) = ws_stream.split();

    let stdin_to_ws = rx.map(Ok).forward(write);
    let ws_to_stdout = {
        read.for_each(|message| async {
            let data = message.unwrap().into_data();
            tokio::io::stdout().write_all(&data).await.unwrap();
        })
    };
    pin_mut!(stdin_to_ws, ws_to_stdout);
    future::select(stdin_to_ws, ws_to_stdout).await;
}

async fn read_stdin(tx: futures_channel::mpsc::UnboundedSender<Message>) {
    let mut stdin = tokio::io::stdin();
    loop {
        let mut buf = vec![0; 1024];
        let n = match stdin.read(&mut buf).await {
            Err(_) | Ok(0) => break,
            Ok(n) => n,
        };
        buf.truncate(n);
        tx.unbounded_send(Message::binary(buf)).unwrap();
    }
}