// Hello WebSocket Protocol Codec - Kotlin implementation.
// Implements the canonical binary protocol defined in PROTOCOL.md.
package org.feuyeux.ws.common

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ─── Constants ───────────────────────────────────────────────────────────

const val PORT = 9898
const val MAGIC: Byte = 0x48
const val VERSION: Byte = 0x01
const val HEADER_LEN = 8
const val SERVER_LANG = "KOTLIN"
const val CLIENT_LANG = "KOTLIN"

// Message types
const val MSG_HELLO: Byte = 0x01
const val MSG_BONJOUR: Byte = 0x02
const val MSG_ECHO_REQUEST: Byte = 0x03
const val MSG_ECHO_RESPONSE: Byte = 0x04
const val MSG_KISS_REQUEST: Byte = 0x05
const val MSG_KISS_RESPONSE: Byte = 0x06
const val MSG_PING: Byte = 0x07
const val MSG_PONG: Byte = 0x08
const val MSG_TIME_NOTIFICATION: Byte = 0x09
const val MSG_RANDOM_NUMBER: Byte = 0x0A
const val MSG_HASH_RESPONSE: Byte = 0x0B
const val MSG_DISCONNECT: Byte = 0x0C
const val MSG_ERROR: Byte = 0x7F

// Error codes
const val ERR_DECODE = 0x01
const val ERR_UNKNOWN_MSG_TYPE = 0x02
const val ERR_TRUNCATED_PAYLOAD = 0x03
const val ERR_BAD_MAGIC = 0x04
const val ERR_BAD_VERSION = 0x05
const val ERR_SESSION_NOT_FOUND = 0x06
const val ERR_INTERNAL = 0x07

// Intervals (ms)
const val PING_INTERVAL_MS = 1000L
const val SESSION_TIMEOUT_MS = 60000L
const val TIME_INTERVAL_MS = 5000L
const val RANDOM_INTERVAL_MS = 5000L
const val KISS_INTERVAL_MS = 5000L

// ─── ByteWriter ──────────────────────────────────────────────────────────

class ByteWriter {
    private val buf = java.io.ByteArrayOutputStream()

    fun writeU8(v: Int) = buf.write(v and 0xFF)
    fun writeU16(v: Int) { buf.write((v shr 8) and 0xFF); buf.write(v and 0xFF) }
    fun writeU32(v: Int) {
        buf.write((v shr 24) and 0xFF); buf.write((v shr 16) and 0xFF)
        buf.write((v shr 8) and 0xFF); buf.write(v and 0xFF)
    }
    fun writeI32(v: Int) = writeU32(v)
    fun writeI64(v: Long) {
        writeU32((v shr 32).toInt())
        writeU32((v and 0xFFFFFFFFL).toInt())
    }
    fun writeString(s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        writeU32(b.size)
        buf.write(b)
    }
    fun writeKV(m: Map<String, String>) {
        writeU32(m.size)
        for ((k, v) in m) { writeString(k); writeString(v) }
    }
    fun toBytes(): ByteArray = buf.toByteArray()
}

// ─── ByteReader ─────────────────────────────────────────────────────────

class ByteReader(private val data: ByteArray) {
    private var pos = 0
    fun remaining(): Int = data.size - pos

    fun readU8(): Int {
        check(1)
        return data[pos++].toInt() and 0xFF
    }
    fun readU16(): Int {
        check(2)
        val v = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
        pos += 2
        return v
    }
    fun readU32(): Int {
        check(4)
        val v = ((data[pos].toInt() and 0xFF) shl 24) or
                ((data[pos + 1].toInt() and 0xFF) shl 16) or
                ((data[pos + 2].toInt() and 0xFF) shl 8) or
                (data[pos + 3].toInt() and 0xFF)
        pos += 4
        return v
    }
    fun readI32(): Int = readU32()
    fun readI64(): Long {
        val hi = readU32().toLong() and 0xFFFFFFFFL
        val lo = readU32().toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }
    fun readString(): String {
        val len = readU32()
        if (len < 0 || len > remaining()) throw IllegalStateException("string length $len exceeds remaining data")
        val s = String(data, pos, len, Charsets.UTF_8)
        pos += len
        return s
    }
    fun readKV(): Map<String, String> {
        val count = readU32()
        if (count < 0 || count > remaining() / 8) throw IllegalStateException("kv count $count exceeds remaining payload")
        val m = LinkedHashMap<String, String>(count)
        for (i in 0 until count) {
            val key = readString()
            val value = readString()
            m[key] = value
        }
        return m
    }

    private fun check(n: Int) {
        if (pos + n > data.size) throw IllegalStateException("unexpected end of data")
    }
}

// ─── Frame Codec ────────────────────────────────────────────────────────

