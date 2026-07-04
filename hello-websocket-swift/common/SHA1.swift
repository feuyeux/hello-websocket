import Foundation

// SHA-1 implementation for WebSocket handshake (RFC 6455).

struct SHA1 {
    static func hash(_ data: [UInt8]) -> [UInt8] {
        var h: [UInt32] = [0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0]

        var msg = data
        let bitLen = UInt64(msg.count) * 8

        msg.append(0x80)
        while msg.count % 64 != 56 {
            msg.append(0)
        }
        for i in (0..<8).reversed() {
            msg.append(UInt8((bitLen >> (i * 8)) & 0xFF))
        }

        for blockStart in stride(from: 0, to: msg.count, by: 64) {
            var w = [UInt32](repeating: 0, count: 80)
            for i in 0..<16 {
                let b0 = UInt32(msg[blockStart + i * 4])
                let b1 = UInt32(msg[blockStart + i * 4 + 1])
                let b2 = UInt32(msg[blockStart + i * 4 + 2])
                let b3 = UInt32(msg[blockStart + i * 4 + 3])
                w[i] = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3
            }
            for i in 16..<80 {
                w[i] = rotl(w[i-3] ^ w[i-8] ^ w[i-14] ^ w[i-16], 1)
            }

            var a = h[0], b = h[1], c = h[2], d = h[3], e = h[4]

            for i in 0..<80 {
                let f: UInt32
                let k: UInt32
                switch i {
                case 0...19:
                    f = (b & c) | (~b & d)
                    k = 0x5A827999
                case 20...39:
                    f = b ^ c ^ d
                    k = 0x6ED9EBA1
                case 40...59:
                    f = (b & c) | (b & d) | (c & d)
                    k = 0x8F1BBCDC
                default:
                    f = b ^ c ^ d
                    k = 0xCA62C1D6
                }
                let temp = rotl(a, 5) &+ f &+ e &+ k &+ w[i]
                e = d; d = c; c = rotl(b, 30); b = a; a = temp
            }

            h[0] = h[0] &+ a
            h[1] = h[1] &+ b
            h[2] = h[2] &+ c
            h[3] = h[3] &+ d
            h[4] = h[4] &+ e
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

    private static func rotl(_ x: UInt32, _ n: UInt32) -> UInt32 {
        return (x << n) | (x >> (32 - n))
    }
}

func base64Encode(_ data: [UInt8]) -> String {
    let chars: [Character] = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")
    var result = ""
    var i = 0
    while i + 2 < data.count {
        let n = (UInt32(data[i]) << 16) | (UInt32(data[i+1]) << 8) | UInt32(data[i+2])
        result.append(chars[Int((n >> 18) & 0x3F)])
        result.append(chars[Int((n >> 12) & 0x3F)])
        result.append(chars[Int((n >> 6) & 0x3F)])
        result.append(chars[Int(n & 0x3F)])
        i += 3
    }
    if i < data.count {
        let remaining = data.count - i
        var n = UInt32(data[i]) << 16
        if remaining > 1 { n |= UInt32(data[i+1]) << 8 }
        result.append(chars[Int((n >> 18) & 0x3F)])
        result.append(chars[Int((n >> 12) & 0x3F)])
        if remaining > 1 {
            result.append(chars[Int((n >> 6) & 0x3F)])
            result.append("=")
        } else {
            result.append("=")
            result.append("=")
        }
    }
    return result
}

func computeWebSocketAccept(_ key: String) -> String {
    let magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    let combined = key + magic
    let hash = SHA1.hash(Array(combined.utf8))
    return base64Encode(hash)
}
