'use strict';

const crypto = require('crypto');
const os = require('os');
const { TextDecoder } = require('util');

// Strict UTF-8 decoder: throws on malformed byte sequences instead of
// silently substituting U+FFFD (the default Buffer#toString('utf-8')
// behavior), so ReadString enforces PROTOCOL.md §3's "valid UTF-8" rule.
const UTF8_DECODER = new TextDecoder('utf-8', { fatal: true });

// ─── Constants ───────────────────────────────────────────────────────────

const PORT = 9898;
const MAGIC = 0x48;
const VERSION = 0x01;
const HEADER_LEN = 8;
const SERVER_LANG = 'NODEJS';
const CLIENT_LANG = 'NODEJS';

const MSG_HELLO = 0x01;
const MSG_BONJOUR = 0x02;
const MSG_ECHO_REQUEST = 0x03;
const MSG_ECHO_RESPONSE = 0x04;
const MSG_KISS_REQUEST = 0x05;
const MSG_KISS_RESPONSE = 0x06;
const MSG_PING = 0x07;
const MSG_PONG = 0x08;
const MSG_TIME_NOTIFICATION = 0x09;
const MSG_RANDOM_NUMBER = 0x0A;
const MSG_HASH_RESPONSE = 0x0B;
const MSG_DISCONNECT = 0x0C;
const MSG_ERROR = 0x7F;

const ERR_DECODE = 0x01;
const ERR_UNKNOWN_MSG_TYPE = 0x02;
const ERR_TRUNCATED_PAYLOAD = 0x03;
const ERR_BAD_MAGIC = 0x04;
const ERR_BAD_VERSION = 0x05;
const ERR_SESSION_NOT_FOUND = 0x06;
const ERR_INTERNAL = 0x07;

const PING_INTERVAL = 1000;
const SESSION_TIMEOUT = 60000;
const TIME_INTERVAL = 5000;
const RANDOM_INTERVAL = 5000;
const KISS_INTERVAL = 5000;

// ─── ByteWriter ─────────────────────────────────────────────────────────

class ByteWriter {
    constructor() { this.buf = []; }

    writeU8(v) { this.buf.push(v & 0xFF); }
    writeU16(v) { this.buf.push((v >> 8) & 0xFF, v & 0xFF); }
    writeU32(v) { this.buf.push((v >>> 24) & 0xFF, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF); }
    writeI32(v) { this.writeU32(v >>> 0); }
    writeI64(v) {
        const n = BigInt.asUintN(64, BigInt(v));
        this.writeU32(Number((n >> 32n) & 0xFFFFFFFFn));
        this.writeU32(Number(n & 0xFFFFFFFFn));
    }
    writeString(s) {
        const b = Buffer.from(s, 'utf-8');
        this.writeU32(b.length);
        for (const byte of b) this.buf.push(byte);
    }
    writeKV(m) {
        const keys = Object.keys(m);
        this.writeU32(keys.length);
        for (const k of keys) { this.writeString(k); this.writeString(m[k]); }
    }
    data() { return Buffer.from(this.buf); }
}

// ─── ByteReader ─────────────────────────────────────────────────────────

class ByteReader {
    constructor(data) { this.data = data; this.pos = 0; }

    remaining() { return this.data.length - this.pos; }
    ensure(n, field) {
        if (!Number.isSafeInteger(n) || n < 0 || n > this.remaining()) {
            throw new Error(`${field} requires ${n} bytes, only ${this.remaining()} remain`);
        }
    }
    readU8() { this.ensure(1, 'u8'); return this.data[this.pos++]; }
    readU16() { this.ensure(2, 'u16'); const v = (this.data[this.pos] << 8) | this.data[this.pos + 1]; this.pos += 2; return v; }
    readU32() {
        this.ensure(4, 'u32');
        const v = (this.data[this.pos] * 0x1000000) + (this.data[this.pos + 1] << 16) +
                  (this.data[this.pos + 2] << 8) + this.data[this.pos + 3];
        this.pos += 4;
        return v;
    }
    readI32() { return this.readU32() | 0; }
    readI64() {
        const hi = BigInt(this.readU32());
        const lo = BigInt(this.readU32());
        return BigInt.asIntN(64, (hi << 32n) | lo);
    }
    readString() {
        const ln = this.readU32();
        this.ensure(ln, 'string');
        const bytes = this.data.subarray(this.pos, this.pos + ln);
        // PROTOCOL.md §3 requires string payloads to be valid UTF-8 bytes.
        // Buffer#toString('utf-8') silently replaces invalid sequences with
        // U+FFFD instead of rejecting them; use a fatal TextDecoder so
        // malformed input is rejected like the Go/Rust/Python/C++ decoders.
        let s;
        try {
            s = UTF8_DECODER.decode(bytes);
        } catch {
            throw new Error('string payload is not valid UTF-8');
        }
        this.pos += ln;
        return s;
    }
    readKV() {
        const count = this.readU32();
        if (count > Math.floor(this.remaining() / 8)) throw new Error(`kv count ${count} exceeds remaining payload`);
        const m = {};
        for (let i = 0; i < count; i++) {
            const k = this.readString();
            const v = this.readString();
            m[k] = v;
        }
        return m;
    }
}

