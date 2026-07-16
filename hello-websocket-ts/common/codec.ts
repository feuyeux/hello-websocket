// Hello WebSocket Protocol Codec - TypeScript implementation.
// Implements the canonical binary protocol defined in PROTOCOL.md.

import { createHash } from 'crypto';

// ─── Constants ───────────────────────────────────────────────────────────

export const PORT = 9898;
export const MAGIC = 0x48;
export const VERSION = 0x01;
export const HEADER_LEN = 8;
export const SERVER_LANG = 'TYPESCRIPT';
export const CLIENT_LANG = 'TYPESCRIPT';

// Message types
export const MSG_HELLO = 0x01;
export const MSG_BONJOUR = 0x02;
export const MSG_ECHO_REQUEST = 0x03;
export const MSG_ECHO_RESPONSE = 0x04;
export const MSG_KISS_REQUEST = 0x05;
export const MSG_KISS_RESPONSE = 0x06;
export const MSG_PING = 0x07;
export const MSG_PONG = 0x08;
export const MSG_TIME_NOTIFICATION = 0x09;
export const MSG_RANDOM_NUMBER = 0x0A;
export const MSG_HASH_RESPONSE = 0x0B;
export const MSG_DISCONNECT = 0x0C;
export const MSG_ERROR = 0x7F;

// Error codes
export const ERR_DECODE = 0x01;
export const ERR_UNKNOWN_MSG_TYPE = 0x02;
export const ERR_TRUNCATED_PAYLOAD = 0x03;
export const ERR_BAD_MAGIC = 0x04;
export const ERR_BAD_VERSION = 0x05;
export const ERR_SESSION_NOT_FOUND = 0x06;
export const ERR_INTERNAL = 0x07;

// Intervals (ms)
export const PING_INTERVAL_MS = 1000;
export const SESSION_TIMEOUT_MS = 60000;
export const TIME_INTERVAL_MS = 5000;
export const RANDOM_INTERVAL_MS = 5000;
export const KISS_INTERVAL_MS = 5000;

// ─── Types ──────────────────────────────────────────────────────────────

export interface EchoResult {
  idx: bigint;
  type: number;
  kv: Record<string, string>;
}

export type Message =
  | { type: typeof MSG_HELLO; clientLanguage: string }
  | { type: typeof MSG_BONJOUR; serverLanguage: string }
  | { type: typeof MSG_ECHO_REQUEST; echoId: bigint; echoMeta: string; echoData: string }
  | { type: typeof MSG_ECHO_RESPONSE; echoStatus: number; echoResults: EchoResult[] }
  | { type: typeof MSG_KISS_REQUEST; osName: string; osVersion: string; osRelease: string; osArch: string }
  | { type: typeof MSG_KISS_RESPONSE; kissLanguage: string; kissEncoding: string; kissTimeZone: string }
  | { type: typeof MSG_PING; timestampMs: bigint }
  | { type: typeof MSG_PONG; timestampMs: bigint }
  | { type: typeof MSG_TIME_NOTIFICATION; timestampMs: bigint; iso8601: string }
  | { type: typeof MSG_RANDOM_NUMBER; randomId: bigint; randomNumber: bigint }
  | { type: typeof MSG_HASH_RESPONSE; randomId: bigint; hashHex: string }
  | { type: typeof MSG_DISCONNECT; disconnectReason: string }
  | { type: typeof MSG_ERROR; errorCode: number; errorMessage: string };

// ─── ByteWriter ─────────────────────────────────────────────────────────

export class ByteWriter {
  private buf: number[] = [];

  writeU8(v: number): void { this.buf.push(v & 0xFF); }

  writeU16(v: number): void { this.buf.push((v >> 8) & 0xFF, v & 0xFF); }

  writeU32(v: number): void {
    this.buf.push((v >>> 24) & 0xFF, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF);
  }

  writeI32(v: number): void { this.writeU32(v >>> 0); }

  writeI64(v: bigint): void {
    const lo = Number(v & 0xFFFFFFFFn);
    const hi = Number((v >> 32n) & 0xFFFFFFFFn);
    this.writeU32(hi);
    this.writeU32(lo);
  }

  writeString(s: string): void {
    const b = Buffer.from(s, 'utf-8');
    this.writeU32(b.length);
    for (const byte of b) this.buf.push(byte);
  }

  writeKV(m: Record<string, string>): void {
    const keys = Object.keys(m);
    this.writeU32(keys.length);
    for (const k of keys) { this.writeString(k); this.writeString(m[k]); }
  }

  toBuffer(): Buffer { return Buffer.from(this.buf); }
}

// ─── ByteReader ────────────────────────────────────────────────────────

export class ByteReader {
  private data: Buffer;
  private pos: number = 0;

  constructor(data: Buffer) { this.data = data; }

  remaining(): number { return this.data.length - this.pos; }
  private ensure(size: number, field: string): void {
    if (!Number.isSafeInteger(size) || size < 0 || size > this.remaining()) {
      throw new Error(`${field} requires ${size} bytes, only ${this.remaining()} remain`);
    }
  }

