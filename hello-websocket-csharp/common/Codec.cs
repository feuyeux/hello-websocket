using System.Buffers.Binary;
using System.Security.Cryptography;
using System.Text;

namespace HelloWebSocket;

// ─── Constants ───────────────────────────────────────────────────────────

public static partial class Codec
{
    public const int PORT = 9898;
    public const byte MAGIC = 0x48;
    public const byte VERSION = 0x01;
    public const int HEADER_LEN = 8;
    public const string SERVER_LANG = "CSHARP";
    public const string CLIENT_LANG = "CSHARP";

    // Message types
    public const byte MSG_HELLO = 0x01;
    public const byte MSG_BONJOUR = 0x02;
    public const byte MSG_ECHO_REQUEST = 0x03;
    public const byte MSG_ECHO_RESPONSE = 0x04;
    public const byte MSG_KISS_REQUEST = 0x05;
    public const byte MSG_KISS_RESPONSE = 0x06;
    public const byte MSG_PING = 0x07;
    public const byte MSG_PONG = 0x08;
    public const byte MSG_TIME_NOTIFICATION = 0x09;
    public const byte MSG_RANDOM_NUMBER = 0x0A;
    public const byte MSG_HASH_RESPONSE = 0x0B;
    public const byte MSG_DISCONNECT = 0x0C;
    public const byte MSG_ERROR = 0x7F;

    // Error codes
    public const int ERR_DECODE = 0x01;
    public const int ERR_UNKNOWN_MSG_TYPE = 0x02;
    public const int ERR_TRUNCATED_PAYLOAD = 0x03;
    public const int ERR_BAD_MAGIC = 0x04;
    public const int ERR_BAD_VERSION = 0x05;
    public const int ERR_SESSION_NOT_FOUND = 0x06;
    public const int ERR_INTERNAL = 0x07;

    // Intervals (ms)
    public const long PING_INTERVAL_MS = 1000;
    public const long SESSION_TIMEOUT_MS = 60000;
    public const long TIME_INTERVAL_MS = 5000;
    public const long RANDOM_INTERVAL_MS = 5000;
    public const long KISS_INTERVAL_MS = 5000;
}

// ─── ByteWriter ─────────────────────────────────────────────────────────

public static partial class Codec
{
    public class ByteWriter
    {
        private readonly List<byte> _buf = new();

        public void WriteU8(byte v) => _buf.Add(v);
        public void WriteU16(ushort v) { var b = new byte[2]; BinaryPrimitives.WriteUInt16BigEndian(b, v); _buf.AddRange(b); }
        public void WriteU32(uint v) { var b = new byte[4]; BinaryPrimitives.WriteUInt32BigEndian(b, v); _buf.AddRange(b); }
        public void WriteI32(int v) => WriteU32((uint)v);
        public void WriteI64(long v) { var b = new byte[8]; BinaryPrimitives.WriteInt64BigEndian(b, v); _buf.AddRange(b); }

        public void WriteString(string s)
        {
            var b = Encoding.UTF8.GetBytes(s);
            WriteU32((uint)b.Length);
            _buf.AddRange(b);
        }

        public void WriteKV(Dictionary<string, string> m)
        {
            WriteU32((uint)m.Count);
            foreach (var (k, v) in m) { WriteString(k); WriteString(v); }
        }

        public byte[] ToArray() => _buf.ToArray();
    }
}

// ─── ByteReader ─────────────────────────────────────────────────────────

public static partial class Codec
{
    public class ByteReader
    {
        private readonly byte[] _data;
        private int _pos;

        public ByteReader(byte[] data) { _data = data; _pos = 0; }

        public byte ReadU8()
        {
            if (_pos + 1 > _data.Length) throw new Exception("unexpected end of data reading u8");
            return _data[_pos++];
        }

        public ushort ReadU16()
        {
            if (_pos + 2 > _data.Length) throw new Exception("unexpected end of data reading u16");
            var v = BinaryPrimitives.ReadUInt16BigEndian(_data.AsSpan(_pos, 2));
            _pos += 2;
            return v;
        }

        public uint ReadU32()
        {
            if (_pos + 4 > _data.Length) throw new Exception("unexpected end of data reading u32");
            var v = BinaryPrimitives.ReadUInt32BigEndian(_data.AsSpan(_pos, 4));
            _pos += 4;
            return v;
        }

        public int ReadI32() => (int)ReadU32();

        public long ReadI64()
        {
            if (_pos + 8 > _data.Length) throw new Exception("unexpected end of data reading i64");
            var v = BinaryPrimitives.ReadInt64BigEndian(_data.AsSpan(_pos, 8));
            _pos += 8;
            return v;
        }

        public string ReadString()
        {
            var ln = (int)ReadU32();
            if (_pos + ln > _data.Length) throw new Exception($"string length {ln} exceeds remaining data");
            var s = Encoding.UTF8.GetString(_data, _pos, ln);
            _pos += ln;
            return s;
        }

