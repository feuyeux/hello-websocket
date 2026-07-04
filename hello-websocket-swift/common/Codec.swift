// Hello WebSocket Protocol Codec - Swift implementation.
//
// Implements the canonical binary protocol defined in PROTOCOL.md.
// Provides constants, primitive encoders/decoders, message types, frame codec,
// SHA-256 hash, and message dispatch for all 13 message types.

import Foundation
import ucrt

// MARK: - SHA-256 Implementation

struct SHA256 {
    private static let k: [UInt32] = [
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    ]

    static func hash(_ data: [UInt8]) -> [UInt8] {
        var h: [UInt32] = [
            0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
            0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
        ]

        var msg = data
        let bitLen = UInt64(msg.count) * 8

        // Padding
        msg.append(0x80)
        while msg.count % 64 != 56 {
            msg.append(0)
        }
        for i in (0..<8).reversed() {
            msg.append(UInt8((bitLen >> (i * 8)) & 0xFF))
        }

        // Process each 512-bit block
        for blockStart in stride(from: 0, to: msg.count, by: 64) {
            var w = [UInt32](repeating: 0, count: 64)
            for i in 0..<16 {
                let b0 = UInt32(msg[blockStart + i * 4])
                let b1 = UInt32(msg[blockStart + i * 4 + 1])
                let b2 = UInt32(msg[blockStart + i * 4 + 2])
                let b3 = UInt32(msg[blockStart + i * 4 + 3])
                w[i] = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3
            }
            for i in 16..<64 {
                let s0 = rotr(w[i-15], 7) ^ rotr(w[i-15], 18) ^ (w[i-15] >> 3)
                let s1 = rotr(w[i-2], 17) ^ rotr(w[i-2], 19) ^ (w[i-2] >> 10)
                w[i] = w[i-16] &+ s0 &+ w[i-7] &+ s1
            }

            var a = h[0], b = h[1], c = h[2], d = h[3]
            var e = h[4], f = h[5], g = h[6], hh = h[7]

            for i in 0..<64 {
                let s1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25)
                let ch = (e & f) ^ (~e & g)
                let t1 = hh &+ s1 &+ ch &+ k[i] &+ w[i]
                let s0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22)
                let maj = (a & b) ^ (a & c) ^ (b & c)
                let t2 = s0 &+ maj
                hh = g; g = f; f = e; e = d &+ t1
                d = c; c = b; b = a; a = t1 &+ t2
            }

            h[0] = h[0] &+ a; h[1] = h[1] &+ b; h[2] = h[2] &+ c; h[3] = h[3] &+ d
            h[4] = h[4] &+ e; h[5] = h[5] &+ f; h[6] = h[6] &+ g; h[7] = h[7] &+ hh
        }

        var result = [UInt8]()
        for v in h {
            result.append(UInt8((v >> 24) & 0xFF))
            result.append(UInt8((v >> 16) & 0xFF))
            result.append(UInt8((v >> 8) & 0xFF))
            result.append(UInt8(v & 0xFF))
        }
        return result
    }

    private static func rotr(_ x: UInt32, _ n: UInt32) -> UInt32 {
        return (x >> n) | (x << (32 - n))
    }
}

// MARK: - Constants

public enum WsProtocol {
    public static let port: Int = 9898
    public static let magic: UInt8 = 0x48
    public static let version: UInt8 = 0x01
    public static let headerLen: Int = 8
    public static let serverLang = "SWIFT"
    public static let clientLang = "SWIFT"
}

// Message types
public enum MsgType: UInt8 {
    case hello = 0x01
    case bonjour = 0x02
    case echoRequest = 0x03
    case echoResponse = 0x04
    case kissRequest = 0x05
    case kissResponse = 0x06
    case ping = 0x07
    case pong = 0x08
    case timeNotification = 0x09
    case randomNumber = 0x0A
    case hashResponse = 0x0B
    case disconnect = 0x0C
    case error = 0x7F
}

// Error codes
public enum ErrCode: Int32 {
    case decode = 0x01
    case unknownMsgType = 0x02
    case truncatedPayload = 0x03
    case badMagic = 0x04
    case badVersion = 0x05
    case sessionNotFound = 0x06
    case internal_ = 0x07
}

// MARK: - Byte Writer

public final class ByteWriter {
    private var buf: [UInt8] = []

    public init() {}

