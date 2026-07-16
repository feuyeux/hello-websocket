//! Hello WebSocket Protocol Codec - Rust implementation.
//!
//! This library implements the canonical binary protocol defined in PROTOCOL.md.

use sha2::{Digest, Sha256};
use std::collections::HashMap;
use std::time::{SystemTime, UNIX_EPOCH};

// ─── Constants ───────────────────────────────────────────────────────────

pub const PORT: u16 = 9898;
pub const MAGIC: u8 = 0x48;
pub const VERSION: u8 = 0x01;
pub const HEADER_LEN: usize = 8;
pub const SERVER_LANG: &str = "RUST";
pub const CLIENT_LANG: &str = "RUST";

pub const MSG_HELLO: u8 = 0x01;
pub const MSG_BONJOUR: u8 = 0x02;
pub const MSG_ECHO_REQUEST: u8 = 0x03;
pub const MSG_ECHO_RESPONSE: u8 = 0x04;
pub const MSG_KISS_REQUEST: u8 = 0x05;
pub const MSG_KISS_RESPONSE: u8 = 0x06;
pub const MSG_PING: u8 = 0x07;
pub const MSG_PONG: u8 = 0x08;
pub const MSG_TIME_NOTIFICATION: u8 = 0x09;
pub const MSG_RANDOM_NUMBER: u8 = 0x0A;
pub const MSG_HASH_RESPONSE: u8 = 0x0B;
pub const MSG_DISCONNECT: u8 = 0x0C;
pub const MSG_ERROR: u8 = 0x7F;

pub const ERR_DECODE: i32 = 0x01;
pub const ERR_UNKNOWN_MSG_TYPE: i32 = 0x02;
pub const ERR_TRUNCATED_PAYLOAD: i32 = 0x03;
pub const ERR_BAD_MAGIC: i32 = 0x04;
pub const ERR_BAD_VERSION: i32 = 0x05;
pub const ERR_SESSION_NOT_FOUND: i32 = 0x06;
pub const ERR_INTERNAL: i32 = 0x07;

pub const PING_INTERVAL_MS: u64 = 1000;
pub const SESSION_TIMEOUT_MS: u64 = 60000;
pub const TIME_INTERVAL_MS: u64 = 5000;
pub const RANDOM_INTERVAL_MS: u64 = 5000;
pub const KISS_INTERVAL_MS: u64 = 5000;

// ─── ByteWriter ──────────────────────────────────────────────────────────

pub struct ByteWriter {
    buf: Vec<u8>,
}

impl ByteWriter {
    pub fn new() -> Self {
        Self { buf: Vec::new() }
    }
    pub fn write_u8(&mut self, v: u8) {
        self.buf.push(v);
    }
    pub fn write_u16(&mut self, v: u16) {
        self.buf.extend_from_slice(&v.to_be_bytes());
    }
    pub fn write_u32(&mut self, v: u32) {
        self.buf.extend_from_slice(&v.to_be_bytes());
    }
    pub fn write_i32(&mut self, v: i32) {
        self.buf.extend_from_slice(&v.to_be_bytes());
    }
    pub fn write_i64(&mut self, v: i64) {
        self.buf.extend_from_slice(&v.to_be_bytes());
    }
    pub fn write_string(&mut self, s: &str) {
        let b = s.as_bytes();
        self.write_u32(b.len() as u32);
        self.buf.extend_from_slice(b);
    }
    pub fn write_kv(&mut self, m: &HashMap<String, String>) {
        self.write_u32(m.len() as u32);
        for (k, v) in m {
            self.write_string(k);
            self.write_string(v);
        }
    }
    pub fn data(self) -> Vec<u8> {
        self.buf
    }
}

impl Default for ByteWriter {
    fn default() -> Self {
        Self::new()
    }
}

// ─── ByteReader ─────────────────────────────────────────────────────────