  readU8(): number { this.ensure(1, 'u8'); return this.data[this.pos++]; }

  readU16(): number {
    this.ensure(2, 'u16');
    const v = (this.data[this.pos] << 8) | this.data[this.pos + 1];
    this.pos += 2;
    return v;
  }

  readU32(): number {
    this.ensure(4, 'u32');
    const v = (this.data[this.pos] * 0x1000000) + (this.data[this.pos + 1] << 16) +
              (this.data[this.pos + 2] << 8) + this.data[this.pos + 3];
    this.pos += 4;
    return v;
  }

  readI32(): number { return this.readU32() | 0; }

  readI64(): bigint {
    const hi = BigInt(this.readU32());
    const lo = BigInt(this.readU32());
    return BigInt.asIntN(64, (hi << 32n) | lo);
  }

  readString(): string {
    const ln = this.readU32();
    this.ensure(ln, 'string');
    const s = this.data.toString('utf-8', this.pos, this.pos + ln);
    this.pos += ln;
    return s;
  }

  readKV(): Record<string, string> {
    const count = this.readU32();
    if (count > Math.floor(this.remaining() / 8)) throw new Error(`kv count ${count} exceeds remaining payload`);
    const m: Record<string, string> = {};
    for (let i = 0; i < count; i++) {
      const k = this.readString();
      const v = this.readString();
      m[k] = v;
    }
    return m;
  }
}

// ─── Frame Codec ────────────────────────────────────────────────────────

export function encodeFrame(msgType: number, payload: Buffer): Buffer {
  const header = Buffer.alloc(HEADER_LEN);
  header[0] = MAGIC;
  header[1] = VERSION;
  header[2] = msgType;
  header[3] = 0x00;
  header.writeUInt32BE(payload.length, 4);
  return Buffer.concat([header, payload]);
}

export function decodeFrame(data: Buffer): { msgType: number; payload: Buffer } {
  if (data.length < HEADER_LEN) throw new Error(`frame too short: ${data.length}`);
  if (data[0] !== MAGIC) throw new Error(`bad magic: 0x${data[0].toString(16)}`);
  if (data[1] !== VERSION) throw new Error(`bad version: 0x${data[1].toString(16)}`);
  const msgType = data[2];
  const payloadLen = data.readUInt32BE(4);
  if (payloadLen !== data.length - HEADER_LEN) {
    throw new Error(`payload length mismatch: declared ${payloadLen}, available ${data.length - HEADER_LEN}`);
  }
  return { msgType, payload: data.subarray(HEADER_LEN, HEADER_LEN + payloadLen) };
}

// ─── Message Encoders ───────────────────────────────────────────────────

export function encodeMessage(msg: Message): Buffer {
  const w = new ByteWriter();
  switch (msg.type) {
    case MSG_HELLO: w.writeString(msg.clientLanguage); return encodeFrame(MSG_HELLO, w.toBuffer());
    case MSG_BONJOUR: w.writeString(msg.serverLanguage); return encodeFrame(MSG_BONJOUR, w.toBuffer());
    case MSG_ECHO_REQUEST: w.writeI64(msg.echoId); w.writeString(msg.echoMeta); w.writeString(msg.echoData); return encodeFrame(MSG_ECHO_REQUEST, w.toBuffer());
    case MSG_ECHO_RESPONSE:
      w.writeI32(msg.echoStatus); w.writeU32(msg.echoResults.length);
      for (const r of msg.echoResults) { w.writeI64(r.idx); w.writeU8(r.type); w.writeKV(r.kv); }
      return encodeFrame(MSG_ECHO_RESPONSE, w.toBuffer());
    case MSG_KISS_REQUEST: w.writeString(msg.osName); w.writeString(msg.osVersion); w.writeString(msg.osRelease); w.writeString(msg.osArch); return encodeFrame(MSG_KISS_REQUEST, w.toBuffer());
    case MSG_KISS_RESPONSE: w.writeString(msg.kissLanguage); w.writeString(msg.kissEncoding); w.writeString(msg.kissTimeZone); return encodeFrame(MSG_KISS_RESPONSE, w.toBuffer());
    case MSG_PING: w.writeI64(msg.timestampMs); return encodeFrame(MSG_PING, w.toBuffer());
    case MSG_PONG: w.writeI64(msg.timestampMs); return encodeFrame(MSG_PONG, w.toBuffer());
    case MSG_TIME_NOTIFICATION: w.writeI64(msg.timestampMs); w.writeString(msg.iso8601); return encodeFrame(MSG_TIME_NOTIFICATION, w.toBuffer());
    case MSG_RANDOM_NUMBER: w.writeI64(msg.randomId); w.writeI64(msg.randomNumber); return encodeFrame(MSG_RANDOM_NUMBER, w.toBuffer());
    case MSG_HASH_RESPONSE: w.writeI64(msg.randomId); w.writeString(msg.hashHex); return encodeFrame(MSG_HASH_RESPONSE, w.toBuffer());
    case MSG_DISCONNECT: w.writeString(msg.disconnectReason); return encodeFrame(MSG_DISCONNECT, w.toBuffer());
    case MSG_ERROR: w.writeI32(msg.errorCode); w.writeString(msg.errorMessage); return encodeFrame(MSG_ERROR, w.toBuffer());
  }
}

