<?php
/**
 * Hello WebSocket Protocol Codec Tests - PHP implementation.
 * 16 tests matching all other language implementations.
 */

use PHPUnit\Framework\TestCase;
use HelloWs\Common\{ByteWriter, ByteReader, Message};
use function HelloWs\Common\{encode_frame, decode_frame, decode_message, hash_number};
use const HelloWs\Common\{
    MAGIC, VERSION, HEADER_LEN,
    MSG_HELLO, MSG_BONJOUR, MSG_ECHO_REQUEST, MSG_ECHO_RESPONSE,
    MSG_KISS_REQUEST, MSG_KISS_RESPONSE, MSG_PING, MSG_PONG,
    MSG_TIME_NOTIFICATION, MSG_RANDOM_NUMBER, MSG_HASH_RESPONSE,
    MSG_DISCONNECT, MSG_ERROR
};

class CodecTest extends TestCase
{
    // ─── ByteWriter Tests ──────────────────────────────────

    public function testWriteU8(): void
    {
        $w = new ByteWriter();
        $w->writeU8(0x48);
        $this->assertSame("\x48", $w->data());
    }

    public function testWriteU16(): void
    {
        $w = new ByteWriter();
        $w->writeU16(0x0102);
        $this->assertSame("\x01\x02", $w->data());
    }

    public function testWriteU32(): void
    {
        $w = new ByteWriter();
        $w->writeU32(0x01020304);
        $this->assertSame("\x01\x02\x03\x04", $w->data());
    }

    public function testWriteI64(): void
    {
        $w = new ByteWriter();
        $w->writeI64(0x0102030405060708);
        $this->assertSame("\x01\x02\x03\x04\x05\x06\x07\x08", $w->data());
    }

    public function testWriteString(): void
    {
        $w = new ByteWriter();
        $w->writeString('Go');
        // u32 length (2) + "Go"
        $this->assertSame("\x00\x00\x00\x02Go", $w->data());
    }

    public function testWriteKv(): void
    {
        $w = new ByteWriter();
        $w->writeKv(['k1' => 'v1']);
        // u32 count(1) + u32 len(2) + "k1" + u32 len(2) + "v1"
        $this->assertSame("\x00\x00\x00\x01\x00\x00\x00\x02k1\x00\x00\x00\x02v1", $w->data());
    }

    // ─── ByteReader Tests ──────────────────────────────────

    public function testReadU8(): void
    {
        $r = new ByteReader("\x48");
        $this->assertSame(0x48, $r->readU8());
    }

    public function testReadU32(): void
    {
        $r = new ByteReader("\x01\x02\x03\x04");
        $this->assertSame(0x01020304, $r->readU32());
    }

    public function testReadI64(): void
    {
        $r = new ByteReader("\x01\x02\x03\x04\x05\x06\x07\x08");
        $this->assertSame(0x0102030405060708, $r->readI64());
    }

    public function testReadString(): void
    {
        $r = new ByteReader("\x00\x00\x00\x02Go");
        $this->assertSame('Go', $r->readString());
    }

    public function testReadKv(): void
    {
        $r = new ByteReader("\x00\x00\x00\x01\x00\x00\x00\x02k1\x00\x00\x00\x02v1");
        $this->assertSame(['k1' => 'v1'], $r->readKv());
    }

    // ─── Frame Codec Tests ────────────────────────────────

    public function testEncodeFrame(): void
    {
        $frame = encode_frame(MSG_HELLO, "\x00\x00\x00\x02Go");
        $this->assertSame(14, strlen($frame));
        $this->assertSame(chr(MAGIC), $frame[0]);
        $this->assertSame(chr(VERSION), $frame[1]);
        $this->assertSame(chr(MSG_HELLO), $frame[2]);
        $this->assertSame("\x00", $frame[3]);
        $this->assertSame("\x00\x00\x00\x06", substr($frame, 4, 4));
    }

    public function testDecodeFrame(): void
    {
        $frame = encode_frame(MSG_HELLO, "\x00\x00\x00\x02Go");
        [$msgType, $payload] = decode_frame($frame);
        $this->assertSame(MSG_HELLO, $msgType);
        $this->assertSame("\x00\x00\x00\x02Go", $payload);
    }

    public function testDecodeFrameBadMagic(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        $bad = "\x00" . chr(VERSION) . chr(MSG_HELLO) . "\x00\x00\x00\x00\x00\x00\x00\x00";
        decode_frame($bad);
    }

    public function testDecodeFrameBadVersion(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        $bad = chr(MAGIC) . "\x02" . chr(MSG_HELLO) . "\x00\x00\x00\x00\x00\x00\x00\x00";
        decode_frame($bad);
    }

    public function testDecodeFrameTruncated(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        $bad = chr(MAGIC) . chr(VERSION) . chr(MSG_HELLO) . "\x00" . "\x00\x00\x00\x10" . "short";
        decode_frame($bad);
    }

    // ─── Message Codec Tests ──────────────────────────────

    public function testHelloEncodeDecode(): void
    {
        $msg = new Message(MSG_HELLO);
        $msg->clientLanguage = 'PHP';
        $encoded = $msg->encode();

        $decoded = decode_message($encoded);
        $this->assertSame(MSG_HELLO, $decoded->type);
        $this->assertSame('PHP', $decoded->clientLanguage);
    }

    public function testHashNumber(): void
    {
        // SHA-256 of "42" = a1d0c6e83f027327d84661f...
        $hash = hash_number(42);
        $this->assertSame(10, strlen($hash));
        $this->assertMatchesRegularExpression('/^[0-9a-f]{10}$/', $hash);
    }
}