pub struct ByteReader<'a> {
    data: &'a [u8],
    pos: usize,
}

impl<'a> ByteReader<'a> {
    pub fn new(data: &'a [u8]) -> Self {
        Self { data, pos: 0 }
    }
    pub fn remaining(&self) -> usize {
        self.data.len() - self.pos
    }
    pub fn read_u8(&mut self) -> Result<u8, String> {
        if self.pos + 1 > self.data.len() {
            return Err("unexpected end of data".into());
        }
        let v = self.data[self.pos];
        self.pos += 1;
        Ok(v)
    }
    pub fn read_u16(&mut self) -> Result<u16, String> {
        if self.pos + 2 > self.data.len() {
            return Err("unexpected end of data".into());
        }
        let v = u16::from_be_bytes([self.data[self.pos], self.data[self.pos + 1]]);
        self.pos += 2;
        Ok(v)
    }
    pub fn read_u32(&mut self) -> Result<u32, String> {
        if self.pos + 4 > self.data.len() {
            return Err("unexpected end of data".into());
        }
        let v = u32::from_be_bytes(self.data[self.pos..self.pos + 4].try_into().unwrap());
        self.pos += 4;
        Ok(v)
    }
    pub fn read_i32(&mut self) -> Result<i32, String> {
        Ok(self.read_u32()? as i32)
    }
    pub fn read_i64(&mut self) -> Result<i64, String> {
        if self.pos + 8 > self.data.len() {
            return Err("unexpected end of data".into());
        }
        let v = i64::from_be_bytes(self.data[self.pos..self.pos + 8].try_into().unwrap());
        self.pos += 8;
        Ok(v)
    }
    pub fn read_string(&mut self) -> Result<String, String> {
        let ln = self.read_u32()? as usize;
        if self.pos + ln > self.data.len() {
            return Err("string length exceeds data".into());
        }
        let s = String::from_utf8(self.data[self.pos..self.pos + ln].to_vec())
            .map_err(|_| "invalid UTF-8 string".to_string())?;
        self.pos += ln;
        Ok(s)
    }
    pub fn read_kv(&mut self) -> Result<HashMap<String, String>, String> {
        let count = self.read_u32()?;
        if count as usize > self.remaining() / 8 {
            return Err("kv count exceeds remaining payload".into());
        }
        let mut m = HashMap::with_capacity(count as usize);
        for _ in 0..count {
            m.insert(self.read_string()?, self.read_string()?);
        }
        Ok(m)
    }
}

// ─── Frame Codec ─────────────────────────────────────────────────────────

pub fn encode_frame(msg_type: u8, payload: &[u8]) -> Vec<u8> {
    let mut buf = Vec::with_capacity(HEADER_LEN + payload.len());
    buf.push(MAGIC);
    buf.push(VERSION);
    buf.push(msg_type);
    buf.push(0x00);
    buf.extend_from_slice(&(payload.len() as u32).to_be_bytes());
    buf.extend_from_slice(payload);
    buf
}

pub fn decode_frame(data: &[u8]) -> Result<(u8, &[u8]), String> {
    if data.len() < HEADER_LEN {
        return Err(format!("frame too short: {}", data.len()));
    }
    if data[0] != MAGIC {
        return Err(format!("bad magic: 0x{:02x}", data[0]));
    }
    if data[1] != VERSION {
        return Err(format!("bad version: 0x{:02x}", data[1]));
    }
    let msg_type = data[2];
    let payload_len = u32::from_be_bytes(data[4..8].try_into().unwrap()) as usize;
    if payload_len != data.len() - HEADER_LEN {
        return Err(format!(
            "payload length mismatch: declared {}, available {}",
            payload_len,
            data.len() - HEADER_LEN
        ));
    }
    Ok((msg_type, &data[HEADER_LEN..HEADER_LEN + payload_len]))
}

// ─── Message Types ───────────────────────────────────────────────────────