    public func writeU8(_ v: UInt8) { buf.append(v) }
    public func writeU16(_ v: UInt16) {
        buf.append(UInt8((v >> 8) & 0xFF))
        buf.append(UInt8(v & 0xFF))
    }
    public func writeU32(_ v: UInt32) {
        buf.append(UInt8((v >> 24) & 0xFF))
        buf.append(UInt8((v >> 16) & 0xFF))
        buf.append(UInt8((v >> 8) & 0xFF))
        buf.append(UInt8(v & 0xFF))
    }
    public func writeI32(_ v: Int32) { writeU32(UInt32(bitPattern: v)) }
    public func writeI64(_ v: Int64) { writeU64(UInt64(bitPattern: v)) }
    public func writeU64(_ v: UInt64) {
        for i in (0..<8).reversed() {
            buf.append(UInt8((v >> (i * 8)) & 0xFF))
        }
    }
    public func writeString(_ s: String) {
        let b = Array(s.utf8)
        writeU32(UInt32(b.count))
        buf.append(contentsOf: b)
    }
    public func writeKv(_ m: [(String, String)]) {
        writeU32(UInt32(m.count))
        for (k, v) in m {
            writeString(k)
            writeString(v)
        }
    }
    public func data() -> [UInt8] { return buf }
}

// MARK: - Byte Reader

public final class ByteReader {
    private let data: [UInt8]
    private var pos: Int = 0

    public init(_ data: [UInt8]) { self.data = data }
    public init(_ data: Data) { self.data = Array(data) }

    public func readU8() throws -> UInt8 {
        guard pos + 1 <= data.count else { throw CodecError.eof("u8 at \(pos)") }
        let v = data[pos]; pos += 1; return v
    }
    public func readU16() throws -> UInt16 {
        guard pos + 2 <= data.count else { throw CodecError.eof("u16 at \(pos)") }
        let v = (UInt16(data[pos]) << 8) | UInt16(data[pos+1])
        pos += 2; return v
    }
    public func readU32() throws -> UInt32 {
        guard pos + 4 <= data.count else { throw CodecError.eof("u32 at \(pos)") }
        var v: UInt32 = 0
        for i in 0..<4 { v = (v << 8) | UInt32(data[pos+i]) }
        pos += 4; return v
    }
    public func readI32() throws -> Int32 { Int32(bitPattern: try readU32()) }
    public func readI64() throws -> Int64 { Int64(bitPattern: try readU64()) }
    public func readU64() throws -> UInt64 {
        guard pos + 8 <= data.count else { throw CodecError.eof("i64 at \(pos)") }
        var v: UInt64 = 0
        for i in 0..<8 { v = (v << 8) | UInt64(data[pos+i]) }
        pos += 8; return v
    }
    public func readString() throws -> String {
        let ln = Int(try readU32())
        guard pos + ln <= data.count else { throw CodecError.eof("string at \(pos), len=\(ln)") }
        let s = String(decoding: data[pos..<pos+ln], as: UTF8.self)
        pos += ln; return s
    }
    public func readKv() throws -> [(String, String)] {
        let count = Int(try readU32())
        var m: [(String, String)] = []
        for _ in 0..<count {
            let k = try readString()
            let v = try readString()
            m.append((k, v))
        }
        return m
    }
}

// MARK: - Errors

public enum CodecError: Error, CustomStringConvertible {
    case eof(String)
    case frameTooShort(Int)
    case badMagic(UInt8)
    case badVersion(UInt8)
    case truncatedPayload(declared: Int, available: Int)
    case unknownMsgType(UInt8)

    public var description: String {
        switch self {
        case .eof(let s): return "unexpected end of data reading \(s)"
        case .frameTooShort(let n): return "frame too short: \(n) bytes"
        case .badMagic(let v): return String(format: "bad magic: 0x%02x", v)
        case .badVersion(let v): return String(format: "bad version: 0x%02x", v)
        case .truncatedPayload(let d, let a): return "truncated payload: declared \(d), available \(a)"
        case .unknownMsgType(let v): return String(format: "unknown message type: 0x%02x", v)
        }
    }
}

// MARK: - Frame Codec

public func encodeFrame(_ msgType: MsgType, payload: [UInt8]) -> [UInt8] {
    var buf = [UInt8]()
    buf.append(WsProtocol.magic)
    buf.append(WsProtocol.version)
    buf.append(msgType.rawValue)
    buf.append(0x00) // flags
    let len = UInt32(payload.count)
    buf.append(UInt8((len >> 24) & 0xFF))
    buf.append(UInt8((len >> 16) & 0xFF))
    buf.append(UInt8((len >> 8) & 0xFF))
    buf.append(UInt8(len & 0xFF))
    buf.append(contentsOf: payload)
    return buf
}

public func decodeFrame(_ data: [UInt8]) throws -> (MsgType, [UInt8]) {
    guard data.count >= WsProtocol.headerLen else {
        throw CodecError.frameTooShort(data.count)
    }
    guard data[0] == WsProtocol.magic else {
        throw CodecError.badMagic(data[0])
    }
    guard data[1] == WsProtocol.version else {
        throw CodecError.badVersion(data[1])
    }
    guard let msgType = MsgType(rawValue: data[2]) else {
        throw CodecError.unknownMsgType(data[2])
    }
    let payloadLen = (UInt32(data[4]) << 24) | (UInt32(data[5]) << 16) | (UInt32(data[6]) << 8) | UInt32(data[7])
    let avail = data.count - WsProtocol.headerLen
    guard Int(payloadLen) <= avail else {
        throw CodecError.truncatedPayload(declared: Int(payloadLen), available: avail)
    }
    let payload = Array(data[WsProtocol.headerLen..<WsProtocol.headerLen+Int(payloadLen)])
    return (msgType, payload)
}

