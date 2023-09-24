mod hello_common;

use std::env;

use futures_util::{future, pin_mut, StreamExt};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio_tungstenite::{connect_async, tungstenite::protocol::Message};
use tungstenite::client::IntoClientRequest;
use crate::hello_common::{CLIENT_NAME_HEAD, WS_ADDRESS};

#[tokio::main]
async fn main() {
    // standard input sender & receiver
    let (stdin_tx, stdin_rx) = futures_channel::mpsc::unbounded();
    tokio::spawn(read_stdin(stdin_tx));

    let mut request = WS_ADDRESS.into_client_request().unwrap();
    let headers = request.headers_mut();
    let client_name = env::args().nth(1).unwrap_or_else(||"C".to_string());
    headers.insert(CLIENT_NAME_HEAD, client_name.parse().unwrap());

    let (ws_stream, _) = connect_async(request)
        .await
        .expect("Failed to connect");
    println!("WebSocket handshake has been successfully completed");
    let (write, read) = ws_stream.split();

    let stdin_to_ws = stdin_rx.map(Ok).forward(write);
    let ws_to_stdout = {
        read.for_each(|message| async {
            let data = message.unwrap().into_data();
            tokio::io::stdout().write_all(&data).await.unwrap();
        })
    };
    pin_mut!(stdin_to_ws, ws_to_stdout);
    future::select(stdin_to_ws, ws_to_stdout).await;
}

// Our helper method which will read data from stdin and send it along the
// sender provided.
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