#[derive(Debug)]
pub enum Message {
    Hello {
        client_language: String,
    },
    Bonjour {
        server_language: String,
    },
    EchoRequest {
        id: i64,
        meta: String,
        data: String,
    },
    EchoResponse {
        status: i32,
        results: Vec<EchoResult>,
    },
    KissRequest {
        os_name: String,
        os_version: String,
        os_release: String,
        os_architecture: String,
    },
    KissResponse {
        language: String,
        encoding: String,
        time_zone: String,
    },
    Ping {
        timestamp_ms: i64,
    },
    Pong {
        timestamp_ms: i64,
    },
    TimeNotification {
        timestamp_ms: i64,
        iso8601: String,
    },
    RandomNumber {
        id: i64,
        number: i64,
    },
    HashResponse {
        id: i64,
        hash_hex: String,
    },
    Disconnect {
        reason: String,
    },
    Error {
        code: i32,
        message: String,
    },
}

#[derive(Debug)]
pub struct EchoResult {
    pub idx: i64,
    pub type_: u8,
    pub kv: HashMap<String, String>,
}

impl Message {
    pub fn encode(&self) -> Vec<u8> {
        let mut w = ByteWriter::new();
        match self {
            Message::Hello { client_language } => {
                w.write_string(client_language);
                encode_frame(MSG_HELLO, &w.data())
            }
            Message::Bonjour { server_language } => {
                w.write_string(server_language);
                encode_frame(MSG_BONJOUR, &w.data())
            }
            Message::EchoRequest { id, meta, data } => {
                w.write_i64(*id);
                w.write_string(meta);
                w.write_string(data);
                encode_frame(MSG_ECHO_REQUEST, &w.data())
            }
            Message::EchoResponse { status, results } => {
                w.write_i32(*status);
                w.write_u32(results.len() as u32);
                for r in results {
                    w.write_i64(r.idx);
                    w.write_u8(r.type_);
                    w.write_kv(&r.kv);
                }
                encode_frame(MSG_ECHO_RESPONSE, &w.data())
            }
            Message::KissRequest {
                os_name,
                os_version,
                os_release,
                os_architecture,
            } => {
                w.write_string(os_name);
                w.write_string(os_version);
                w.write_string(os_release);
                w.write_string(os_architecture);
                encode_frame(MSG_KISS_REQUEST, &w.data())
            }
            Message::KissResponse {
                language,
                encoding,
                time_zone,
            } => {
                w.write_string(language);
                w.write_string(encoding);
                w.write_string(time_zone);
                encode_frame(MSG_KISS_RESPONSE, &w.data())
            }
            Message::Ping { timestamp_ms } => {
                w.write_i64(*timestamp_ms);
                encode_frame(MSG_PING, &w.data())
            }
            Message::Pong { timestamp_ms } => {
                w.write_i64(*timestamp_ms);
                encode_frame(MSG_PONG, &w.data())
            }
            Message::TimeNotification {
                timestamp_ms,
                iso8601,
            } => {
                w.write_i64(*timestamp_ms);
                w.write_string(iso8601);
                encode_frame(MSG_TIME_NOTIFICATION, &w.data())
            }
            Message::RandomNumber { id, number } => {
                w.write_i64(*id);
                w.write_i64(*number);
                encode_frame(MSG_RANDOM_NUMBER, &w.data())
            }
            Message::HashResponse { id, hash_hex } => {
                w.write_i64(*id);
                w.write_string(hash_hex);
                encode_frame(MSG_HASH_RESPONSE, &w.data())
            }
            Message::Disconnect { reason } => {
                w.write_string(reason);
                encode_frame(MSG_DISCONNECT, &w.data())
            }
            Message::Error { code, message } => {
                w.write_i32(*code);
                w.write_string(message);
                encode_frame(MSG_ERROR, &w.data())
            }
        }
    }

