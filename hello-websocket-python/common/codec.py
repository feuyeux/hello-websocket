#!/usr/bin/env python3
"""Hello WebSocket Protocol Codec - Python implementation.

This module implements the canonical binary protocol defined in PROTOCOL.md.
It provides constants, primitive encoders/decoders, message types, frame codec,
and message dispatch for all 13 message types.
"""

import hashlib
import struct
import time
from dataclasses import dataclass, field
from typing import Optional

# ─── Constants ────────────────────────────────────────────────────────────

PORT = 9898
MAGIC = 0x48
VERSION = 0x01
HEADER_LEN = 8
SERVER_LANG = "PYTHON"
CLIENT_LANG = "PYTHON"

# Message types
MSG_HELLO = 0x01
MSG_BONJOUR = 0x02
MSG_ECHO_REQUEST = 0x03
MSG_ECHO_RESPONSE = 0x04
MSG_KISS_REQUEST = 0x05
MSG_KISS_RESPONSE = 0x06
MSG_PING = 0x07
MSG_PONG = 0x08
MSG_TIME_NOTIFICATION = 0x09
MSG_RANDOM_NUMBER = 0x0A
MSG_HASH_RESPONSE = 0x0B
MSG_DISCONNECT = 0x0C
MSG_ERROR = 0x7F

# Error codes
ERR_DECODE = 0x01
ERR_UNKNOWN_MSG_TYPE = 0x02
ERR_TRUNCATED_PAYLOAD = 0x03
ERR_BAD_MAGIC = 0x04
ERR_BAD_VERSION = 0x05
ERR_SESSION_NOT_FOUND = 0x06
ERR_INTERNAL = 0x07

# Intervals (seconds)
PING_INTERVAL = 1.0
SESSION_TIMEOUT = 60.0
TIME_INTERVAL = 5.0
RANDOM_INTERVAL = 5.0
KISS_INTERVAL = 5.0


# ─── Primitive Encoders ──────────────────────────────────────────────────

class ByteWriter:
    def __init__(self):
        self.buf = bytearray()

    def write_u8(self, v: int):
        self.buf.append(v & 0xFF)

    def write_u16(self, v: int):
        self.buf.extend(struct.pack(">H", v))

    def write_u32(self, v: int):
        self.buf.extend(struct.pack(">I", v))

    def write_i32(self, v: int):
        self.buf.extend(struct.pack(">i", v))

    def write_i64(self, v: int):
        self.buf.extend(struct.pack(">q", v))

    def write_string(self, s: str):
        b = s.encode("utf-8")
        self.write_u32(len(b))
        self.buf.extend(b)

    def write_kv(self, m: dict):
        self.write_u32(len(m))
        for k, v in m.items():
            self.write_string(k)
            self.write_string(v)

    def data(self) -> bytes:
        return bytes(self.buf)


# ─── Primitive Decoders ─────────────────────────────────────────────────

class ByteReader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def remaining(self) -> int:
        return len(self.data) - self.pos

    def require(self, size: int, field: str):
        if size < 0 or size > self.remaining():
            raise ValueError(f"{field} requires {size} bytes, only {self.remaining()} remain")

    def read_u8(self) -> int:
        self.require(1, "u8")
        v = self.data[self.pos]
        self.pos += 1
        return v

    def read_u16(self) -> int:
        self.require(2, "u16")
        v = struct.unpack(">H", self.data[self.pos:self.pos + 2])[0]
        self.pos += 2
        return v

    def read_u32(self) -> int:
        self.require(4, "u32")
        v = struct.unpack(">I", self.data[self.pos:self.pos + 4])[0]
        self.pos += 4
        return v

    def read_i32(self) -> int:
        self.require(4, "i32")
        v = struct.unpack(">i", self.data[self.pos:self.pos + 4])[0]
        self.pos += 4
        return v

    def read_i64(self) -> int:
        self.require(8, "i64")
        v = struct.unpack(">q", self.data[self.pos:self.pos + 8])[0]
        self.pos += 8
        return v

    def read_string(self) -> str:
        ln = self.read_u32()
        self.require(ln, "string")
        s = self.data[self.pos:self.pos + ln].decode("utf-8")
        self.pos += ln
        return s

    def read_kv(self) -> dict:
        count = self.read_u32()
        if count > self.remaining() // 8:
            raise ValueError(f"kv count {count} exceeds remaining payload")
        m = {}
        for _ in range(count):
            k = self.read_string()
            v = self.read_string()
            m[k] = v
        return m


