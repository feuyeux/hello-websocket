import { describe, it, expect } from 'vitest';
import {
  ByteWriter, ByteReader, encodeFrame, decodeFrame,
  encodeMessage, decodeMessage, hashNumber,
  MSG_HELLO, MSG_BONJOUR, MSG_ECHO_REQUEST, MSG_ECHO_RESPONSE,
  MSG_KISS_REQUEST, MSG_KISS_RESPONSE, MSG_PING, MSG_PONG,
  MSG_TIME_NOTIFICATION, MSG_RANDOM_NUMBER, MSG_HASH_RESPONSE,
  MSG_DISCONNECT, MSG_ERROR,
  ERR_UNKNOWN_MSG_TYPE, ERR_DECODE,
  type Message, type EchoResult,
} from './codec.js';

describe('Codec', () => {
  it('HELLO worked example byte-level match', () => {
    const msg: Message = { type: MSG_HELLO, clientLanguage: 'Go' };
    const data = encodeMessage(msg);
    const expected = Buffer.from([
      0x48, 0x01, 0x01, 0x00,
      0x00, 0x00, 0x00, 0x06,
      0x00, 0x00, 0x00, 0x02,
      0x47, 0x6F,
    ]);
    expect(data.equals(expected)).toBe(true);
  });

  it('Round-trip all simple message types', () => {
    const messages: Message[] = [
      { type: MSG_HELLO, clientLanguage: 'TypeScript' },
      { type: MSG_BONJOUR, serverLanguage: 'Java' },
      { type: MSG_ECHO_REQUEST, echoId: 42n, echoMeta: 'Python', echoData: 'hello' },
      { type: MSG_PING, timestampMs: 1700000000000n },
      { type: MSG_PONG, timestampMs: 1700000000001n },
      { type: MSG_TIME_NOTIFICATION, timestampMs: 1700000000000n, iso8601: '2023-11-14T22:13:20Z' },
      { type: MSG_RANDOM_NUMBER, randomId: 99n, randomNumber: 42n },
      { type: MSG_HASH_RESPONSE, randomId: 99n, hashHex: '7688b6ef5a' },
      { type: MSG_DISCONNECT, disconnectReason: 'bye' },
      { type: MSG_ERROR, errorCode: ERR_UNKNOWN_MSG_TYPE, errorMessage: 'bad type' },
    ];
    for (const orig of messages) {
      const decoded = decodeMessage(encodeMessage(orig));
      expect(decoded.type).toBe(orig.type);
    }
  });

  it('Round-trip EchoResponse', () => {
    const orig: Message = {
      type: MSG_ECHO_RESPONSE,
      echoStatus: 200,
      echoResults: [{ idx: 123n, type: 0, kv: { id: '1', data: 'Hello' } }],
    };
    const decoded = decodeMessage(encodeMessage(orig));
    expect(decoded.type).toBe(MSG_ECHO_RESPONSE);
    if (decoded.type === MSG_ECHO_RESPONSE) {
      expect(decoded.echoStatus).toBe(200);
      expect(decoded.echoResults.length).toBe(1);
      expect(decoded.echoResults[0].kv['id']).toBe('1');
    }
  });

  it('Round-trip Kiss', () => {
    const orig: Message = {
      type: MSG_KISS_REQUEST,
      osName: 'Linux', osVersion: '6.6', osRelease: 'arch', osArch: 'AMD64',
    };
    const decoded = decodeMessage(encodeMessage(orig));
    expect(decoded.type).toBe(MSG_KISS_REQUEST);
    if (decoded.type === MSG_KISS_REQUEST) {
      expect(decoded.osName).toBe('Linux');
      expect(decoded.osArch).toBe('AMD64');
    }
  });

  it('Bad magic rejected', () => {
    const data = Buffer.from([0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00]);
    expect(() => decodeFrame(data)).toThrow();
  });

  it('Bad version rejected', () => {
    const data = Buffer.from([0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00]);
    expect(() => decodeFrame(data)).toThrow();
  });

  it('Truncated payload rejected', () => {
    const data = Buffer.from([0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF]);
    expect(() => decodeFrame(data)).toThrow();
  });

  it('Hash number produces 10-char hex', () => {
    const h = hashNumber(42n);
    expect(h.length).toBe(10);
    expect(h).toBe(hashNumber(42n));
  });

  it('Round-trip KissResponse', () => {
    const orig: Message = {
      type: MSG_KISS_RESPONSE,
      kissLanguage: 'en_US', kissEncoding: 'UTF-8', kissTimeZone: 'UTC',
    };
    const decoded = decodeMessage(encodeMessage(orig));
    expect(decoded.type).toBe(MSG_KISS_RESPONSE);
    if (decoded.type === MSG_KISS_RESPONSE) {
      expect(decoded.kissLanguage).toBe('en_US');
      expect(decoded.kissEncoding).toBe('UTF-8');
      expect(decoded.kissTimeZone).toBe('UTC');
    }
  });

  it('Round-trip Disconnect', () => {
    const orig: Message = { type: MSG_DISCONNECT, disconnectReason: 'test reason' };
    const decoded = decodeMessage(encodeMessage(orig));
    expect(decoded.type).toBe(MSG_DISCONNECT);
    if (decoded.type === MSG_DISCONNECT) {
      expect(decoded.disconnectReason).toBe('test reason');
    }
  });

  it('Round-trip Error', () => {
    const orig: Message = { type: MSG_ERROR, errorCode: ERR_DECODE, errorMessage: 'decode failed' };
    const decoded = decodeMessage(encodeMessage(orig));
    expect(decoded.type).toBe(MSG_ERROR);
    if (decoded.type === MSG_ERROR) {
      expect(decoded.errorCode).toBe(ERR_DECODE);
      expect(decoded.errorMessage).toBe('decode failed');
    }
  });

  it('Round-trip RandomNumber', () => {
    const orig: Message = { type: MSG_RANDOM_NUMBER, randomId: 5n, randomNumber: 99999n };
    const decoded = decodeMessage(encodeMessage(orig));
    expect(decoded.type).toBe(MSG_RANDOM_NUMBER);
    if (decoded.type === MSG_RANDOM_NUMBER) {
      expect(decoded.randomId).toBe(5n);
      expect(decoded.randomNumber).toBe(99999n);
    }
  });

  it('Round-trip HashResponse', () => {
    const orig: Message = { type: MSG_HASH_RESPONSE, randomId: 7n, hashHex: 'abcdef1234' };
    const decoded = decodeMessage(encodeMessage(orig));
    expect(decoded.type).toBe(MSG_HASH_RESPONSE);
    if (decoded.type === MSG_HASH_RESPONSE) {
      expect(decoded.randomId).toBe(7n);
      expect(decoded.hashHex).toBe('abcdef1234');
    }
  });

  it('Empty string round-trip', () => {
    const orig: Message = { type: MSG_DISCONNECT, disconnectReason: '' };
    const decoded = decodeMessage(encodeMessage(orig));
    if (decoded.type === MSG_DISCONNECT) {
      expect(decoded.disconnectReason).toBe('');
    }
  });

  it('Unknown message type rejected', () => {
    const data = Buffer.from([0x48, 0x01, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00]);
    expect(() => decodeMessage(data)).toThrow();
  });

  it('Empty EchoResponse results array', () => {
    const orig: Message = { type: MSG_ECHO_RESPONSE, echoStatus: 204, echoResults: [] };
    const decoded = decodeMessage(encodeMessage(orig));
    if (decoded.type === MSG_ECHO_RESPONSE) {
      expect(decoded.echoStatus).toBe(204);
      expect(decoded.echoResults.length).toBe(0);
    }
  });
});
