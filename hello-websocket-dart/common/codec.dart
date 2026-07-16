// Hello WebSocket Protocol Codec - Dart implementation.
// Implements the canonical binary protocol defined in PROTOCOL.md.

import 'dart:convert';
import 'dart:typed_data';
import 'package:crypto/crypto.dart';

// ─── Constants ───────────────────────────────────────────────────────────

const int port = 9898;
const int magic = 0x48;
const int version = 0x01;
const int headerLen = 8;
const String serverLang = 'DART';
const String clientLang = 'DART';

// Message types
const int msgHello = 0x01;
const int msgBonjour = 0x02;
const int msgEchoRequest = 0x03;
const int msgEchoResponse = 0x04;
const int msgKissRequest = 0x05;
const int msgKissResponse = 0x06;
const int msgPing = 0x07;
const int msgPong = 0x08;
const int msgTimeNotification = 0x09;
const int msgRandomNumber = 0x0A;
const int msgHashResponse = 0x0B;
const int msgDisconnect = 0x0C;
const int msgError = 0x7F;

// Error codes
const int errDecode = 0x01;
const int errUnknownMsgType = 0x02;
const int errTruncatedPayload = 0x03;
const int errBadMagic = 0x04;
const int errBadVersion = 0x05;
const int errSessionNotFound = 0x06;
const int errInternal = 0x07;

// Intervals (ms)
const int pingIntervalMs = 1000;
const int sessionTimeoutMs = 60000;
const int timeIntervalMs = 5000;
const int randomIntervalMs = 5000;
const int kissIntervalMs = 5000;

// ─── ByteWriter ─────────────────────────────────────────────────────────

class ByteWriter {
  final List<int> _buf = [];

  void writeU8(int v) => _buf.add(v & 0xFF);
  void writeU16(int v) {
    _buf.add((v >> 8) & 0xFF);
    _buf.add(v & 0xFF);
  }

  void writeU32(int v) {
    _buf.add((v >> 24) & 0xFF);
    _buf.add((v >> 16) & 0xFF);
    _buf.add((v >> 8) & 0xFF);
    _buf.add(v & 0xFF);
  }

  void writeI32(int v) => writeU32(v & 0xFFFFFFFF);
  void writeI64(int v) {
    writeU32((v >> 32) & 0xFFFFFFFF);
    writeU32(v & 0xFFFFFFFF);
  }

  void writeString(String s) {
    final b = utf8.encode(s);
    writeU32(b.length);
    _buf.addAll(b);
  }

  void writeKV(Map<String, String> m) {
    writeU32(m.length);
    m.forEach((k, v) {
      writeString(k);
      writeString(v);
    });
  }

  Uint8List toBytes() => Uint8List.fromList(_buf);
}

// ─── ByteReader ─────────────────────────────────────────────────────────

class ByteReader {
  final Uint8List _data;
  int _pos = 0;
  ByteReader(this._data);
  int get remaining => _data.length - _pos;

  int readU8() {
    if (_pos + 1 > _data.length)
      throw Exception('unexpected end of data reading u8');
    return _data[_pos++];
  }

  int readU16() {
    if (_pos + 2 > _data.length)
      throw Exception('unexpected end of data reading u16');
    final v = (_data[_pos] << 8) | _data[_pos + 1];
    _pos += 2;
    return v;
  }

  int readU32() {
    if (_pos + 4 > _data.length)
      throw Exception('unexpected end of data reading u32');
    final v = (_data[_pos] << 24) |
        (_data[_pos + 1] << 16) |
        (_data[_pos + 2] << 8) |
        _data[_pos + 3];
    _pos += 4;
    return v;
  }

  int readI32() => readU32().toSigned(32);

  int readI64() {
    if (_pos + 8 > _data.length)
      throw Exception('unexpected end of data reading i64');
    final hi = readU32();
    final lo = readU32();
    return ((hi << 32) | lo).toSigned(64);
  }

  String readString() {
    final ln = readU32();
    if (_pos + ln > _data.length)
      throw Exception('string length $ln exceeds remaining data');
    final s = utf8.decode(_data.sublist(_pos, _pos + ln));
    _pos += ln;
    return s;
  }

  Map<String, String> readKV() {
    final count = readU32();
    if (count > remaining ~/ 8)
      throw Exception('kv count $count exceeds remaining payload');
    final m = <String, String>{};
    for (int i = 0; i < count; i++) {
      m[readString()] = readString();
    }
    return m;
  }
}