    pub fn decode(data: &[u8]) -> Result<Self, String> {
        let (msg_type, payload) = decode_frame(data)?;
        let mut r = ByteReader::new(payload);
        match msg_type {
            MSG_HELLO => Ok(Message::Hello {
                client_language: r.read_string()?,
            }),
            MSG_BONJOUR => Ok(Message::Bonjour {
                server_language: r.read_string()?,
            }),
            MSG_ECHO_REQUEST => Ok(Message::EchoRequest {
                id: r.read_i64()?,
                meta: r.read_string()?,
                data: r.read_string()?,
            }),
            MSG_ECHO_RESPONSE => {
                let status = r.read_i32()?;
                let count = r.read_u32()? as usize;
                if count > r.remaining() / 13 {
                    return Err("result count exceeds remaining payload".into());
                }
                let mut results = Vec::with_capacity(count);
                for _ in 0..count {
                    results.push(EchoResult {
                        idx: r.read_i64()?,
                        type_: r.read_u8()?,
                        kv: r.read_kv()?,
                    });
                }
                Ok(Message::EchoResponse { status, results })
            }
            MSG_KISS_REQUEST => Ok(Message::KissRequest {
                os_name: r.read_string()?,
                os_version: r.read_string()?,
                os_release: r.read_string()?,
                os_architecture: r.read_string()?,
            }),
            MSG_KISS_RESPONSE => Ok(Message::KissResponse {
                language: r.read_string()?,
                encoding: r.read_string()?,
                time_zone: r.read_string()?,
            }),
            MSG_PING => Ok(Message::Ping {
                timestamp_ms: r.read_i64()?,
            }),
            MSG_PONG => Ok(Message::Pong {
                timestamp_ms: r.read_i64()?,
            }),
            MSG_TIME_NOTIFICATION => Ok(Message::TimeNotification {
                timestamp_ms: r.read_i64()?,
                iso8601: r.read_string()?,
            }),
            MSG_RANDOM_NUMBER => Ok(Message::RandomNumber {
                id: r.read_i64()?,
                number: r.read_i64()?,
            }),
            MSG_HASH_RESPONSE => Ok(Message::HashResponse {
                id: r.read_i64()?,
                hash_hex: r.read_string()?,
            }),
            MSG_DISCONNECT => Ok(Message::Disconnect {
                reason: r.read_string()?,
            }),
            MSG_ERROR => Ok(Message::Error {
                code: r.read_i32()?,
                message: r.read_string()?,
            }),
            _ => Err(format!("unknown message type: 0x{:02x}", msg_type)),
        }
    }
}

// ─── Utility ─────────────────────────────────────────────────────────────

pub fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64
}

pub fn now_iso() -> String {
    let secs = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs();
    let (y, mo, d, h, mi, s) = unix_to_utc(secs);
    format!("{:04}-{:02}-{:02}T{:02}:{:02}:{:02}Z", y, mo, d, h, mi, s)
}

