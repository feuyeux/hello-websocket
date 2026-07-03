package io.github.hellowebsocket;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hello WebSocket Protocol Codec - Java implementation.
 * Implements the canonical binary protocol defined in PROTOCOL.md.
 */
public final class Codec {

    private Codec() {}

    // ─── Constants ───────────────────────────────────────────────────────────

    public static final int PORT = 9898;
    public static final byte MAGIC = 0x48;
    public static final byte VERSION = 0x01;
    public static final int HEADER_LEN = 8;
    public static final String SERVER_LANG = "JAVA";
    public static final String CLIENT_LANG = "JAVA";

    // Message types
    public static final byte MSG_HELLO = 0x01;
    public static final byte MSG_BONJOUR = 0x02;
    public static final byte MSG_ECHO_REQUEST = 0x03;
    public static final byte MSG_ECHO_RESPONSE = 0x04;
    public static final byte MSG_KISS_REQUEST = 0x05;
    public static final byte MSG_KISS_RESPONSE = 0x06;
    public static final byte MSG_PING = 0x07;
    public static final byte MSG_PONG = 0x08;
    public static final byte MSG_TIME_NOTIFICATION = 0x09;
    public static final byte MSG_RANDOM_NUMBER = 0x0A;
    public static final byte MSG_HASH_RESPONSE = 0x0B;
    public static final byte MSG_DISCONNECT = 0x0C;
    public static final byte MSG_ERROR = 0x7F;

    // Error codes
    public static final int ERR_DECODE = 0x01;
    public static final int ERR_UNKNOWN_MSG_TYPE = 0x02;
    public static final int ERR_TRUNCATED_PAYLOAD = 0x03;
    public static final int ERR_BAD_MAGIC = 0x04;
    public static final int ERR_BAD_VERSION = 0x05;
    public static final int ERR_SESSION_NOT_FOUND = 0x06;
    public static final int ERR_INTERNAL = 0x07;

    // Intervals (ms)
    public static final long PING_INTERVAL_MS = 1000;
    public static final long SESSION_TIMEOUT_MS = 60000;
    public static final long TIME_INTERVAL_MS = 5000;
    public static final long RANDOM_INTERVAL_MS = 5000;
    public static final long KISS_INTERVAL_MS = 5000;

    // ─── ByteWriter ──────────────────────────────────────────────────────────

    public static final class ByteWriter {
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

        public void writeU8(int v) { buf.write(v & 0xFF); }

        public void writeU16(int v) {
            buf.write((v >> 8) & 0xFF);
            buf.write(v & 0xFF);
        }

        public void writeU32(long v) {
            buf.write((int)((v >> 24) & 0xFF));
            buf.write((int)((v >> 16) & 0xFF));
            buf.write((int)((v >> 8) & 0xFF));
            buf.write((int)(v & 0xFF));
        }

        public void writeI32(int v) { writeU32(v & 0xFFFFFFFFL); }

        public void writeI64(long v) {
            buf.write((int)((v >> 56) & 0xFF));
            buf.write((int)((v >> 48) & 0xFF));
            buf.write((int)((v >> 40) & 0xFF));
            buf.write((int)((v >> 32) & 0xFF));
            buf.write((int)((v >> 24) & 0xFF));
            buf.write((int)((v >> 16) & 0xFF));
            buf.write((int)((v >> 8) & 0xFF));
            buf.write((int)(v & 0xFF));
        }

        public void writeString(String s) {
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            writeU32(b.length);
            buf.writeBytes(b);
        }

        public void writeKV(Map<String, String> m) {
            writeU32(m.size());
            for (var e : m.entrySet()) {
                writeString(e.getKey());
                writeString(e.getValue());
            }
        }

        public byte[] toByteArray() { return buf.toByteArray(); }
    }

    // ─── ByteReader ─────────────────────────────────────────────────────────

    public static final class ByteReader {
        private final byte[] data;
        private int pos;