// ─── Frame Codec ─────────────────────────────────────────────────────────

function encodeFrame(msgType, payload) {
    const header = Buffer.alloc(HEADER_LEN);
    header[0] = MAGIC;
    header[1] = VERSION;
    header[2] = msgType;
    header[3] = 0x00;
    header.writeUInt32BE(payload.length, 4);
    return Buffer.concat([header, payload]);
}

function decodeFrame(data) {
    if (data.length < HEADER_LEN) throw new Error(`frame too short: ${data.length}`);
    if (data[0] !== MAGIC) throw new Error(`bad magic: 0x${data[0].toString(16)}`);
    if (data[1] !== VERSION) throw new Error(`bad version: 0x${data[1].toString(16)}`);
    const msgType = data[2];
    const payloadLen = data.readUInt32BE(4);
    if (payloadLen !== data.length - HEADER_LEN) {
        throw new Error(`payload length mismatch: declared ${payloadLen}, available ${data.length - HEADER_LEN}`);
    }
    return { msgType, payload: data.slice(HEADER_LEN, HEADER_LEN + payloadLen) };
}

// ─── Message Encoders ────────────────────────────────────────────────────

function encodeHello(clientLanguage) {
    const w = new ByteWriter();
    w.writeString(clientLanguage);
    return encodeFrame(MSG_HELLO, w.data());
}

function encodeBonjour(serverLanguage) {
    const w = new ByteWriter();
    w.writeString(serverLanguage);
    return encodeFrame(MSG_BONJOUR, w.data());
}

function encodeEchoRequest(id, meta, data) {
    const w = new ByteWriter();
    w.writeI64(id);
    w.writeString(meta);
    w.writeString(data);
    return encodeFrame(MSG_ECHO_REQUEST, w.data());
}

function encodeEchoResponse(status, results) {
    const w = new ByteWriter();
    w.writeI32(status);
    w.writeU32(results.length);
    for (const r of results) {
        w.writeI64(r.idx);
        w.writeU8(r.type);
        w.writeKV(r.kv);
    }
    return encodeFrame(MSG_ECHO_RESPONSE, w.data());
}

function encodeKissRequest(osName, osVersion, osRelease, osArch) {
    const w = new ByteWriter();
    w.writeString(osName); w.writeString(osVersion);
    w.writeString(osRelease); w.writeString(osArch);
    return encodeFrame(MSG_KISS_REQUEST, w.data());
}

function encodeKissResponse(language, encoding, timeZone) {
    const w = new ByteWriter();
    w.writeString(language); w.writeString(encoding); w.writeString(timeZone);
    return encodeFrame(MSG_KISS_RESPONSE, w.data());
}

function encodePing(ts) {
    const w = new ByteWriter();
    w.writeI64(ts);
    return encodeFrame(MSG_PING, w.data());
}

function encodePong(ts) {
    const w = new ByteWriter();
    w.writeI64(ts);
    return encodeFrame(MSG_PONG, w.data());
}

function encodeTimeNotification(ts, iso) {
    const w = new ByteWriter();
    w.writeI64(ts);
    w.writeString(iso);
    return encodeFrame(MSG_TIME_NOTIFICATION, w.data());
}

function encodeRandomNumber(id, number) {
    const w = new ByteWriter();
    w.writeI64(id);
    w.writeI64(number);
    return encodeFrame(MSG_RANDOM_NUMBER, w.data());
}

function encodeHashResponse(id, hashHex) {
    const w = new ByteWriter();
    w.writeI64(id);
    w.writeString(hashHex);
    return encodeFrame(MSG_HASH_RESPONSE, w.data());
}

function encodeDisconnect(reason) {
    const w = new ByteWriter();
    w.writeString(reason);
    return encodeFrame(MSG_DISCONNECT, w.data());
}

