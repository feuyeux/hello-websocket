import Foundation

// MARK: - WebSocket Frame Helpers

/// Encodes a WebSocket binary frame (opcode 0x02).
/// Server-side: no masking. Client-side: with masking.
public func wsEncodeBinaryFrame(_ data: [UInt8], masked: Bool) -> [UInt8] {
    var frame: [UInt8] = []
    // Byte 0: FIN=1, RSV=0, opcode=0x02 (binary)
    frame.append(0x82)

    let len = data.count
    let maskBit: UInt8 = masked ? 0x80 : 0x00

    if len <= 125 {
        frame.append(maskBit | UInt8(len))
    } else if len <= 65535 {
        frame.append(maskBit | 126)
        frame.append(UInt8((len >> 8) & 0xFF))
        frame.append(UInt8(len & 0xFF))
    } else {
        frame.append(maskBit | 127)
        for i in (0..<8).reversed() {
            frame.append(UInt8((UInt64(len) >> (i * 8)) & 0xFF))
        }
    }

    if masked {
        // Generate 4-byte masking key
        var rng = SystemRandomNumberGenerator()
        let maskKey: [UInt8] = (0..<4).map { _ in UInt8.random(in: 0...255, using: &rng) }
        frame.append(contentsOf: maskKey)
        // XOR payload with mask key
        var maskedData = data
        for i in 0..<maskedData.count {
            maskedData[i] ^= maskKey[i % 4]
        }
        frame.append(contentsOf: maskedData)
    } else {
        frame.append(contentsOf: data)
    }

    return frame
}

/// Reads a complete WebSocket frame from a TCP socket.
/// Returns the payload bytes (unmasked if masked).
public func wsReadFrame(_ socket: TCPSocket) throws -> [UInt8] {
    // Read first 2 bytes
    let header = try recvExact(socket, 2)
    guard header.count >= 2 else {
        throw SocketError.upgradeFailed("WebSocket frame too short")
    }

    let opcode = header[0] & 0x0F
    let masked = (header[1] & 0x80) != 0
    var payloadLen = Int(header[1] & 0x7F)

    // Extended payload length
    if payloadLen == 126 {
        let ext = try recvExact(socket, 2)
        payloadLen = (Int(ext[0]) << 8) | Int(ext[1])
    } else if payloadLen == 127 {
        let ext = try recvExact(socket, 8)
        payloadLen = 0
        for i in 0..<8 {
            payloadLen = (payloadLen << 8) | Int(ext[i])
        }
    }

    // Masking key
    var maskKey: [UInt8] = []
    if masked {
        maskKey = try recvExact(socket, 4)
    }

    // Payload
    var payload: [UInt8] = []
    if payloadLen > 0 {
        payload = try recvExact(socket, payloadLen)
    }

    // Unmask if needed
    if masked {
        for i in 0..<payload.count {
            payload[i] ^= maskKey[i % 4]
        }
    }

    // Handle control frames
    if opcode == 0x08 {
        // Close frame
        throw SocketError.connectionClosed
    }

    return payload
}

/// Reads exactly `count` bytes from a TCP socket, handling partial reads.
private func recvExact(_ socket: TCPSocket, _ count: Int) throws -> [UInt8] {
    var result: [UInt8] = []
    while result.count < count {
        let remaining = count - result.count
        let chunk = try socket.recvBytes(maxLen: remaining)
        if chunk.isEmpty {
            throw SocketError.connectionClosed
        }
        result.append(contentsOf: chunk)
    }
    return result
}

// MARK: - WebSocket Server Upgrade

/// Handles the server-side WebSocket upgrade: reads HTTP request, sends 101 response.
public func wsServerUpgrade(_ socket: TCPSocket) throws -> String {
    let request = try socket.recvHTTP()

    // Extract Sec-WebSocket-Key
    var wsKey = ""
    for line in request.components(separatedBy: "\r\n") {
        if line.lowercased().hasPrefix("sec-websocket-key:") {
            let colon = line.firstIndex(of: ":")!
            wsKey = String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
        }
    }
    if wsKey.isEmpty {
        throw SocketError.upgradeFailed("No Sec-WebSocket-Key in request")
    }

    // Compute Sec-WebSocket-Accept
    let accept = computeWebSocketAccept(wsKey)

    // Send 101 response
    let response = "HTTP/1.1 101 Switching Protocols\r\n" +
                   "Upgrade: websocket\r\n" +
                   "Connection: Upgrade\r\n" +
                   "Sec-WebSocket-Accept: \(accept)\r\n\r\n"
    _ = try socket.sendBytes(Array(response.utf8))

    // Extract userId header
    var userId = ""
    for line in request.components(separatedBy: "\r\n") {
        if line.lowercased().hasPrefix("userid:") {
            let colon = line.firstIndex(of: ":")!
            userId = String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespaces)
        }
    }
    return userId
}

// MARK: - WebSocket Client Upgrade

/// Handles the client-side WebSocket upgrade: sends HTTP request, receives 101 response.
public func wsClientUpgrade(_ socket: TCPSocket, host: String, port: Int, userId: String) throws {
    // Generate random Sec-WebSocket-Key (16 bytes, base64)
    var rng = SystemRandomNumberGenerator()
    var keyBytes = [UInt8](repeating: 0, count: 16)
    for i in 0..<16 {
        keyBytes[i] = UInt8.random(in: 0...255, using: &rng)
    }
    let wsKey = base64Encode(keyBytes)

    // Send upgrade request
    let request = "GET / HTTP/1.1\r\n" +
                  "Host: \(host):\(port)\r\n" +
                  "Upgrade: websocket\r\n" +
                  "Connection: Upgrade\r\n" +
                  "Sec-WebSocket-Key: \(wsKey)\r\n" +
                  "Sec-WebSocket-Version: 13\r\n" +
                  "userId: \(userId)\r\n" +
                  "\r\n"
    _ = try socket.sendBytes(Array(request.utf8))

    // Receive response
    let response = try socket.recvHTTP()

    // Verify 101 response
    if !response.contains("101") {
        throw SocketError.upgradeFailed("Expected 101 response, got: \(response.prefix(50))")
    }
}
