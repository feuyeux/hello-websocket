mod hello_common;

use crate::hello_common::{EchoKv, EchoRequest, EchoResponse, EchoResult, ADDRESS};
use futures_util::{SinkExt, StreamExt};
use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::Arc;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::mpsc;
use tokio::sync::mpsc::{Receiver, Sender};
use tokio::sync::Mutex;
use tokio_tungstenite::accept_async;
use tokio_tungstenite::tungstenite::Message;

type PeerMap = Arc<Mutex<HashMap<SocketAddr, Sender<Message>>>>;
type NameMap = Arc<Mutex<HashMap<SocketAddr, String>>>;

async fn handle_connection(
    raw_stream: TcpStream,
    addr: SocketAddr,
    peer_map: PeerMap,
    name_map: NameMap,
) {
    let ws_stream = accept_async(raw_stream)
        .await
        .expect("Error during the websocket handshake occurred");
    println!("New WebSocket connection: {}", addr);

    let (tx, mut rx): (Sender<Message>, Receiver<Message>) = mpsc::channel(100);
    peer_map.lock().await.insert(addr, tx);
    name_map.lock().await.insert(addr, addr.to_string());
    let peer_map_clone = peer_map.clone();

    let (mut ws_sender, mut ws_receiver) = ws_stream.split();

    // Spawn a task to forward messages from the channel to the WebSocket
    tokio::spawn(async move {
        while let Some(msg) = rx.recv().await {
            if ws_sender.send(msg).await.is_err() {
                break;
            }
        }
    });

    while let Some(message) = ws_receiver.next().await {
        match message {
            Ok(msg) => match msg {
                Message::Binary(bin) => {
                    println!("Received binary message from {}: {:?}", addr, bin);
                    let request: EchoRequest = EchoRequest::decode(&bin);
                    println!("Decoded request: {:?}", request);

                    let response = EchoResponse {
                        status: 200,
                        results: vec![EchoResult {
                            id: request.id,
                            r#type: "OK".to_string(),
                            kv: EchoKv {
                                id: "uuid".to_string(),
                                idx: 1,
                                data: request.data,
                                meta: request.meta,
                            },
                        }],
                    };

                    let response_msg = Message::binary(response.encode());
                    let peers = peer_map_clone.lock().await;
                    for (peer_addr, tx) in peers.iter() {
                        if *peer_addr != addr {
                            tx.send(response_msg.clone()).await.unwrap();
                        }
                    }
                }
                Message::Text(text) => {
                    println!("Received text message from {}: {}", addr, text);
                    // Handle text message if needed
                }
                _ => {
                    println!("Received other type of message from {}", addr);
                }
            },
            Err(e) => {
                println!("Error receiving message from {}: {}", addr, e);
                break;
            }
        }
    }

    peer_map.lock().await.remove(&addr);
    name_map.lock().await.remove(&addr);
    println!("Connection closed: {}", addr);
}

#[tokio::main]
async fn main() {
    let addr = ADDRESS.to_string();
    let listener = TcpListener::bind(&addr)
        .await
        .expect("Can't bind to address");

    let peer_map: PeerMap = Arc::new(Mutex::new(HashMap::new()));
    let name_map: NameMap = Arc::new(Mutex::new(HashMap::new()));

    println!("Listening on: {}", addr);

    while let Ok((stream, addr)) = listener.accept().await {
        tokio::spawn(handle_connection(
            stream,
            addr,
            peer_map.clone(),
            name_map.clone(),
        ));
    }
}