        public Dictionary<string, string> ReadKV()
        {
            var count = ReadU32();
            var m = new Dictionary<string, string>((int)count);
            for (int i = 0; i < count; i++) { m[ReadString()] = ReadString(); }
            return m;
        }
    }
}

// ─── Frame Codec ────────────────────────────────────────────────────────

public static partial class Codec
{
    public static byte[] EncodeFrame(byte msgType, byte[] payload)
    {
        var buf = new byte[HEADER_LEN + payload.Length];
        buf[0] = MAGIC;
        buf[1] = VERSION;
        buf[2] = msgType;
        buf[3] = 0x00;
        BinaryPrimitives.WriteUInt32BigEndian(buf.AsSpan(4), (uint)payload.Length);
        Array.Copy(payload, 0, buf, HEADER_LEN, payload.Length);
        return buf;
    }

    public static (byte msgType, byte[] payload) DecodeFrame(byte[] data)
    {
        if (data.Length < HEADER_LEN) throw new Exception($"frame too short: {data.Length}");
        if (data[0] != MAGIC) throw new Exception($"bad magic: 0x{data[0]:x2}");
        if (data[1] != VERSION) throw new Exception($"bad version: 0x{data[1]:x2}");
        var msgType = data[2];
        var payloadLen = BinaryPrimitives.ReadUInt32BigEndian(data.AsSpan(4, 4));
        if (payloadLen > data.Length - HEADER_LEN)
            throw new Exception($"truncated payload: declared {payloadLen}, available {data.Length - HEADER_LEN}");
        var payload = new byte[payloadLen];
        Array.Copy(data, HEADER_LEN, payload, 0, (int)payloadLen);
        return (msgType, payload);
    }
}

// ─── Message Types ──────────────────────────────────────────────────────

public static partial class Codec
{
    public record EchoResult(long Idx, byte Type, Dictionary<string, string> Kv);

    public class Message
    {
        public byte Type;
        public string? ClientLanguage;
        public string? ServerLanguage;
        public long EchoId; public string? EchoMeta; public string? EchoData;
        public int EchoStatus; public EchoResult[]? EchoResults;
        public string? OsName; public string? OsVersion; public string? OsRelease; public string? OsArch;
        public string? KissLanguage; public string? KissEncoding; public string? KissTimeZone;
        public long TimestampMs; public string? Iso8601;
        public long RandomId; public long RandomNumber;
        public string? HashHex;
        public string? DisconnectReason;
        public int ErrorCode; public string? ErrorMessage;

        public byte[] Encode()
        {
            var w = new ByteWriter();
            switch (Type)
            {
                case MSG_HELLO: w.WriteString(ClientLanguage!); return EncodeFrame(MSG_HELLO, w.ToArray());
                case MSG_BONJOUR: w.WriteString(ServerLanguage!); return EncodeFrame(MSG_BONJOUR, w.ToArray());
                case MSG_ECHO_REQUEST: w.WriteI64(EchoId); w.WriteString(EchoMeta!); w.WriteString(EchoData!); return EncodeFrame(MSG_ECHO_REQUEST, w.ToArray());
                case MSG_ECHO_RESPONSE:
                    w.WriteI32(EchoStatus); w.WriteU32((uint)EchoResults!.Length);
                    foreach (var r in EchoResults) { w.WriteI64(r.Idx); w.WriteU8(r.Type); w.WriteKV(r.Kv); }
                    return EncodeFrame(MSG_ECHO_RESPONSE, w.ToArray());
                case MSG_KISS_REQUEST: w.WriteString(OsName!); w.WriteString(OsVersion!); w.WriteString(OsRelease!); w.WriteString(OsArch!); return EncodeFrame(MSG_KISS_REQUEST, w.ToArray());
                case MSG_KISS_RESPONSE: w.WriteString(KissLanguage!); w.WriteString(KissEncoding!); w.WriteString(KissTimeZone!); return EncodeFrame(MSG_KISS_RESPONSE, w.ToArray());
                case MSG_PING: w.WriteI64(TimestampMs); return EncodeFrame(MSG_PING, w.ToArray());
                case MSG_PONG: w.WriteI64(TimestampMs); return EncodeFrame(MSG_PONG, w.ToArray());
                case MSG_TIME_NOTIFICATION: w.WriteI64(TimestampMs); w.WriteString(Iso8601!); return EncodeFrame(MSG_TIME_NOTIFICATION, w.ToArray());
                case MSG_RANDOM_NUMBER: w.WriteI64(RandomId); w.WriteI64(RandomNumber); return EncodeFrame(MSG_RANDOM_NUMBER, w.ToArray());
                case MSG_HASH_RESPONSE: w.WriteI64(RandomId); w.WriteString(HashHex!); return EncodeFrame(MSG_HASH_RESPONSE, w.ToArray());
                case MSG_DISCONNECT: w.WriteString(DisconnectReason!); return EncodeFrame(MSG_DISCONNECT, w.ToArray());
                case MSG_ERROR: w.WriteI32(ErrorCode); w.WriteString(ErrorMessage!); return EncodeFrame(MSG_ERROR, w.ToArray());
                default: throw new ArgumentException($"unknown message type: 0x{Type:x2}");
            }
        }
    }

