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
public func wsReadFrame(_ socket: TCPSocket, expectMasked: Bool) throws -> [UInt8] {
    let maxMessageSize = 1024 * 1024
    var complete: [UInt8] = []
    var fragmented = false

    while true {
        let header = try recvExact(socket, 2)
        let fin = (header[0] & 0x80) != 0
        guard (header[0] & 0x70) == 0 else { throw SocketError.upgradeFailed("RSV bits are unsupported") }
        let opcode = header[0] & 0x0F
        let masked = (header[1] & 0x80) != 0
        guard masked == expectMasked else { throw SocketError.upgradeFailed("invalid WebSocket masking direction") }

        var payloadLength = UInt64(header[1] & 0x7F)
        if payloadLength == 126 {
            let ext = try recvExact(socket, 2)
            payloadLength = (UInt64(ext[0]) << 8) | UInt64(ext[1])
        } else if payloadLength == 127 {
            let ext = try recvExact(socket, 8)
            guard (ext[0] & 0x80) == 0 else { throw SocketError.upgradeFailed("invalid 64-bit payload length") }
            payloadLength = ext.reduce(0) { ($0 << 8) | UInt64($1) }
        }

        let isControl = opcode >= 0x08
        guard (!isControl || (fin && payloadLength <= 125)),
              payloadLength <= UInt64(maxMessageSize),
              UInt64(complete.count) + payloadLength <= UInt64(maxMessageSize) else {
            throw SocketError.upgradeFailed("WebSocket message exceeds protocol limits")
        }

        let maskKey = masked ? try recvExact(socket, 4) : []
        var payload = payloadLength == 0 ? [] : try recvExact(socket, Int(payloadLength))
        if masked {
            for index in payload.indices { payload[index] ^= maskKey[index % 4] }
        }

        switch opcode {
        case 0x08:
            let close = wsEncodeControlFrame(opcode: 0x08, payload: payload, masked: !expectMasked)
            _ = try? socket.sendBytes(close)
            throw SocketError.connectionClosed
        case 0x09:
            _ = try socket.sendBytes(wsEncodeControlFrame(opcode: 0x0A, payload: payload, masked: !expectMasked))
            continue
        case 0x0A:
            continue
        case 0x02 where !fragmented:
            complete = payload
            if fin { return complete }
            fragmented = true
        case 0x00 where fragmented:
            complete.append(contentsOf: payload)
            if fin { return complete }
        default:
            throw SocketError.upgradeFailed("unsupported WebSocket opcode \(opcode)")
        }
    }
}

private func wsEncodeControlFrame(opcode: UInt8, payload: [UInt8], masked: Bool) -> [UInt8] {
    precondition(payload.count <= 125)
    var frame: [UInt8] = [0x80 | opcode, (masked ? 0x80 : 0x00) | UInt8(payload.count)]
    if !masked { frame.append(contentsOf: payload); return frame }
    var rng = SystemRandomNumberGenerator()
    let key = (0..<4).map { _ in UInt8.random(in: 0...255, using: &rng) }
    frame.append(contentsOf: key)
    for index in payload.indices { frame.append(payload[index] ^ key[index % 4]) }
    return frame
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
    let requestLine = request.components(separatedBy: "\r\n").first ?? ""
    let expectedPath = ProcessInfo.processInfo.environment["WS_PATH"] ?? "/ws"
    guard requestLine == "GET \(expectedPath) HTTP/1.1" else {
        throw SocketError.upgradeFailed("Unexpected WebSocket path")
    }

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
    let path = ProcessInfo.processInfo.environment["WS_PATH"] ?? "/ws"
    let request = "GET \(path) HTTP/1.1\r\n" +
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
    if !response.hasPrefix("HTTP/1.1 101 ") || !response.lowercased().contains("upgrade: websocket") ||
       !response.lowercased().contains("sec-websocket-accept: \(computeWebSocketAccept(wsKey).lowercased())") {
        throw SocketError.upgradeFailed("Expected 101 response, got: \(response.prefix(50))")
    }
}