// ─── Frame Codec ────────────────────────────────────────────────────────

Uint8List encodeFrame(int msgType, Uint8List payload) {
  final buf = Uint8List(headerLen + payload.length);
  buf[0] = magic;
  buf[1] = version;
  buf[2] = msgType;
  buf[3] = 0x00;
  buf[4] = (payload.length >> 24) & 0xFF;
  buf[5] = (payload.length >> 16) & 0xFF;
  buf[6] = (payload.length >> 8) & 0xFF;
  buf[7] = payload.length & 0xFF;
  buf.setRange(headerLen, headerLen + payload.length, payload);
  return buf;
}

class Frame {
  final int msgType;
  final Uint8List payload;
  Frame(this.msgType, this.payload);
}

Frame decodeFrame(Uint8List data) {
  if (data.length < headerLen)
    throw Exception('frame too short: ${data.length}');
  if (data[0] != magic)
    throw Exception('bad magic: 0x${data[0].toRadixString(16)}');
  if (data[1] != version)
    throw Exception('bad version: 0x${data[1].toRadixString(16)}');
  final msgType = data[2];
  final payloadLen =
      (data[4] << 24) | (data[5] << 16) | (data[6] << 8) | data[7];
  if (payloadLen != data.length - headerLen) {
    throw Exception(
        'payload length mismatch: declared $payloadLen, available ${data.length - headerLen}');
  }
  return Frame(msgType, data.sublist(headerLen, headerLen + payloadLen));
}

// ─── Message Types ──────────────────────────────────────────────────────

class EchoResult {
  final int idx;
  final int type;
  final Map<String, String> kv;
  EchoResult(this.idx, this.type, this.kv);
}

class Message {
  final int type;
  String? clientLanguage;
  String? serverLanguage;
  int? echoId;
  String? echoMeta;
  String? echoData;
  int? echoStatus;
  List<EchoResult>? echoResults;
  String? osName;
  String? osVersion;
  String? osRelease;
  String? osArch;
  String? kissLanguage;
  String? kissEncoding;
  String? kissTimeZone;
  int? timestampMs;
  String? iso8601;
  int? randomId;
  int? randomNumber;
  String? hashHex;
  String? disconnectReason;
  int? errorCode;
  String? errorMessage;

  Message(this.type);

  Uint8List encode() {
    final w = ByteWriter();
    switch (type) {
      case msgHello:
        w.writeString(clientLanguage!);
        return encodeFrame(msgHello, w.toBytes());
      case msgBonjour:
        w.writeString(serverLanguage!);
        return encodeFrame(msgBonjour, w.toBytes());
      case msgEchoRequest:
        w.writeI64(echoId!);
        w.writeString(echoMeta!);
        w.writeString(echoData!);
        return encodeFrame(msgEchoRequest, w.toBytes());
      case msgEchoResponse:
        w.writeI32(echoStatus!);
        w.writeU32(echoResults!.length);
        for (final r in echoResults!) {
          w.writeI64(r.idx);
          w.writeU8(r.type);
          w.writeKV(r.kv);
        }
        return encodeFrame(msgEchoResponse, w.toBytes());
      case msgKissRequest:
        w.writeString(osName!);
        w.writeString(osVersion!);
        w.writeString(osRelease!);
        w.writeString(osArch!);
        return encodeFrame(msgKissRequest, w.toBytes());
      case msgKissResponse:
        w.writeString(kissLanguage!);
        w.writeString(kissEncoding!);
        w.writeString(kissTimeZone!);
        return encodeFrame(msgKissResponse, w.toBytes());
      case msgPing:
        w.writeI64(timestampMs!);
        return encodeFrame(msgPing, w.toBytes());
      case msgPong:
        w.writeI64(timestampMs!);
        return encodeFrame(msgPong, w.toBytes());
      case msgTimeNotification:
        w.writeI64(timestampMs!);
        w.writeString(iso8601!);
        return encodeFrame(msgTimeNotification, w.toBytes());
      case msgRandomNumber:
        w.writeI64(randomId!);
        w.writeI64(randomNumber!);
        return encodeFrame(msgRandomNumber, w.toBytes());
      case msgHashResponse:
        w.writeI64(randomId!);
        w.writeString(hashHex!);
        return encodeFrame(msgHashResponse, w.toBytes());
      case msgDisconnect:
        w.writeString(disconnectReason!);
        return encodeFrame(msgDisconnect, w.toBytes());
      case msgError:
        w.writeI32(errorCode!);
        w.writeString(errorMessage!);
        return encodeFrame(msgError, w.toBytes());
      default:
        throw ArgumentError(
            'unknown message type: 0x${type.toRadixString(16)}');
    }
  }
}