# ─── Frame Codec ─────────────────────────────────────────────────────────

def encode_frame(msg_type: int, payload: bytes) -> bytes:
    header = struct.pack(">BBBB", MAGIC, VERSION, msg_type, 0x00)
    header += struct.pack(">I", len(payload))
    return header + payload


class CodecError(ValueError):
    """A decode failure carrying the precise PROTOCOL.md §7 error code.

    Lets callers (e.g. the server's receive loop) classify a decode failure
    exactly instead of pattern-matching the error message text, which
    silently breaks if the wording ever changes.
    """

    def __init__(self, code: int, message: str):
        super().__init__(message)
        self.code = code


def decode_frame(data: bytes):
    if len(data) < HEADER_LEN:
        raise CodecError(ERR_TRUNCATED_PAYLOAD, f"frame too short: {len(data)} bytes")
    magic = data[0]
    if magic != MAGIC:
        raise CodecError(ERR_BAD_MAGIC, f"bad magic: 0x{magic:02x}")
    version = data[1]
    if version != VERSION:
        raise CodecError(ERR_BAD_VERSION, f"bad version: 0x{version:02x}")
    msg_type = data[2]
    payload_len = struct.unpack(">I", data[4:8])[0]
    if payload_len != len(data) - HEADER_LEN:
        raise CodecError(
            ERR_TRUNCATED_PAYLOAD,
            f"payload length mismatch: declared {payload_len}, available {len(data) - HEADER_LEN}",
        )
    return msg_type, data[HEADER_LEN:HEADER_LEN + payload_len]


# ─── Message Types ───────────────────────────────────────────────────────

@dataclass
class Hello:
    client_language: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_string(self.client_language)
        return encode_frame(MSG_HELLO, w.data())


@dataclass
class Bonjour:
    server_language: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_string(self.server_language)
        return encode_frame(MSG_BONJOUR, w.data())


@dataclass
class EchoRequest:
    id: int = 0
    meta: str = ""
    data: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_i64(self.id)
        w.write_string(self.meta)
        w.write_string(self.data)
        return encode_frame(MSG_ECHO_REQUEST, w.data())


@dataclass
class EchoResult:
    idx: int = 0
    type: int = 0
    kv: dict = field(default_factory=dict)


@dataclass
class EchoResponse:
    status: int = 200
    results: list = field(default_factory=list)

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_i32(self.status)
        w.write_u32(len(self.results))
        for r in self.results:
            w.write_i64(r.idx)
            w.write_u8(r.type)
            w.write_kv(r.kv)
        return encode_frame(MSG_ECHO_RESPONSE, w.data())


@dataclass
class KissRequest:
    os_name: str = ""
    os_version: str = ""
    os_release: str = ""
    os_architecture: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_string(self.os_name)
        w.write_string(self.os_version)
        w.write_string(self.os_release)
        w.write_string(self.os_architecture)
        return encode_frame(MSG_KISS_REQUEST, w.data())


@dataclass
class KissResponse:
    language: str = ""
    encoding: str = ""
    time_zone: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_string(self.language)
        w.write_string(self.encoding)
        w.write_string(self.time_zone)
        return encode_frame(MSG_KISS_RESPONSE, w.data())


@dataclass
class Ping:
    timestamp_ms: int = 0

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_i64(self.timestamp_ms)
        return encode_frame(MSG_PING, w.data())


@dataclass
class Pong:
    timestamp_ms: int = 0

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_i64(self.timestamp_ms)
        return encode_frame(MSG_PONG, w.data())


@dataclass
class TimeNotification:
    timestamp_ms: int = 0
    iso8601: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_i64(self.timestamp_ms)
        w.write_string(self.iso8601)
        return encode_frame(MSG_TIME_NOTIFICATION, w.data())


@dataclass
class RandomNumber:
    id: int = 0
    number: int = 0

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_i64(self.id)
        w.write_i64(self.number)
        return encode_frame(MSG_RANDOM_NUMBER, w.data())


