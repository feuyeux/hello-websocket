<?php
/**
 * Hello WebSocket Protocol Codec - PHP implementation.
 *
 * Implements the canonical binary protocol defined in PROTOCOL.md.
 * Provides constants, primitive encoders/decoders, message types, frame codec,
 * and message dispatch for all 13 message types.
 */

namespace HelloWs\Common;

// ─── Constants ──────────────────────────────────────────────────────────

const PORT = 9898;
const MAGIC = 0x48;
const VERSION = 0x01;
const HEADER_LEN = 8;
const SERVER_LANG = 'PHP';
const CLIENT_LANG = 'PHP';

// Message types
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

// Error codes
const ERR_DECODE = 0x01;
const ERR_UNKNOWN_MSG_TYPE = 0x02;
const ERR_TRUNCATED_PAYLOAD = 0x03;
const ERR_BAD_MAGIC = 0x04;
const ERR_BAD_VERSION = 0x05;
const ERR_SESSION_NOT_FOUND = 0x06;
const ERR_INTERNAL = 0x07;

// Intervals (seconds, used as float for sub-second precision in timers)
const PING_INTERVAL = 1.0;
const SESSION_TIMEOUT = 60.0;
const TIME_INTERVAL = 5.0;
const RANDOM_INTERVAL = 5.0;
const KISS_INTERVAL = 5.0;

// ─── Primitive Encoders ──────────────────────────────────────────────────

class ByteWriter
{
    private string $buf = '';

    public function writeU8(int $v): void
    {
        $this->buf .= chr($v & 0xFF);
    }

    public function writeU16(int $v): void
    {
        $this->buf .= pack('n', $v & 0xFFFF);
    }

    public function writeU32(int $v): void
    {
        $this->buf .= pack('N', $v & 0xFFFFFFFF);
    }

    public function writeI32(int $v): void
    {
        $this->buf .= pack('N', $v & 0xFFFFFFFF);
    }

    public function writeI64(int $v): void
    {
        // PHP ints are 64-bit signed; arithmetic right shift preserves sign
        $high = ($v >> 32) & 0xFFFFFFFF;
        $low = $v & 0xFFFFFFFF;
        $this->buf .= pack('NN', $high, $low);
    }

    public function writeString(string $s): void
    {
        $b = $s;
        $this->writeU32(strlen($b));
        $this->buf .= $b;
    }

    /** @param array<string,string> $m */
    public function writeKv(array $m): void
    {
        $this->writeU32(count($m));
        foreach ($m as $k => $v) {
            $this->writeString((string)$k);
            $this->writeString((string)$v);
        }
    }

    public function data(): string
    {
        return $this->buf;
    }
}

// ─── Primitive Decoders ─────────────────────────────────────────────────

class ByteReader
{
    private string $data;
    private int $pos = 0;
    private int $len;

    public function __construct(string $data)
    {
        $this->data = $data;
        $this->len = strlen($data);
    }

    public function remaining(): int { return $this->len - $this->pos; }

    private function requireBytes(int $size, string $field): void
    {
        if ($size < 0 || $size > $this->remaining()) {
            throw new \InvalidArgumentException("$field requires $size bytes, only {$this->remaining()} remain");
        }
    }

    public function readU8(): int
    {
        $this->requireBytes(1, 'u8');
        $v = ord($this->data[$this->pos]);
        $this->pos++;
        return $v;
    }

    public function readU16(): int
    {
        $this->requireBytes(2, 'u16');
        $v = unpack('n', substr($this->data, $this->pos, 2))[1];
        $this->pos += 2;
        return $v;
    }

    public function readU32(): int
    {
        $this->requireBytes(4, 'u32');
        $v = unpack('N', substr($this->data, $this->pos, 4))[1];
        $this->pos += 4;
        return $v;
    }

    public function readI32(): int
    {
        $this->requireBytes(4, 'i32');
        $v = unpack('N', substr($this->data, $this->pos, 4))[1];
        $this->pos += 4;
        // Convert to signed
        if ($v >= 2147483648) {
            $v -= 4294967296;
        }
        return $v;
    }

    public function readI64(): int
    {
        $this->requireBytes(8, 'i64');
        $high = unpack('N', substr($this->data, $this->pos, 4))[1];
        $low = unpack('N', substr($this->data, $this->pos + 4, 4))[1];
        $this->pos += 8;
        // Combine using pack/unpack to avoid float precision loss
        $packed = pack('NN', $high, $low);
        $v = unpack('J', $packed)[1];
        // unpack('J') returns int for values <= PHP_INT_MAX, float otherwise
        if (is_float($v)) {
            $v = (int)($v - 18446744073709551616.0);
        }
        return $v;
    }

    public function readString(): string
    {
        $ln = $this->readU32();
        $this->requireBytes($ln, 'string');
        $s = substr($this->data, $this->pos, $ln);
        $this->pos += $ln;
        return $s;
    }

    /** @return array<string,string> */
    public function readKv(): array
    {
        $count = $this->readU32();
        if ($count > intdiv($this->remaining(), 8)) {
            throw new \InvalidArgumentException("kv count $count exceeds remaining payload");
        }
        $m = [];
        for ($i = 0; $i < $count; $i++) {
            $k = $this->readString();
            $v = $this->readString();
            $m[$k] = $v;
        }
        return $m;
    }
}