Message decodeMessage(Uint8List data) {
  final frame = decodeFrame(data);
  final r = ByteReader(frame.payload);
  final m = Message(frame.msgType);
  switch (frame.msgType) {
    case msgHello:
      m.clientLanguage = r.readString();
      break;
    case msgBonjour:
      m.serverLanguage = r.readString();
      break;
    case msgEchoRequest:
      m.echoId = r.readI64();
      m.echoMeta = r.readString();
      m.echoData = r.readString();
      break;
    case msgEchoResponse:
      m.echoStatus = r.readI32();
      final count = r.readU32();
      if (count > r.remaining ~/ 13)
        throw Exception('result count $count exceeds remaining payload');
      final results = <EchoResult>[];
      for (int i = 0; i < count; i++)
        results.add(EchoResult(r.readI64(), r.readU8(), r.readKV()));
      m.echoResults = results;
      break;
    case msgKissRequest:
      m.osName = r.readString();
      m.osVersion = r.readString();
      m.osRelease = r.readString();
      m.osArch = r.readString();
      break;
    case msgKissResponse:
      m.kissLanguage = r.readString();
      m.kissEncoding = r.readString();
      m.kissTimeZone = r.readString();
      break;
    case msgPing:
      m.timestampMs = r.readI64();
      break;
    case msgPong:
      m.timestampMs = r.readI64();
      break;
    case msgTimeNotification:
      m.timestampMs = r.readI64();
      m.iso8601 = r.readString();
      break;
    case msgRandomNumber:
      m.randomId = r.readI64();
      m.randomNumber = r.readI64();
      break;
    case msgHashResponse:
      m.randomId = r.readI64();
      m.hashHex = r.readString();
      break;
    case msgDisconnect:
      m.disconnectReason = r.readString();
      break;
    case msgError:
      m.errorCode = r.readI32();
      m.errorMessage = r.readString();
      break;
    default:
      throw Exception(
          'unknown message type: 0x${frame.msgType.toRadixString(16)}');
  }
  return m;
}

// ─── Utility ────────────────────────────────────────────────────────────

int nowMs() => DateTime.now().millisecondsSinceEpoch;

String nowISO() =>
    DateTime.now().toUtc().toIso8601String().split('.').first + 'Z';

String hashNumber(int num) {
  final h = sha256.convert(num.toString().codeUnits);
  return h.toString().substring(0, 10);
}

void log(String name, String msg) {
  final ts = DateTime.now().toString().split('.').first;
  print('[$ts] [INFO] [$name] $msg');
}

// ─── Factory Helpers ─────────────────────────────────────────────────────

Message hello(String lang) => Message(msgHello)..clientLanguage = lang;
Message bonjour(String lang) => Message(msgBonjour)..serverLanguage = lang;
Message ping(int ts) => Message(msgPing)..timestampMs = ts;
Message pong(int ts) => Message(msgPong)..timestampMs = ts;
Message timeNotif(int ts, String iso) => Message(msgTimeNotification)
  ..timestampMs = ts
  ..iso8601 = iso;
Message kissRequest(String os, String ver, String rel, String arch) =>
    Message(msgKissRequest)
      ..osName = os
      ..osVersion = ver
      ..osRelease = rel
      ..osArch = arch;
Message kissResponse(String lang, String enc, String tz) =>
    Message(msgKissResponse)
      ..kissLanguage = lang
      ..kissEncoding = enc
      ..kissTimeZone = tz;
Message randomNumberMsg(int id, int num) => Message(msgRandomNumber)
  ..randomId = id
  ..randomNumber = num;
Message hashResponseMsg(int id, String hash) => Message(msgHashResponse)
  ..randomId = id
  ..hashHex = hash;
Message disconnectMsg(String reason) =>
    Message(msgDisconnect)..disconnectReason = reason;
Message errorMsg(int code, String msg) => Message(msgError)
  ..errorCode = code
  ..errorMessage = msg;
