<?php
/**
 * Hello WebSocket Protocol Codec Tests - PHP standalone runner.
 * 16 tests matching all other language implementations.
 */

require __DIR__ . '/../vendor/autoload.php';

use HelloWs\Common\ByteWriter;
use HelloWs\Common\ByteReader;
use HelloWs\Common\Message;
use function HelloWs\Common\{encode_frame, decode_frame, decode_message, hash_number};
use const HelloWs\Common\{
    MAGIC, VERSION, HEADER_LEN,
    MSG_HELLO, MSG_BONJOUR, MSG_ECHO_REQUEST, MSG_ECHO_RESPONSE,
    MSG_KISS_REQUEST, MSG_KISS_RESPONSE, MSG_PING, MSG_PONG,
    MSG_TIME_NOTIFICATION, MSG_RANDOM_NUMBER, MSG_HASH_RESPONSE,
    MSG_DISCONNECT, MSG_ERROR
};

$passed = 0;
$failed = 0;

function assert_eq($expected, $actual, string $msg): void
{
    global $passed, $failed;
    if ($expected === $actual) {
        $passed++;
    } else {
        $failed++;
        echo "FAIL: $msg (expected: " . var_export($expected, true) . ", got: " . var_export($actual, true) . ")\n";
    }
}

function assert_throws(callable $fn, string $exceptionClass, string $msg): void
{
    global $passed, $failed;
    try {
        $fn();
        $failed++;
        echo "FAIL: $msg (expected exception $exceptionClass, none thrown)\n";
    } catch (\Throwable $e) {
        if ($e instanceof $exceptionClass) {
            $passed++;
        } else {
            $failed++;
            echo "FAIL: $msg (expected $exceptionClass, got " . get_class($e) . ": " . $e->getMessage() . ")\n";
        }
    }
}

// ─── ByteWriter Tests ──────────────────────────────────

$w = new ByteWriter();
$w->writeU8(0x48);
assert_eq("\x48", $w->data(), "writeU8");

$w = new ByteWriter();
$w->writeU16(0x0102);
assert_eq("\x01\x02", $w->data(), "writeU16");

$w = new ByteWriter();
$w->writeU32(0x01020304);
assert_eq("\x01\x02\x03\x04", $w->data(), "writeU32");

$w = new ByteWriter();
$w->writeI64(0x0102030405060708);
assert_eq("\x01\x02\x03\x04\x05\x06\x07\x08", $w->data(), "writeI64");

$w = new ByteWriter();
$w->writeString('Go');
assert_eq("\x00\x00\x00\x02Go", $w->data(), "writeString");

$w = new ByteWriter();
$w->writeKv(['k1' => 'v1']);
assert_eq("\x00\x00\x00\x01\x00\x00\x00\x02k1\x00\x00\x00\x02v1", $w->data(), "writeKv");

// ─── ByteReader Tests ──────────────────────────────────

$r = new ByteReader("\x48");
assert_eq(0x48, $r->readU8(), "readU8");

$r = new ByteReader("\x01\x02\x03\x04");
assert_eq(0x01020304, $r->readU32(), "readU32");

$r = new ByteReader("\x01\x02\x03\x04\x05\x06\x07\x08");
assert_eq(0x0102030405060708, $r->readI64(), "readI64");

$r = new ByteReader("\x00\x00\x00\x02Go");
assert_eq('Go', $r->readString(), "readString");

$r = new ByteReader("\x00\x00\x00\x01\x00\x00\x00\x02k1\x00\x00\x00\x02v1");
assert_eq(['k1' => 'v1'], $r->readKv(), "readKv");

// ─── Frame Codec Tests ────────────────────────────────

$frame = encode_frame(MSG_HELLO, "\x00\x00\x00\x02Go");
assert_eq(14, strlen($frame), "encodeFrame length");
assert_eq(chr(MAGIC), $frame[0], "encodeFrame magic");
assert_eq(chr(VERSION), $frame[1], "encodeFrame version");
assert_eq(chr(MSG_HELLO), $frame[2], "encodeFrame msgType");
assert_eq("\x00", $frame[3], "encodeFrame flags");
assert_eq("\x00\x00\x00\x06", substr($frame, 4, 4), "encodeFrame payloadLen");

[$msgType, $payload] = decode_frame($frame);
assert_eq(MSG_HELLO, $msgType, "decodeFrame msgType");
assert_eq("\x00\x00\x00\x02Go", $payload, "decodeFrame payload");

assert_throws(
    fn() => decode_frame("\x00" . chr(VERSION) . chr(MSG_HELLO) . "\x00\x00\x00\x00\x00\x00\x00\x00"),
    \InvalidArgumentException::class,
    "decodeFrame badMagic"
);

assert_throws(
    fn() => decode_frame(chr(MAGIC) . "\x02" . chr(MSG_HELLO) . "\x00\x00\x00\x00\x00\x00\x00\x00"),
    \InvalidArgumentException::class,
    "decodeFrame badVersion"
);

assert_throws(
    fn() => decode_frame(chr(MAGIC) . chr(VERSION) . chr(MSG_HELLO) . "\x00\x00\x00\x00\x10short"),
    \InvalidArgumentException::class,
    "decodeFrame truncated"
);

// ─── Message Codec Tests ──────────────────────────────

$msg = new Message(MSG_HELLO);
$msg->clientLanguage = 'PHP';
$encoded = $msg->encode();
$decoded = decode_message($encoded);
assert_eq(MSG_HELLO, $decoded->type, "hello encode/decode type");
assert_eq('PHP', $decoded->clientLanguage, "hello encode/decode language");

$hash = hash_number(42);
assert_eq(10, strlen($hash), "hashNumber length");
assert_eq(true, (bool)preg_match('/^[0-9a-f]{10}$/', $hash), "hashNumber format");

// ─── Summary ──────────────────────────────────────────

$total = $passed + $failed;
echo "\n";
echo str_repeat("=", 40) . "\n";
echo "Tests: $total, Passed: $passed, Failed: $failed\n";
echo str_repeat("=", 40) . "\n";

exit($failed > 0 ? 1 : 0);