// ─── Frame Codec ─────────────────────────────────────────────────────────

function encode_frame(int $msgType, string $payload): string
{
    $header = pack('CCCC', MAGIC, VERSION, $msgType, 0x00);
    $header .= pack('N', strlen($payload));
    return $header . $payload;
}

/**
 * @return array{0:int,1:string} [msgType, payload]
 */
function decode_frame(string $data): array
{
    $len = strlen($data);
    if ($len < HEADER_LEN) {
        throw new \InvalidArgumentException("frame too short: $len bytes");
    }
    $magic = ord($data[0]);
    if ($magic !== MAGIC) {
        throw new \InvalidArgumentException(sprintf("bad magic: 0x%02x", $magic));
    }
    $version = ord($data[1]);
    if ($version !== VERSION) {
        throw new \InvalidArgumentException(sprintf("bad version: 0x%02x", $version));
    }
    $msgType = ord($data[2]);
    $payloadLen = unpack('N', substr($data, 4, 4))[1];
    if ($payloadLen !== $len - HEADER_LEN) {
        throw new \InvalidArgumentException("payload length mismatch: declared $payloadLen, available " . ($len - HEADER_LEN));
    }
    return [$msgType, substr($data, HEADER_LEN, $payloadLen)];
}

// ─── Message Class ───────────────────────────────────────────────────────

class Message
{
    public int $type;
    public ?string $clientLanguage = null;
    public ?string $serverLanguage = null;
    public ?int $echoId = null;
    public ?string $echoMeta = null;
    public ?string $echoData = null;
    public ?int $echoStatus = null;
    /** @var array<int,array{idx:int,type:int,kv:array<string,string>}> */
    public ?array $echoResults = null;
    public ?string $osName = null;
    public ?string $osVersion = null;
    public ?string $osRelease = null;
    public ?string $osArch = null;
    public ?string $kissLanguage = null;
    public ?string $kissEncoding = null;
    public ?string $kissTimeZone = null;
    public ?int $timestampMs = null;
    public ?string $iso8601 = null;
    public ?int $randomId = null;
    public ?int $randomNumber = null;
    public ?string $hashHex = null;
    public ?string $disconnectReason = null;
    public ?int $errorCode = null;
    public ?string $errorMessage = null;

    public function __construct(int $type)
    {
        $this->type = $type;
    }

    public function encode(): string
    {
        $w = new ByteWriter();
        switch ($this->type) {
            case MSG_HELLO:
                $w->writeString($this->clientLanguage ?? '');
                break;
            case MSG_BONJOUR:
                $w->writeString($this->serverLanguage ?? '');
                break;
            case MSG_ECHO_REQUEST:
                $w->writeI64($this->echoId ?? 0);
                $w->writeString($this->echoMeta ?? '');
                $w->writeString($this->echoData ?? '');
                break;
            case MSG_ECHO_RESPONSE:
                $w->writeI32($this->echoStatus ?? 200);
                $w->writeU32(count($this->echoResults ?? []));
                foreach ($this->echoResults ?? [] as $r) {
                    $w->writeI64($r['idx']);
                    $w->writeU8($r['type']);
                    $w->writeKv($r['kv']);
                }
                break;
            case MSG_KISS_REQUEST:
                $w->writeString($this->osName ?? '');
                $w->writeString($this->osVersion ?? '');
                $w->writeString($this->osRelease ?? '');
                $w->writeString($this->osArch ?? '');
                break;
            case MSG_KISS_RESPONSE:
                $w->writeString($this->kissLanguage ?? '');
                $w->writeString($this->kissEncoding ?? '');
                $w->writeString($this->kissTimeZone ?? '');
                break;
            case MSG_PING:
                $w->writeI64($this->timestampMs ?? 0);
                break;
            case MSG_PONG:
                $w->writeI64($this->timestampMs ?? 0);
                break;
            case MSG_TIME_NOTIFICATION:
                $w->writeI64($this->timestampMs ?? 0);
                $w->writeString($this->iso8601 ?? '');
                break;
            case MSG_RANDOM_NUMBER:
                $w->writeI64($this->randomId ?? 0);
                $w->writeI64($this->randomNumber ?? 0);
                break;
            case MSG_HASH_RESPONSE:
                $w->writeI64($this->randomId ?? 0);
                $w->writeString($this->hashHex ?? '');
                break;
            case MSG_DISCONNECT:
                $w->writeString($this->disconnectReason ?? '');
                break;
            case MSG_ERROR:
                $w->writeI32($this->errorCode ?? 0);
                $w->writeString($this->errorMessage ?? '');
                break;
            default:
                throw new \InvalidArgumentException("unknown message type: 0x" . dechex($this->type));
        }
        return encode_frame($this->type, $w->data());
    }
}