    public static Message DecodeMessage(byte[] data)
    {
        var (msgType, payload) = DecodeFrame(data);
        var r = new ByteReader(payload);
        var m = new Message { Type = msgType };
        switch (msgType)
        {
            case MSG_HELLO: m.ClientLanguage = r.ReadString(); break;
            case MSG_BONJOUR: m.ServerLanguage = r.ReadString(); break;
            case MSG_ECHO_REQUEST: m.EchoId = r.ReadI64(); m.EchoMeta = r.ReadString(); m.EchoData = r.ReadString(); break;
            case MSG_ECHO_RESPONSE:
                m.EchoStatus = r.ReadI32();
                var count = r.ReadU32();
                var results = new EchoResult[count];
                for (int i = 0; i < count; i++) results[i] = new EchoResult(r.ReadI64(), r.ReadU8(), r.ReadKV());
                m.EchoResults = results;
                break;
            case MSG_KISS_REQUEST: m.OsName = r.ReadString(); m.OsVersion = r.ReadString(); m.OsRelease = r.ReadString(); m.OsArch = r.ReadString(); break;
            case MSG_KISS_RESPONSE: m.KissLanguage = r.ReadString(); m.KissEncoding = r.ReadString(); m.KissTimeZone = r.ReadString(); break;
            case MSG_PING: m.TimestampMs = r.ReadI64(); break;
            case MSG_PONG: m.TimestampMs = r.ReadI64(); break;
            case MSG_TIME_NOTIFICATION: m.TimestampMs = r.ReadI64(); m.Iso8601 = r.ReadString(); break;
            case MSG_RANDOM_NUMBER: m.RandomId = r.ReadI64(); m.RandomNumber = r.ReadI64(); break;
            case MSG_HASH_RESPONSE: m.RandomId = r.ReadI64(); m.HashHex = r.ReadString(); break;
            case MSG_DISCONNECT: m.DisconnectReason = r.ReadString(); break;
            case MSG_ERROR: m.ErrorCode = r.ReadI32(); m.ErrorMessage = r.ReadString(); break;
            default: throw new Exception($"unknown message type: 0x{msgType:x2}");
        }
        return m;
    }

    // ─── Utility ─────────────────────────────────────────────────────────

    public static long NowMs() => DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();

    public static string NowISO() => DateTimeOffset.UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ");

    public static string HashNumber(long num)
    {
        var hash = SHA256.HashData(Encoding.UTF8.GetBytes(num.ToString()));
        return Convert.ToHexString(hash).ToLowerInvariant().Substring(0, 10);
    }

    public static void Log(string name, string msg)
    {
        var ts = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss");
        Console.WriteLine($"[{ts}] [INFO] [{name}] {msg}");
    }

    // ─── Factory Helpers ─────────────────────────────────────────────────

    public static Message Hello(string lang) => new() { Type = MSG_HELLO, ClientLanguage = lang };
    public static Message Bonjour(string lang) => new() { Type = MSG_BONJOUR, ServerLanguage = lang };
    public static Message Ping(long ts) => new() { Type = MSG_PING, TimestampMs = ts };
    public static Message Pong(long ts) => new() { Type = MSG_PONG, TimestampMs = ts };
    public static Message TimeNotif(long ts, string iso) => new() { Type = MSG_TIME_NOTIFICATION, TimestampMs = ts, Iso8601 = iso };
    public static Message KissRequest(string os, string ver, string rel, string arch) => new() { Type = MSG_KISS_REQUEST, OsName = os, OsVersion = ver, OsRelease = rel, OsArch = arch };
    public static Message KissResponse(string lang, string enc, string tz) => new() { Type = MSG_KISS_RESPONSE, KissLanguage = lang, KissEncoding = enc, KissTimeZone = tz };
    public static Message RandomNumberMsg(long id, long num) => new() { Type = MSG_RANDOM_NUMBER, RandomId = id, RandomNumber = num };
    public static Message HashResponseMsg(long id, string hash) => new() { Type = MSG_HASH_RESPONSE, RandomId = id, HashHex = hash };
    public static Message DisconnectMsg(string reason) => new() { Type = MSG_DISCONNECT, DisconnectReason = reason };
    public static Message ErrorMsg(int code, string msg) => new() { Type = MSG_ERROR, ErrorCode = code, ErrorMessage = msg };
    public static Message EchoRequestMsg(long id, string meta, string data) => new() { Type = MSG_ECHO_REQUEST, EchoId = id, EchoMeta = meta, EchoData = data };
    public static Message EchoResponseMsg(int status, EchoResult[] results) => new() { Type = MSG_ECHO_RESPONSE, EchoStatus = status, EchoResults = results };
}