@dataclass
class HashResponse:
    id: int = 0
    hash_hex: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_i64(self.id)
        w.write_string(self.hash_hex)
        return encode_frame(MSG_HASH_RESPONSE, w.data())


@dataclass
class Disconnect:
    reason: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_string(self.reason)
        return encode_frame(MSG_DISCONNECT, w.data())


@dataclass
class ErrorMsg:
    code: int = 0
    message: str = ""

    def encode(self) -> bytes:
        w = ByteWriter()
        w.write_i32(self.code)
        w.write_string(self.message)
        return encode_frame(MSG_ERROR, w.data())


# ─── Message Dispatch ────────────────────────────────────────────────────

class Message:
    def __init__(self, msg_type: int):
        self.type = msg_type
        self.hello: Optional[Hello] = None
        self.bonjour: Optional[Bonjour] = None
        self.echo_req: Optional[EchoRequest] = None
        self.echo_resp: Optional[EchoResponse] = None
        self.kiss_req: Optional[KissRequest] = None
        self.kiss_resp: Optional[KissResponse] = None
        self.ping: Optional[Ping] = None
        self.pong: Optional[Pong] = None
        self.time_notif: Optional[TimeNotification] = None
        self.random: Optional[RandomNumber] = None
        self.hash: Optional[HashResponse] = None
        self.disconnect: Optional[Disconnect] = None
        self.error: Optional[ErrorMsg] = None


def decode_message(data: bytes) -> Message:
    msg_type, payload = decode_frame(data)
    r = ByteReader(payload)
    msg = Message(msg_type)

    if msg_type == MSG_HELLO:
        msg.hello = Hello(client_language=r.read_string())
    elif msg_type == MSG_BONJOUR:
        msg.bonjour = Bonjour(server_language=r.read_string())
    elif msg_type == MSG_ECHO_REQUEST:
        msg.echo_req = EchoRequest(id=r.read_i64(), meta=r.read_string(), data=r.read_string())
    elif msg_type == MSG_ECHO_RESPONSE:
        status = r.read_i32()
        count = r.read_u32()
        if count > r.remaining() // 13:
            raise ValueError(f"result count {count} exceeds remaining payload")
        results = []
        for _ in range(count):
            idx = r.read_i64()
            typ = r.read_u8()
            kv = r.read_kv()
            results.append(EchoResult(idx=idx, type=typ, kv=kv))
        msg.echo_resp = EchoResponse(status=status, results=results)
    elif msg_type == MSG_KISS_REQUEST:
        msg.kiss_req = KissRequest(
            os_name=r.read_string(), os_version=r.read_string(),
            os_release=r.read_string(), os_architecture=r.read_string())
    elif msg_type == MSG_KISS_RESPONSE:
        msg.kiss_resp = KissResponse(
            language=r.read_string(), encoding=r.read_string(), time_zone=r.read_string())
    elif msg_type == MSG_PING:
        msg.ping = Ping(timestamp_ms=r.read_i64())
    elif msg_type == MSG_PONG:
        msg.pong = Pong(timestamp_ms=r.read_i64())
    elif msg_type == MSG_TIME_NOTIFICATION:
        msg.time_notif = TimeNotification(timestamp_ms=r.read_i64(), iso8601=r.read_string())
    elif msg_type == MSG_RANDOM_NUMBER:
        msg.random = RandomNumber(id=r.read_i64(), number=r.read_i64())
    elif msg_type == MSG_HASH_RESPONSE:
        msg.hash = HashResponse(id=r.read_i64(), hash_hex=r.read_string())
    elif msg_type == MSG_DISCONNECT:
        msg.disconnect = Disconnect(reason=r.read_string())
    elif msg_type == MSG_ERROR:
        msg.error = ErrorMsg(code=r.read_i32(), message=r.read_string())
    else:
        raise CodecError(ERR_UNKNOWN_MSG_TYPE, f"unknown message type: 0x{msg_type:02x}")

    return msg


# ─── Utility ─────────────────────────────────────────────────────────────

def now_ms() -> int:
    return int(time.time() * 1000)


def now_iso() -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())


def hash_number(num: int) -> str:
    h = hashlib.sha256(str(num).encode("ascii")).hexdigest()
    return h[:10]


def log(name: str, msg: str):
    ts = time.strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{ts}] [INFO] [{name}] {msg}")
