'use strict';

const assert = require('assert');
const C = require('./codec.js');

let passed = 0, failed = 0;

function test(name, fn) {
    try { fn(); console.log(`  PASS: ${name}`); passed++; }
    catch (e) { console.log(`  FAIL: ${name}: ${e.message}`); failed++; }
}

// Worked example from PROTOCOL.md section 9
test('helloWorkedExample', () => {
    const data = C.encodeHello('Go');
    const expected = Buffer.from([0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x02, 0x47, 0x6F]);
    assert.deepStrictEqual(Array.from(data), Array.from(expected));
});

test('roundTripHello', () => {
    const msg = C.decodeMessage(C.encodeHello('Rust'));
    assert.strictEqual(msg.type, C.MSG_HELLO);
    assert.strictEqual(msg.hello.clientLanguage, 'Rust');
});

test('roundTripBonjour', () => {
    const msg = C.decodeMessage(C.encodeBonjour('Java'));
    assert.strictEqual(msg.bonjour.serverLanguage, 'Java');
});

test('roundTripEchoRequest', () => {
    const msg = C.decodeMessage(C.encodeEchoRequest(42, 'Python', 'hello'));
    assert.strictEqual(msg.echoReq.id, 42);
    assert.strictEqual(msg.echoReq.meta, 'Python');
    assert.strictEqual(msg.echoReq.data, 'hello');
});

test('roundTripEchoResponse', () => {
    const msg = C.decodeMessage(C.encodeEchoResponse(200, [{ idx: 123, type: 0, kv: { id: '1', data: 'Hello' } }]));
    assert.strictEqual(msg.echoResp.status, 200);
    assert.strictEqual(msg.echoResp.results.length, 1);
    assert.strictEqual(msg.echoResp.results[0].kv.id, '1');
});

test('roundTripKiss', () => {
    const msg = C.decodeMessage(C.encodeKissRequest('Linux', '6.6', 'arch', 'AMD64'));
    assert.strictEqual(msg.kissReq.osName, 'Linux');
    assert.strictEqual(msg.kissReq.osArchitecture, 'AMD64');
});

test('roundTripPingPong', () => {
    const msg = C.decodeMessage(C.encodePing(1700000000000));
    assert.strictEqual(msg.ping.timestampMs, 1700000000000);
    const msg2 = C.decodeMessage(C.encodePong(1700000000001));
    assert.strictEqual(msg2.pong.timestampMs, 1700000000001);
});

test('roundTripTimeNotification', () => {
    const msg = C.decodeMessage(C.encodeTimeNotification(1700000000000, '2023-11-14T22:13:20Z'));
    assert.strictEqual(msg.timeNotif.iso8601, '2023-11-14T22:13:20Z');
});

test('roundTripRandomHash', () => {
    const msg = C.decodeMessage(C.encodeRandomNumber(99, 42));
    assert.strictEqual(msg.random.id, 99);
    assert.strictEqual(msg.random.number, 42);
    const msg2 = C.decodeMessage(C.encodeHashResponse(99, '7688b6ef5a'));
    assert.strictEqual(msg2.hash.hashHex, '7688b6ef5a');
});

test('roundTripDisconnect', () => {
    const msg = C.decodeMessage(C.encodeDisconnect('bye'));
    assert.strictEqual(msg.disconnect.reason, 'bye');
});

test('roundTripError', () => {
    const msg = C.decodeMessage(C.encodeError(C.ERR_UNKNOWN_MSG_TYPE, 'bad type'));
    assert.strictEqual(msg.error.code, C.ERR_UNKNOWN_MSG_TYPE);
    assert.strictEqual(msg.error.message, 'bad type');
});

test('badMagic', () => {
    assert.throws(() => C.decodeFrame(Buffer.from([0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00])));
});

test('badVersion', () => {
    assert.throws(() => C.decodeFrame(Buffer.from([0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00])));
});

test('truncatedPayload', () => {
    assert.throws(() => C.decodeFrame(Buffer.from([0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF])));
});

test('hashNumber', () => {
    const h = C.hashNumber(42);
    assert.strictEqual(h.length, 10);
    assert.strictEqual(h, C.hashNumber(42));
});

console.log(`\n${passed} passed, ${failed} failed, ${passed + failed} total`);
process.exit(failed === 0 ? 0 : 1);