// MARK: - Message

public struct WsMessage {
    public var type: MsgType
    public var clientLanguage: String?
    public var serverLanguage: String?
    public var echoId: Int64?
    public var echoMeta: String?
    public var echoData: String?
    public var echoStatus: Int32?
    public var echoResults: [(idx: Int64, type: UInt8, kv: [(String, String)])]?
    public var osName: String?
    public var osVersion: String?
    public var osRelease: String?
    public var osArch: String?
    public var kissLanguage: String?
    public var kissEncoding: String?
    public var kissTimeZone: String?
    public var timestampMs: Int64?
    public var iso8601: String?
    public var randomId: Int64?
    public var randomNumber: Int64?
    public var hashHex: String?
    public var disconnectReason: String?
    public var errorCode: Int32?
    public var errorMessage: String?

    public init(type: MsgType) { self.type = type }
}

public func decodeMessage(_ data: [UInt8]) throws -> WsMessage {
    let (msgType, payload) = try decodeFrame(data)
    let r = ByteReader(payload)
    var msg = WsMessage(type: msgType)

    switch msgType {
    case .hello:
        msg.clientLanguage = try r.readString()
    case .bonjour:
        msg.serverLanguage = try r.readString()
    case .echoRequest:
        msg.echoId = try r.readI64()
        msg.echoMeta = try r.readString()
        msg.echoData = try r.readString()
    case .echoResponse:
        msg.echoStatus = try r.readI32()
        let count = Int(try r.readU32())
        var results: [(idx: Int64, type: UInt8, kv: [(String, String)])] = []
        for _ in 0..<count {
            let idx = try r.readI64()
            let typ = try r.readU8()
            let kv = try r.readKv()
            results.append((idx, typ, kv))
        }
        msg.echoResults = results
    case .kissRequest:
        msg.osName = try r.readString()
        msg.osVersion = try r.readString()
        msg.osRelease = try r.readString()
        msg.osArch = try r.readString()
    case .kissResponse:
        msg.kissLanguage = try r.readString()
        msg.kissEncoding = try r.readString()
        msg.kissTimeZone = try r.readString()
    case .ping:
        msg.timestampMs = try r.readI64()
    case .pong:
        msg.timestampMs = try r.readI64()
    case .timeNotification:
        msg.timestampMs = try r.readI64()
        msg.iso8601 = try r.readString()
    case .randomNumber:
        msg.randomId = try r.readI64()
        msg.randomNumber = try r.readI64()
    case .hashResponse:
        msg.randomId = try r.readI64()
        msg.hashHex = try r.readString()
    case .disconnect:
        msg.disconnectReason = try r.readString()
    case .error:
        msg.errorCode = try r.readI32()
        msg.errorMessage = try r.readString()
    }

    return msg
}

// MARK: - Utility

public func nowMs() -> Int64 {
    return Int64(Date().timeIntervalSince1970 * 1000)
}

public func nowISO() -> String {
    let ts = Date()
    let secs = Int64(ts.timeIntervalSince1970)
    let dayNum = Int(secs / 86400)
    let rem = secs % 86400
    let h = Int(rem / 3600)
    let m = Int((rem / 60) % 60)
    let s = Int(rem % 60)
    var y = 1970
    var d = dayNum
    while true {
        let leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
        let yearDays = leap ? 366 : 365
        if d < yearDays { break }
        d -= yearDays
        y += 1
    }
    let leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    let monthDays = [31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    var month = 1
    for md in monthDays {
        if d < md { break }
        d -= md
        month += 1
    }
    return String(format: "%04d-%02d-%02dT%02d:%02d:%02dZ", y, month, d + 1, h, m, s)
}

public func hashNumber(_ num: Int64) -> String {
    let s = String(num)
    let bytes = Array(s.utf8)
    let fullHash = SHA256.hash(bytes)
    // First 5 bytes = 10 hex chars
    let prefix = fullHash.prefix(5)
    return prefix.map { String(format: "%02x", $0) }.joined()
}

public func logMsg(_ name: String, _ msg: String) {
    let ts = Date()
    let secs = Int64(ts.timeIntervalSince1970)
    let h = (secs / 3600) % 24
    let m = (secs / 60) % 60
    let s = secs % 60
    let dateStr = String(format: "%02d:%02d:%02d", h, m, s)
    print("[\(dateStr)] [INFO] [\(name)] \(msg)")
    fflush(stdout)
}
