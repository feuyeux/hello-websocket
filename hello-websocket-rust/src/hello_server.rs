mod hello_common;

use std::{
    collections::HashMap,
    env,
    io::Error as IoError,
    net::SocketAddr,
    sync::{Arc, Mutex},
};

use futures_channel::mpsc::{unbounded, UnboundedSender};
use futures_util::{future, pin_mut, stream::TryStreamExt, StreamExt};

use tokio::net::{TcpListener, TcpStream};
use tokio_tungstenite::tungstenite::protocol::Message;
use tungstenite::handshake::server::{ErrorResponse, Request, Response};
use tungstenite::http::HeaderValue;
use crate::hello_common::{ADDRESS, CLIENT_NAME_HEAD, SERVER_LAN_HEAD};

// tungstenite[тунгстените(马其顿语) 钨 tungsten] Message
type Tx = UnboundedSender<Message>;

// 线程安全的引用计数指针 多线程共享HashMap
type PeerMap = Arc<Mutex<HashMap<SocketAddr, Tx>>>;
type NameMap = Arc<Mutex<HashMap<SocketAddr, HeaderValue>>>;

async fn handle_connection(raw_stream: TcpStream, addr: SocketAddr, peer_map: PeerMap, name_map: NameMap) {
    println!("Incoming TCP connection from: {}", addr);
    let callback = |request: &Request, mut response: Response| -> Result<Response, ErrorResponse> {
        for (name, value) in request.headers().iter() {
            println!("Name: {}, value: {}", name.to_string(), value.to_str().expect("expected a value"));
        }
        //access the protocol in the request, then set it in the response
        let client_name = request.headers().get(CLIENT_NAME_HEAD).expect("the client should specify a protocol").to_owned(); //save the protocol to use outside the closure
        name_map.lock().unwrap().insert(addr, client_name);
        response.headers_mut().insert(SERVER_LAN_HEAD, HeaderValue::from_str("RUST").unwrap());
        Ok(response)
    };

    let ws_stream =
        tokio_tungstenite::accept_hdr_async(raw_stream, callback)
            .await
            .expect("Error during the websocket handshake occurred");
    println!("WebSocket connection established: {}", addr);

    // tx Transmit 上行流量(Up-link) Sender
    // rx Receive 下行流量(Down-link) Receiver
    let (tx, rx) = unbounded();
    // Insert the write part of this peer to the peer map.
    peer_map.lock().unwrap().insert(addr, tx);

    // outgoing: Write Stream SplitSink
    // incoming: Read Stream SplitStream
    let (outgoing, incoming) = ws_stream.split();

    // broadcast incoming
    let broadcast_incoming = incoming.try_for_each(|msg| {
        println!("Received a message from {}: {}", addr, msg.to_text().unwrap());
        let peers = peer_map.lock().unwrap();
        let names = name_map.lock().unwrap();

        // We want to broadcast the message to everyone except ourselves.
        let broadcast_recipients =
            peers.iter().filter(|(peer_addr, _)| peer_addr != &&addr).map(|(_, ws_sink)| ws_sink);
        for recipient in broadcast_recipients {
            let client_name = names.get(&&addr).unwrap();
            let message = format!("[Rust] [{}]:{}", client_name.to_str().expect("C"), msg.clone());
            recipient.unbounded_send(Message::Text(message)).unwrap();
        }
        future::ok(())
    });

    let receive_from_others = rx.map(Ok).forward(outgoing);

    pin_mut!(broadcast_incoming, receive_from_others);
    future::select(broadcast_incoming, receive_from_others).await;

    println!("{} disconnected", &addr);
    peer_map.lock().unwrap().remove(&addr);
}

#[tokio::main]
async fn main() -> Result<(), IoError> {
    let addr = env::args().nth(1).unwrap_or_else(|| ADDRESS.to_string());
    // Create the event loop and TCP listener we'll accept connections on.
    let try_socket = TcpListener::bind(&addr).await;
    let listener = try_socket.expect("Failed to bind");
    println!("Listening on: {}", addr);
    let state = PeerMap::new(Mutex::new(HashMap::new()));
    let naming = NameMap::new(Mutex::new(HashMap::new()));
    // Let's spawn the handling of each connection in a separate task.
    while let Ok((stream, addr)) = listener.accept().await {
        tokio::spawn(handle_connection(stream, addr, state.clone(), naming.clone()));
    }
    Ok(())
}