fun encodeFrame(msgType: Byte, payload: ByteArray): ByteArray {
    val buf = ByteArray(HEADER_LEN + payload.size)
    buf[0] = MAGIC; buf[1] = VERSION; buf[2] = msgType; buf[3] = 0x00
    val len = payload.size
    buf[4] = ((len shr 24) and 0xFF).toByte()
    buf[5] = ((len shr 16) and 0xFF).toByte()
    buf[6] = ((len shr 8) and 0xFF).toByte()
    buf[7] = (len and 0xFF).toByte()
    System.arraycopy(payload, 0, buf, HEADER_LEN, payload.size)
    return buf
}

data class Frame(val msgType: Byte, val payload: ByteArray)

fun decodeFrame(data: ByteArray): Frame {
    if (data.size < HEADER_LEN) throw IllegalStateException("frame too short: ${data.size}")
    if (data[0] != MAGIC) throw IllegalStateException("bad magic: 0x${data[0].toInt().toString(16)}")
    if (data[1] != VERSION) throw IllegalStateException("bad version: 0x${data[1].toInt().toString(16)}")
    val msgType = data[2]
    val payloadLen = ((data[4].toInt() and 0xFF) shl 24) or
                     ((data[5].toInt() and 0xFF) shl 16) or
                     ((data[6].toInt() and 0xFF) shl 8) or
                     (data[7].toInt() and 0xFF)
    if (payloadLen < 0 || payloadLen != data.size - HEADER_LEN)
        throw IllegalStateException("payload length mismatch: declared $payloadLen, available ${data.size - HEADER_LEN}")
    return Frame(msgType, data.copyOfRange(HEADER_LEN, HEADER_LEN + payloadLen))
}

// ─── Message Types ───────────────────────────────────────────────────────

data class EchoResult(
    var idx: Long = 0,
    var type: Byte = 0,
    var kv: Map<String, String> = emptyMap()
)

class Message(val type: Byte) {
    var clientLanguage: String = ""
    var serverLanguage: String = ""
    var echoId: Long = 0; var echoMeta: String = ""; var echoData: String = ""
    var echoStatus: Int = 0; var echoResults: List<EchoResult> = emptyList()
    var osName: String = ""; var osVersion: String = ""; var osRelease: String = ""; var osArch: String = ""
    var kissLanguage: String = ""; var kissEncoding: String = ""; var kissTimeZone: String = ""
    var timestampMs: Long = 0; var iso8601: String = ""
    var randomId: Long = 0; var randomNumber: Long = 0
    var hashHex: String = ""
    var disconnectReason: String = ""
    var errorCode: Int = 0; var errorMessage: String = ""

    fun encode(): ByteArray {
        val w = ByteWriter()
        return when (type) {
            MSG_HELLO -> { w.writeString(clientLanguage); encodeFrame(MSG_HELLO, w.toBytes()) }
            MSG_BONJOUR -> { w.writeString(serverLanguage); encodeFrame(MSG_BONJOUR, w.toBytes()) }
            MSG_ECHO_REQUEST -> { w.writeI64(echoId); w.writeString(echoMeta); w.writeString(echoData); encodeFrame(MSG_ECHO_REQUEST, w.toBytes()) }
            MSG_ECHO_RESPONSE -> {
                w.writeI32(echoStatus); w.writeU32(echoResults.size)
                for (r in echoResults) { w.writeI64(r.idx); w.writeU8(r.type.toInt()); w.writeKV(r.kv) }
                encodeFrame(MSG_ECHO_RESPONSE, w.toBytes())
            }
            MSG_KISS_REQUEST -> { w.writeString(osName); w.writeString(osVersion); w.writeString(osRelease); w.writeString(osArch); encodeFrame(MSG_KISS_REQUEST, w.toBytes()) }
            MSG_KISS_RESPONSE -> { w.writeString(kissLanguage); w.writeString(kissEncoding); w.writeString(kissTimeZone); encodeFrame(MSG_KISS_RESPONSE, w.toBytes()) }
            MSG_PING -> { w.writeI64(timestampMs); encodeFrame(MSG_PING, w.toBytes()) }
            MSG_PONG -> { w.writeI64(timestampMs); encodeFrame(MSG_PONG, w.toBytes()) }
            MSG_TIME_NOTIFICATION -> { w.writeI64(timestampMs); w.writeString(iso8601); encodeFrame(MSG_TIME_NOTIFICATION, w.toBytes()) }
            MSG_RANDOM_NUMBER -> { w.writeI64(randomId); w.writeI64(randomNumber); encodeFrame(MSG_RANDOM_NUMBER, w.toBytes()) }
            MSG_HASH_RESPONSE -> { w.writeI64(randomId); w.writeString(hashHex); encodeFrame(MSG_HASH_RESPONSE, w.toBytes()) }
            MSG_DISCONNECT -> { w.writeString(disconnectReason); encodeFrame(MSG_DISCONNECT, w.toBytes()) }
            MSG_ERROR -> { w.writeI32(errorCode); w.writeString(errorMessage); encodeFrame(MSG_ERROR, w.toBytes()) }
            else -> throw IllegalArgumentException("unknown message type: 0x${type.toInt().toString(16)}")
        }
    }
}