fn unix_to_utc(secs: u64) -> (u32, u32, u32, u32, u32, u32) {
    let days = (secs / 86400) as u32;
    let rem = (secs % 86400) as u32;
    let h = rem / 3600;
    let mi = (rem % 3600) / 60;
    let s = rem % 60;
    let mut y = 1970u32;
    let mut d = days;
    loop {
        let dy = if is_leap(y) { 366 } else { 365 };
        if d < dy {
            break;
        }
        d -= dy;
        y += 1;
    }
    let months = if is_leap(y) {
        [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    } else {
        [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    };
    let mut mo = 1u32;
    for &dm in &months {
        if d < dm {
            break;
        }
        d -= dm;
        mo += 1;
    }
    (y, mo, d + 1, h, mi, s)
}

#[allow(clippy::manual_is_multiple_of)]
fn is_leap(y: u32) -> bool {
    (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
}

pub fn hash_number(num: i64) -> String {
    let mut hasher = Sha256::new();
    hasher.update(num.to_string().as_bytes());
    let result = hasher.finalize();
    let hex: String = result.iter().map(|b| format!("{:02x}", b)).collect();
    hex[..10].to_string()
}

pub fn log(name: &str, msg: &str) {
    let ts = {
        let secs = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();
        let (y, mo, d, h, mi, s) = unix_to_utc(secs);
        format!("{:04}-{:02}-{:02} {:02}:{:02}:{:02}", y, mo, d, h, mi, s)
    };
    println!("[{}] [INFO] [{}] {}", ts, name, msg);
}

// ─── Tests ───────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    #[test]
    fn test_hello_worked_example() {
        let msg = Message::Hello {
            client_language: "Go".into(),
        };
        let data = msg.encode();
        let expected: [u8; 14] = [
            0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x02, 0x47, 0x6F,
        ];
        assert_eq!(data, expected);
    }

    #[test]
    fn test_round_trip_all() {
        let messages = vec![
            Message::Hello {
                client_language: "Rust".into(),
            },
            Message::Bonjour {
                server_language: "Java".into(),
            },
            Message::EchoRequest {
                id: 42,
                meta: "Python".into(),
                data: "hello".into(),
            },
            Message::Ping {
                timestamp_ms: 1700000000000,
            },
            Message::Pong {
                timestamp_ms: 1700000000001,
            },
            Message::TimeNotification {
                timestamp_ms: 1700000000000,
                iso8601: "2023-11-14T22:13:20Z".into(),
            },
            Message::RandomNumber { id: 99, number: 42 },
            Message::HashResponse {
                id: 99,
                hash_hex: "7688b6ef5a".into(),
            },
            Message::Disconnect {
                reason: "bye".into(),
            },
            Message::Error {
                code: ERR_UNKNOWN_MSG_TYPE,
                message: "bad type".into(),
            },
        ];
        for orig in messages {
            let decoded = Message::decode(&orig.encode()).unwrap();
            assert_eq!(format!("{:?}", orig), format!("{:?}", decoded));
        }
    }

    #[test]
    fn test_round_trip_echo_response() {
        let mut kv = HashMap::new();
        kv.insert("id".into(), "1".into());
        kv.insert("data".into(), "Hello".into());
        let orig = Message::EchoResponse {
            status: 200,
            results: vec![EchoResult {
                idx: 123,
                type_: 0,
                kv,
            }],
        };
        let decoded = Message::decode(&orig.encode()).unwrap();
        match decoded {
            Message::EchoResponse { status, results } => {
                assert_eq!(status, 200);
                assert_eq!(results.len(), 1);
                assert_eq!(results[0].kv.get("id"), Some(&"1".to_string()));
            }
            _ => panic!("wrong type"),
        }
    }

    #[test]
    fn test_round_trip_kiss() {
        let orig = Message::KissRequest {
            os_name: "Linux".into(),
            os_version: "6.6".into(),
            os_release: "arch".into(),
            os_architecture: "AMD64".into(),
        };
        let decoded = Message::decode(&orig.encode()).unwrap();
        match decoded {
            Message::KissRequest {
                os_name,
                os_architecture,
                ..
            } => {
                assert_eq!(os_name, "Linux");
                assert_eq!(os_architecture, "AMD64");
            }
            _ => panic!("wrong type"),
        }
    }

    #[test]
    fn test_bad_magic() {
        let data = [0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00];
        assert!(decode_frame(&data).is_err());
    }

    #[test]
    fn test_bad_version() {
        let data = [0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00];
        assert!(decode_frame(&data).is_err());
    }

    #[test]
    fn test_truncated_payload() {
        let data = [0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF];
        assert!(decode_frame(&data).is_err());
    }

    #[test]
    fn test_hash_number() {
        let h = hash_number(42);
        assert_eq!(h.len(), 10);
        assert_eq!(h, hash_number(42));
    }
}