        public ByteReader(byte[] data) { this.data = data; this.pos = 0; }

        public int readU8() throws DecodeException {
            if (pos + 1 > data.length) throw new DecodeException("unexpected end of data reading u8");
            return data[pos++] & 0xFF;
        }

        public int readU16() throws DecodeException {
            if (pos + 2 > data.length) throw new DecodeException("unexpected end of data reading u16");
            int v = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            return v;
        }

        public long readU32() throws DecodeException {
            if (pos + 4 > data.length) throw new DecodeException("unexpected end of data reading u32");
            long v = ((long)(data[pos] & 0xFF) << 24) | ((long)(data[pos + 1] & 0xFF) << 16)
                   | ((long)(data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        public int readI32() throws DecodeException { return (int) readU32(); }

        public long readI64() throws DecodeException {
            if (pos + 8 > data.length) throw new DecodeException("unexpected end of data reading i64");
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (data[pos + i] & 0xFF);
            }
            pos += 8;
            return v;
        }

        public String readString() throws DecodeException {
            long ln = readU32();
            if (pos + ln > data.length) throw new DecodeException("string length " + ln + " exceeds remaining data");
            String s = new String(data, pos, (int) ln, StandardCharsets.UTF_8);
            pos += ln;
            return s;
        }

        public Map<String, String> readKV() throws DecodeException {
            long count = readU32();
            Map<String, String> m = new LinkedHashMap<>((int) count);
            for (long i = 0; i < count; i++) {
                String k = readString();
                String v = readString();
                m.put(k, v);
            }
            return m;
        }
    }

    @SuppressWarnings("serial")
    public static final class DecodeException extends Exception {
        public DecodeException(String msg) { super(msg); }
    }

    // ─── Frame Codec ─────────────────────────────────────────────────────────

    public static byte[] encodeFrame(byte msgType, byte[] payload) {
        byte[] buf = new byte[HEADER_LEN + payload.length];
        buf[0] = MAGIC;
        buf[1] = VERSION;
        buf[2] = msgType;
        buf[3] = 0x00;
        buf[4] = (byte)((payload.length >> 24) & 0xFF);
        buf[5] = (byte)((payload.length >> 16) & 0xFF);
        buf[6] = (byte)((payload.length >> 8) & 0xFF);
        buf[7] = (byte)(payload.length & 0xFF);
        System.arraycopy(payload, 0, buf, HEADER_LEN, payload.length);
        return buf;
    }

    public record Frame(byte msgType, byte[] payload) {}

    public static Frame decodeFrame(byte[] data) throws DecodeException {
        if (data.length < HEADER_LEN) throw new DecodeException("frame too short: " + data.length);
        if (data[0] != MAGIC) throw new DecodeException("bad magic: 0x" + String.format("%02x", data[0]));
        if (data[1] != VERSION) throw new DecodeException("bad version: 0x" + String.format("%02x", data[1]));
        byte msgType = data[2];
        long payloadLen = ((long)(data[4] & 0xFF) << 24) | ((long)(data[5] & 0xFF) << 16)
                        | ((long)(data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        if (payloadLen > data.length - HEADER_LEN)
            throw new DecodeException("truncated payload: declared " + payloadLen + ", available " + (data.length - HEADER_LEN));
        byte[] payload = new byte[(int) payloadLen];
        System.arraycopy(data, HEADER_LEN, payload, 0, (int) payloadLen);
        return new Frame(msgType, payload);
    }

    // ─── Message Types ───────────────────────────────────────────────────────

    public record EchoResult(long idx, int type, Map<String, String> kv) {}

    public static final class Message {
        public byte type;
        public String clientLanguage;
        public String serverLanguage;
        public long echoId; public String echoMeta; public String echoData;
        public int echoStatus; public EchoResult[] echoResults;
        public String osName; public String osVersion; public String osRelease; public String osArch;
        public String kissLanguage; public String kissEncoding; public String kissTimeZone;
        public long timestampMs; public String iso8601;
        public long randomId; public long randomNumber;
        public String hashHex;
        public String disconnectReason;
        public int errorCode; public String errorMessage;

        public byte[] encode() {
            ByteWriter w = new ByteWriter();
            switch (type) {
                case MSG_HELLO -> { w.writeString(clientLanguage); return encodeFrame(MSG_HELLO, w.toByteArray()); }
                case MSG_BONJOUR -> { w.writeString(serverLanguage); return encodeFrame(MSG_BONJOUR, w.toByteArray()); }
                case MSG_ECHO_REQUEST -> { w.writeI64(echoId); w.writeString(echoMeta); w.writeString(echoData); return encodeFrame(MSG_ECHO_REQUEST, w.toByteArray()); }
                case MSG_ECHO_RESPONSE -> {
                    w.writeI32(echoStatus); w.writeU32(echoResults.length);
                    for (EchoResult r : echoResults) { w.writeI64(r.idx()); w.writeU8(r.type()); w.writeKV(r.kv()); }
                    return encodeFrame(MSG_ECHO_RESPONSE, w.toByteArray());
                }
                case MSG_KISS_REQUEST -> { w.writeString(osName); w.writeString(osVersion); w.writeString(osRelease); w.writeString(osArch); return encodeFrame(MSG_KISS_REQUEST, w.toByteArray()); }
                case MSG_KISS_RESPONSE -> { w.writeString(kissLanguage); w.writeString(kissEncoding); w.writeString(kissTimeZone); return encodeFrame(MSG_KISS_RESPONSE, w.toByteArray()); }
                case MSG_PING -> { w.writeI64(timestampMs); return encodeFrame(MSG_PING, w.toByteArray()); }
                case MSG_PONG -> { w.writeI64(timestampMs); return encodeFrame(MSG_PONG, w.toByteArray()); }
                case MSG_TIME_NOTIFICATION -> { w.writeI64(timestampMs); w.writeString(iso8601); return encodeFrame(MSG_TIME_NOTIFICATION, w.toByteArray()); }
                case MSG_RANDOM_NUMBER -> { w.writeI64(randomId); w.writeI64(randomNumber); return encodeFrame(MSG_RANDOM_NUMBER, w.toByteArray()); }
                case MSG_HASH_RESPONSE -> { w.writeI64(randomId); w.writeString(hashHex); return encodeFrame(MSG_HASH_RESPONSE, w.toByteArray()); }
                case MSG_DISCONNECT -> { w.writeString(disconnectReason); return encodeFrame(MSG_DISCONNECT, w.toByteArray()); }
                case MSG_ERROR -> { w.writeI32(errorCode); w.writeString(errorMessage); return encodeFrame(MSG_ERROR, w.toByteArray()); }
                default -> throw new IllegalArgumentException("unknown message type: 0x" + String.format("%02x", type));
            }
        }
    }

    public static Message decodeMessage(byte[] data) throws DecodeException {
        Frame frame = decodeFrame(data);
        ByteReader r = new ByteReader(frame.payload());
        Message m = new Message();
        m.type = frame.msgType();
        switch (frame.msgType()) {
            case MSG_HELLO -> { m.clientLanguage = r.readString(); }
            case MSG_BONJOUR -> { m.serverLanguage = r.readString(); }
            case MSG_ECHO_REQUEST -> { m.echoId = r.readI64(); m.echoMeta = r.readString(); m.echoData = r.readString(); }
            case MSG_ECHO_RESPONSE -> {
                m.echoStatus = r.readI32();
                long count = r.readU32();
                m.echoResults = new EchoResult[(int) count];
                for (int i = 0; i < count; i++) {
                    long idx = r.readI64();
                    int type = r.readU8();
                    Map<String, String> kv = r.readKV();
                    m.echoResults[i] = new EchoResult(idx, type, kv);
                }
            }
            case MSG_KISS_REQUEST -> { m.osName = r.readString(); m.osVersion = r.readString(); m.osRelease = r.readString(); m.osArch = r.readString(); }
            case MSG_KISS_RESPONSE -> { m.kissLanguage = r.readString(); m.kissEncoding = r.readString(); m.kissTimeZone = r.readString(); }
            case MSG_PING -> { m.timestampMs = r.readI64(); }
            case MSG_PONG -> { m.timestampMs = r.readI64(); }
            case MSG_TIME_NOTIFICATION -> { m.timestampMs = r.readI64(); m.iso8601 = r.readString(); }
            case MSG_RANDOM_NUMBER -> { m.randomId = r.readI64(); m.randomNumber = r.readI64(); }
            case MSG_HASH_RESPONSE -> { m.randomId = r.readI64(); m.hashHex = r.readString(); }
            case MSG_DISCONNECT -> { m.disconnectReason = r.readString(); }
            case MSG_ERROR -> { m.errorCode = r.readI32(); m.errorMessage = r.readString(); }
            default -> throw new DecodeException("unknown message type: 0x" + String.format("%02x", frame.msgType()));
        }
        return m;
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    public static long nowMs() { return Instant.now().toEpochMilli(); }

    public static String nowISO() {
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }

    public static String hashNumber(long num) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(String.valueOf(num).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void log(String name, String msg) {
        String ts = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.printf("[%s] [INFO] [%s] %s%n", ts, name, msg);
    }

    // ─── Message Factory Helpers ─────────────────────────────────────────────

    public static Message hello(String lang) { Message m = new Message(); m.type = MSG_HELLO; m.clientLanguage = lang; return m; }
    public static Message bonjour(String lang) { Message m = new Message(); m.type = MSG_BONJOUR; m.serverLanguage = lang; return m; }
    public static Message ping(long ts) { Message m = new Message(); m.type = MSG_PING; m.timestampMs = ts; return m; }
    public static Message pong(long ts) { Message m = new Message(); m.type = MSG_PONG; m.timestampMs = ts; return m; }
    public static Message timeNotif(long ts, String iso) { Message m = new Message(); m.type = MSG_TIME_NOTIFICATION; m.timestampMs = ts; m.iso8601 = iso; return m; }
    public static Message kissRequest(String os, String ver, String rel, String arch) { Message m = new Message(); m.type = MSG_KISS_REQUEST; m.osName = os; m.osVersion = ver; m.osRelease = rel; m.osArch = arch; return m; }
    public static Message kissResponse(String lang, String enc, String tz) { Message m = new Message(); m.type = MSG_KISS_RESPONSE; m.kissLanguage = lang; m.kissEncoding = enc; m.kissTimeZone = tz; return m; }
    public static Message randomNumber(long id, long num) { Message m = new Message(); m.type = MSG_RANDOM_NUMBER; m.randomId = id; m.randomNumber = num; return m; }
    public static Message hashResponse(long id, String hash) { Message m = new Message(); m.type = MSG_HASH_RESPONSE; m.randomId = id; m.hashHex = hash; return m; }
    public static Message disconnect(String reason) { Message m = new Message(); m.type = MSG_DISCONNECT; m.disconnectReason = reason; return m; }
    public static Message error(int code, String msg) { Message m = new Message(); m.type = MSG_ERROR; m.errorCode = code; m.errorMessage = msg; return m; }
    public static Message echoRequest(long id, String meta, String data) { Message m = new Message(); m.type = MSG_ECHO_REQUEST; m.echoId = id; m.echoMeta = meta; m.echoData = data; return m; }
    public static Message echoResponse(int status, EchoResult[] results) { Message m = new Message(); m.type = MSG_ECHO_RESPONSE; m.echoStatus = status; m.echoResults = results; return m; }
}