function decode_message(string $data): Message
{
    [$msgType, $payload] = decode_frame($data);
    $r = new ByteReader($payload);
    $msg = new Message($msgType);

    switch ($msgType) {
        case MSG_HELLO:
            $msg->clientLanguage = $r->readString();
            break;
        case MSG_BONJOUR:
            $msg->serverLanguage = $r->readString();
            break;
        case MSG_ECHO_REQUEST:
            $msg->echoId = $r->readI64();
            $msg->echoMeta = $r->readString();
            $msg->echoData = $r->readString();
            break;
        case MSG_ECHO_RESPONSE:
            $msg->echoStatus = $r->readI32();
            $count = $r->readU32();
            if ($count > intdiv($r->remaining(), 13)) {
                throw new \InvalidArgumentException("result count $count exceeds remaining payload");
            }
            $msg->echoResults = [];
            for ($i = 0; $i < $count; $i++) {
                $idx = $r->readI64();
                $type = $r->readU8();
                $kv = $r->readKv();
                $msg->echoResults[] = ['idx' => $idx, 'type' => $type, 'kv' => $kv];
            }
            break;
        case MSG_KISS_REQUEST:
            $msg->osName = $r->readString();
            $msg->osVersion = $r->readString();
            $msg->osRelease = $r->readString();
            $msg->osArch = $r->readString();
            break;
        case MSG_KISS_RESPONSE:
            $msg->kissLanguage = $r->readString();
            $msg->kissEncoding = $r->readString();
            $msg->kissTimeZone = $r->readString();
            break;
        case MSG_PING:
            $msg->timestampMs = $r->readI64();
            break;
        case MSG_PONG:
            $msg->timestampMs = $r->readI64();
            break;
        case MSG_TIME_NOTIFICATION:
            $msg->timestampMs = $r->readI64();
            $msg->iso8601 = $r->readString();
            break;
        case MSG_RANDOM_NUMBER:
            $msg->randomId = $r->readI64();
            $msg->randomNumber = $r->readI64();
            break;
        case MSG_HASH_RESPONSE:
            $msg->randomId = $r->readI64();
            $msg->hashHex = $r->readString();
            break;
        case MSG_DISCONNECT:
            $msg->disconnectReason = $r->readString();
            break;
        case MSG_ERROR:
            $msg->errorCode = $r->readI32();
            $msg->errorMessage = $r->readString();
            break;
        default:
            throw new \InvalidArgumentException("unknown message type: 0x" . dechex($msgType));
    }

    return $msg;
}

// ─── Factory Helpers ─────────────────────────────────────────────────────

function hello(string $clientLanguage): Message
{
    $m = new Message(MSG_HELLO);
    $m->clientLanguage = $clientLanguage;
    return $m;
}

function bonjour(string $serverLanguage): Message
{
    $m = new Message(MSG_BONJOUR);
    $m->serverLanguage = $serverLanguage;
    return $m;
}

function ping(int $timestampMs): Message
{
    $m = new Message(MSG_PING);
    $m->timestampMs = $timestampMs;
    return $m;
}

function pong(int $timestampMs): Message
{
    $m = new Message(MSG_PONG);
    $m->timestampMs = $timestampMs;
    return $m;
}

function timeNotif(int $timestampMs, string $iso8601): Message
{
    $m = new Message(MSG_TIME_NOTIFICATION);
    $m->timestampMs = $timestampMs;
    $m->iso8601 = $iso8601;
    return $m;
}

function randomNumberMsg(int $id, int $number): Message
{
    $m = new Message(MSG_RANDOM_NUMBER);
    $m->randomId = $id;
    $m->randomNumber = $number;
    return $m;
}

function hashResponseMsg(int $id, string $hashHex): Message
{
    $m = new Message(MSG_HASH_RESPONSE);
    $m->randomId = $id;
    $m->hashHex = $hashHex;
    return $m;
}

function kissRequest(string $osName, string $osVersion, string $osRelease, string $osArch): Message
{
    $m = new Message(MSG_KISS_REQUEST);
    $m->osName = $osName;
    $m->osVersion = $osVersion;
    $m->osRelease = $osRelease;
    $m->osArch = $osArch;
    return $m;
}

function kissResponse(string $language, string $encoding, string $timeZone): Message
{
    $m = new Message(MSG_KISS_RESPONSE);
    $m->kissLanguage = $language;
    $m->kissEncoding = $encoding;
    $m->kissTimeZone = $timeZone;
    return $m;
}

function disconnectMsg(string $reason): Message
{
    $m = new Message(MSG_DISCONNECT);
    $m->disconnectReason = $reason;
    return $m;
}

function errorMsg(int $code, string $message): Message
{
    $m = new Message(MSG_ERROR);
    $m->errorCode = $code;
    $m->errorMessage = $message;
    return $m;
}

// ─── Utility ─────────────────────────────────────────────────────────────

function now_ms(): int
{
    return (int)(microtime(true) * 1000);
}

function now_iso(): string
{
    return gmdate('Y-m-d\TH:i:s\Z');
}

function hash_number(int $num): string
{
    return substr(hash('sha256', (string)$num), 0, 10);
}

function log_msg(string $name, string $msg): void
{
    $ts = date('Y-m-d H:i:s');
    echo "[$ts] [INFO] [$name] $msg\n";
}
