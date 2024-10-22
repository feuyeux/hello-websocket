mod hello_common;

use crate::hello_common::EchoRequest;
use futures_channel::mpsc::unbounded;
use futures_util::StreamExt;
use hello_common::WS_ADDRESS;
use tokio_tungstenite::connect_async;
use tungstenite::Message;

#[tokio::main]
async fn main() {
    let (tx, _rx) = unbounded();
    let (mut ws_stream, _) = connect_async(WS_ADDRESS).await.expect("Failed to connect");
    println!("WebSocket handshake has been successfully completed");

    let request = EchoRequest {
        id: 1,
        meta: "Rust".to_string(),
        data: "Hello".to_string(),
    };
    // Encode the request
    let encoded_request = request.encode();
    println!("Sending request: {:?}", request);
    // Send the request
    tx.unbounded_send(Message::binary(encoded_request)).unwrap();
    println!("Request sent");

    // Receive message and print
    while let Some(message) = ws_stream.next().await {
        match message {
            Ok(msg) => {
                if let Ok(text) = msg.to_text() {
                    println!("Received: {}", text);
                } else {
                    println!("Received non-text message");
                }
            }
            Err(e) => {
                println!("Error receiving message: {}", e);
                break;
            }
        }
    }
}