function encodeError(code, message) {
    const w = new ByteWriter();
    w.writeI32(code);
    w.writeString(message);
    return encodeFrame(MSG_ERROR, w.data());
}

// ─── Message Decoder ────────────────────────────────────────────────────

function decodeMessage(data) {
    const { msgType, payload } = decodeFrame(data);
    const r = new ByteReader(payload);
    const msg = { type: msgType };

    switch (msgType) {
        case MSG_HELLO:
            msg.hello = { clientLanguage: r.readString() };
            break;
        case MSG_BONJOUR:
            msg.bonjour = { serverLanguage: r.readString() };
            break;
        case MSG_ECHO_REQUEST:
            msg.echoReq = { id: r.readI64(), meta: r.readString(), data: r.readString() };
            break;
        case MSG_ECHO_RESPONSE:
            msg.echoResp = { status: r.readI32(), results: [] };
            const count = r.readU32();
            if (count > Math.floor(r.remaining() / 13)) throw new Error(`result count ${count} exceeds remaining payload`);
            for (let i = 0; i < count; i++) {
                msg.echoResp.results.push({ idx: r.readI64(), type: r.readU8(), kv: r.readKV() });
            }
            break;
        case MSG_KISS_REQUEST:
            msg.kissReq = { osName: r.readString(), osVersion: r.readString(), osRelease: r.readString(), osArchitecture: r.readString() };
            break;
        case MSG_KISS_RESPONSE:
            msg.kissResp = { language: r.readString(), encoding: r.readString(), timeZone: r.readString() };
            break;
        case MSG_PING:
            msg.ping = { timestampMs: r.readI64() };
            break;
        case MSG_PONG:
            msg.pong = { timestampMs: r.readI64() };
            break;
        case MSG_TIME_NOTIFICATION:
            msg.timeNotif = { timestampMs: r.readI64(), iso8601: r.readString() };
            break;
        case MSG_RANDOM_NUMBER:
            msg.random = { id: r.readI64(), number: r.readI64() };
            break;
        case MSG_HASH_RESPONSE:
            msg.hash = { id: r.readI64(), hashHex: r.readString() };
            break;
        case MSG_DISCONNECT:
            msg.disconnect = { reason: r.readString() };
            break;
        case MSG_ERROR:
            msg.error = { code: r.readI32(), message: r.readString() };
            break;
        default:
            throw new Error(`unknown message type: 0x${msgType.toString(16)}`);
    }
    return msg;
}

// ─── Utility ─────────────────────────────────────────────────────────────

function nowMs() { return Date.now(); }

function nowISO() { return new Date().toISOString().replace(/\.\d+Z$/, 'Z'); }

function hashNumber(num) {
    const h = crypto.createHash('sha256').update(String(num)).digest('hex');
    return h.substring(0, 10);
}

function log(name, msg) {
    const ts = new Date().toISOString().replace('T', ' ').substring(0, 19);
    console.log(`[${ts}] [INFO] [${name}] ${msg}`);
}

module.exports = {
    PORT, MAGIC, VERSION, HEADER_LEN, SERVER_LANG, CLIENT_LANG,
    MSG_HELLO, MSG_BONJOUR, MSG_ECHO_REQUEST, MSG_ECHO_RESPONSE,
    MSG_KISS_REQUEST, MSG_KISS_RESPONSE, MSG_PING, MSG_PONG,
    MSG_TIME_NOTIFICATION, MSG_RANDOM_NUMBER, MSG_HASH_RESPONSE,
    MSG_DISCONNECT, MSG_ERROR,
    ERR_DECODE, ERR_UNKNOWN_MSG_TYPE, ERR_TRUNCATED_PAYLOAD,
    ERR_BAD_MAGIC, ERR_BAD_VERSION, ERR_SESSION_NOT_FOUND, ERR_INTERNAL,
    PING_INTERVAL, SESSION_TIMEOUT, TIME_INTERVAL, RANDOM_INTERVAL, KISS_INTERVAL,
    ByteWriter, ByteReader, encodeFrame, decodeFrame,
    encodeHello, encodeBonjour, encodeEchoRequest, encodeEchoResponse,
    encodeKissRequest, encodeKissResponse, encodePing, encodePong,
    encodeTimeNotification, encodeRandomNumber, encodeHashResponse,
    encodeDisconnect, encodeError, decodeMessage,
    nowMs, nowISO, hashNumber, log
};
