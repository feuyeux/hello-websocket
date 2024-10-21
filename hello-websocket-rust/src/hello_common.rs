#![allow(dead_code)]
#![allow(unused_variables)]

pub const ADDRESS: &'static str = "127.0.0.1:9898";
pub const WS_ADDRESS: &'static str = "ws://127.0.0.1:9898";
pub const SERVER_LAN_HEAD: &'static str = "SVR";
pub const CLIENT_NAME_HEAD: &'static str = "WhoAmI";
// src/protocol.rs
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug)]
pub struct EchoRequest {
    pub id: u64,
    pub meta: String,
    pub data: String,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EchoResponse {
    pub status: u16,
    pub results: Vec<EchoResult>,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EchoResult {
    pub id: u64,
    pub r#type: String,
    pub kv: EchoKv,
}

#[derive(Serialize, Deserialize, Debug)]
pub struct EchoKv {
    pub id: String,
    pub idx: u32,
    pub data: String,
    pub meta: String,
}
// src/protocol.rs
use serde_json;

impl EchoRequest {
    pub fn encode(&self) -> Vec<u8> {
        serde_json::to_vec(self).unwrap()
    }

    pub fn decode(data: &[u8]) -> Self {
        serde_json::from_slice(data).unwrap()
    }
}

impl EchoResponse {
    pub fn encode(&self) -> Vec<u8> {
        serde_json::to_vec(self).unwrap()
    }

    pub fn decode(data: &[u8]) -> Self {
        serde_json::from_slice(data).unwrap()
    }
}