// ─── Message Decoder ────────────────────────────────────────────────────

export function decodeMessage(data: Buffer): Message {
  const { msgType, payload } = decodeFrame(data);
  const r = new ByteReader(payload);
  switch (msgType) {
    case MSG_HELLO: return { type: MSG_HELLO, clientLanguage: r.readString() };
    case MSG_BONJOUR: return { type: MSG_BONJOUR, serverLanguage: r.readString() };
    case MSG_ECHO_REQUEST: return { type: MSG_ECHO_REQUEST, echoId: r.readI64(), echoMeta: r.readString(), echoData: r.readString() };
    case MSG_ECHO_RESPONSE: {
      const status = r.readI32();
      const count = r.readU32();
      if (count > Math.floor(r.remaining() / 13)) throw new Error(`result count ${count} exceeds remaining payload`);
      const results: EchoResult[] = [];
      for (let i = 0; i < count; i++) results.push({ idx: r.readI64(), type: r.readU8(), kv: r.readKV() });
      return { type: MSG_ECHO_RESPONSE, echoStatus: status, echoResults: results };
    }
    case MSG_KISS_REQUEST: return { type: MSG_KISS_REQUEST, osName: r.readString(), osVersion: r.readString(), osRelease: r.readString(), osArch: r.readString() };
    case MSG_KISS_RESPONSE: return { type: MSG_KISS_RESPONSE, kissLanguage: r.readString(), kissEncoding: r.readString(), kissTimeZone: r.readString() };
    case MSG_PING: return { type: MSG_PING, timestampMs: r.readI64() };
    case MSG_PONG: return { type: MSG_PONG, timestampMs: r.readI64() };
    case MSG_TIME_NOTIFICATION: return { type: MSG_TIME_NOTIFICATION, timestampMs: r.readI64(), iso8601: r.readString() };
    case MSG_RANDOM_NUMBER: return { type: MSG_RANDOM_NUMBER, randomId: r.readI64(), randomNumber: r.readI64() };
    case MSG_HASH_RESPONSE: return { type: MSG_HASH_RESPONSE, randomId: r.readI64(), hashHex: r.readString() };
    case MSG_DISCONNECT: return { type: MSG_DISCONNECT, disconnectReason: r.readString() };
    case MSG_ERROR: return { type: MSG_ERROR, errorCode: r.readI32(), errorMessage: r.readString() };
    default: throw new Error(`unknown message type: 0x${msgType.toString(16)}`);
  }
}

// ─── Utility ────────────────────────────────────────────────────────────

export function nowMs(): bigint { return BigInt(Date.now()); }

export function nowISO(): string { return new Date().toISOString().replace(/\.\d+Z$/, 'Z'); }

export function hashNumber(num: bigint): string {
  const h = createHash('sha256').update(num.toString()).digest('hex');
  return h.substring(0, 10);
}

export function log(name: string, msg: string): void {
  const ts = new Date().toISOString().replace('T', ' ').substring(0, 19);
  console.log(`[${ts}] [INFO] [${name}] ${msg}`);
}

// ─── Message Factory Helpers ─────────────────────────────────────────────

export const hello = (clientLanguage: string): Message => ({ type: MSG_HELLO, clientLanguage });
export const bonjour = (serverLanguage: string): Message => ({ type: MSG_BONJOUR, serverLanguage });
export const ping = (timestampMs: bigint): Message => ({ type: MSG_PING, timestampMs });
export const pong = (timestampMs: bigint): Message => ({ type: MSG_PONG, timestampMs });
export const timeNotif = (timestampMs: bigint, iso8601: string): Message => ({ type: MSG_TIME_NOTIFICATION, timestampMs, iso8601 });
export const kissRequest = (osName: string, osVersion: string, osRelease: string, osArch: string): Message => ({ type: MSG_KISS_REQUEST, osName, osVersion, osRelease, osArch });
export const kissResponse = (kissLanguage: string, kissEncoding: string, kissTimeZone: string): Message => ({ type: MSG_KISS_RESPONSE, kissLanguage, kissEncoding, kissTimeZone });
export const randomNumber = (randomId: bigint, randomNumber: bigint): Message => ({ type: MSG_RANDOM_NUMBER, randomId, randomNumber });
export const hashResponse = (randomId: bigint, hashHex: string): Message => ({ type: MSG_HASH_RESPONSE, randomId, hashHex });
export const disconnect = (disconnectReason: string): Message => ({ type: MSG_DISCONNECT, disconnectReason });
export const error = (errorCode: number, errorMessage: string): Message => ({ type: MSG_ERROR, errorCode, errorMessage });