fun decodeMessage(data: ByteArray): Message {
    val frame = decodeFrame(data)
    val r = ByteReader(frame.payload)
    val m = Message(frame.msgType)
    when (frame.msgType) {
        MSG_HELLO -> m.clientLanguage = r.readString()
        MSG_BONJOUR -> m.serverLanguage = r.readString()
        MSG_ECHO_REQUEST -> { m.echoId = r.readI64(); m.echoMeta = r.readString(); m.echoData = r.readString() }
        MSG_ECHO_RESPONSE -> {
            m.echoStatus = r.readI32()
            val count = r.readU32()
            if (count < 0 || count > r.remaining() / 13) throw IllegalStateException("result count $count exceeds remaining payload")
            val results = ArrayList<EchoResult>(count)
            for (i in 0 until count) {
                val er = EchoResult()
                er.idx = r.readI64()
                er.type = r.readU8().toByte()
                er.kv = r.readKV()
                results.add(er)
            }
            m.echoResults = results
        }
        MSG_KISS_REQUEST -> { m.osName = r.readString(); m.osVersion = r.readString(); m.osRelease = r.readString(); m.osArch = r.readString() }
        MSG_KISS_RESPONSE -> { m.kissLanguage = r.readString(); m.kissEncoding = r.readString(); m.kissTimeZone = r.readString() }
        MSG_PING -> m.timestampMs = r.readI64()
        MSG_PONG -> m.timestampMs = r.readI64()
        MSG_TIME_NOTIFICATION -> { m.timestampMs = r.readI64(); m.iso8601 = r.readString() }
        MSG_RANDOM_NUMBER -> { m.randomId = r.readI64(); m.randomNumber = r.readI64() }
        MSG_HASH_RESPONSE -> { m.randomId = r.readI64(); m.hashHex = r.readString() }
        MSG_DISCONNECT -> m.disconnectReason = r.readString()
        MSG_ERROR -> { m.errorCode = r.readI32(); m.errorMessage = r.readString() }
        else -> throw IllegalStateException("unknown message type: 0x${frame.msgType.toInt().toString(16)}")
    }
    return m
}

// ─── Utility ─────────────────────────────────────────────────────────────

fun nowMs(): Long = System.currentTimeMillis()

fun nowISO(): String {
    val now = Instant.now().atOffset(ZoneOffset.UTC)
    return DateTimeFormatter.ISO_INSTANT.format(now).let {
        // Truncate to seconds: "2026-07-03T14:22:01.123Z" -> "2026-07-03T14:22:01Z"
        val dotIdx = it.indexOf('.')
        if (dotIdx >= 0) it.substring(0, dotIdx) + "Z" else it
    }
}

fun hashNumber(num: Long): String {
    val md = MessageDigest.getInstance("SHA-256")
    val hash = md.digest(num.toString().toByteArray(Charsets.US_ASCII))
    val sb = StringBuilder()
    for (i in 0 until 5) sb.append(String.format("%02x", hash[i]))
    return sb.toString()
}

fun log(name: String, msg: String) {
    val ts = Instant.now().atOffset(ZoneOffset.UTC)
    val tsStr = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(ts)
    println("[$tsStr] [INFO] [$name] $msg")
}

// ─── Factory Helpers ─────────────────────────────────────────────────────

fun hello(lang: String) = Message(MSG_HELLO).apply { clientLanguage = lang }
fun bonjour(lang: String) = Message(MSG_BONJOUR).apply { serverLanguage = lang }
fun ping(ts: Long) = Message(MSG_PING).apply { timestampMs = ts }
fun pong(ts: Long) = Message(MSG_PONG).apply { timestampMs = ts }
fun timeNotif(ts: Long, iso: String) = Message(MSG_TIME_NOTIFICATION).apply { timestampMs = ts; iso8601 = iso }
fun kissRequest(os: String, ver: String, rel: String, arch: String) =
    Message(MSG_KISS_REQUEST).apply { osName = os; osVersion = ver; osRelease = rel; osArch = arch }
fun kissResponse(lang: String, enc: String, tz: String) =
    Message(MSG_KISS_RESPONSE).apply { kissLanguage = lang; kissEncoding = enc; kissTimeZone = tz }
fun randomNumberMsg(id: Long, num: Long) = Message(MSG_RANDOM_NUMBER).apply { randomId = id; randomNumber = num }
fun hashResponseMsg(id: Long, hash: String) = Message(MSG_HASH_RESPONSE).apply { randomId = id; hashHex = hash }
fun disconnectMsg(reason: String) = Message(MSG_DISCONNECT).apply { disconnectReason = reason }
fun errorMsg(code: Int, msg: String) = Message(MSG_ERROR).apply { errorCode = code; errorMessage = msg }
