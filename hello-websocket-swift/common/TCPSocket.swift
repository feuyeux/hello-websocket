import Foundation
import ucrt
import WinSDK

// MARK: - Socket Errors

public enum SocketError: Error, CustomStringConvertible {
    case socketFailed(Int32)
    case bindFailed(Int32)
    case listenFailed(Int32)
    case connectFailed(Int32)
    case acceptFailed(Int32)
    case sendFailed(Int32)
    case recvFailed(Int32)
    case connectionClosed
    case upgradeFailed(String)

    public var description: String {
        switch self {
        case .socketFailed(let c): return "socket() failed: \(c)"
        case .bindFailed(let c): return "bind() failed: \(c)"
        case .listenFailed(let c): return "listen() failed: \(c)"
        case .connectFailed(let c): return "connect() failed: \(c)"
        case .acceptFailed(let c): return "accept() failed: \(c)"
        case .sendFailed(let c): return "send() failed: \(c)"
        case .recvFailed(let c): return "recv() failed: \(c)"
        case .connectionClosed: return "connection closed"
        case .upgradeFailed(let m): return "upgrade failed: \(m)"
        }
    }
}

// MARK: - TCP Socket

public final class TCPSocket: @unchecked Sendable {
    private var fd: SOCKET
    private var closed = false
    private static let wsaInit: Void = {
        var data = WSADATA()
        _ = WSAStartup(WORD(0x0202), &data)
    }()

    private init(fd: SOCKET) {
        self.fd = fd
    }

    deinit {
        close()
    }

    // MARK: - Server

    public static func createServer(port: Int) throws -> TCPSocket {
        _ = wsaInit
        let s = socket(AF_INET, SOCK_STREAM, Int32(IPPROTO_TCP.rawValue))
        if s == INVALID_SOCKET {
            throw SocketError.socketFailed(WSAGetLastError())
        }

        var optval: Int32 = 1
        _ = setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &optval, Int32(MemoryLayout<Int32>.size))

        var addr = sockaddr_in()
        addr.sin_family = ADDRESS_FAMILY(AF_INET)
        addr.sin_port = htons(UInt16(port))
        // sin_addr is zero-initialized by default = INADDR_ANY

        let rc = withUnsafePointer(to: &addr) { ptr -> Int32 in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saddr in
                WinSDK.bind(s, saddr, Int32(MemoryLayout<sockaddr_in>.size))
            }
        }
        if rc == SOCKET_ERROR {
            _ = closesocket(s)
            throw SocketError.bindFailed(WSAGetLastError())
        }

        if WinSDK.listen(s, 5) == SOCKET_ERROR {
            _ = closesocket(s)
            throw SocketError.listenFailed(WSAGetLastError())
        }
        return TCPSocket(fd: s)
    }

    public func accept() throws -> TCPSocket {
        var addr = sockaddr_in()
        var addrLen = Int32(MemoryLayout<sockaddr_in>.size)
        let clientFd = withUnsafeMutablePointer(to: &addr) { ptr -> SOCKET in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saddr in
                WinSDK.accept(fd, saddr, &addrLen)
            }
        }
        if clientFd == INVALID_SOCKET {
            throw SocketError.acceptFailed(WSAGetLastError())
        }
        return TCPSocket(fd: clientFd)
    }

    // MARK: - Client

    public static func createClient(host: String, port: Int) throws -> TCPSocket {
        _ = wsaInit
        let s = socket(AF_INET, SOCK_STREAM, Int32(IPPROTO_TCP.rawValue))
        if s == INVALID_SOCKET {
            throw SocketError.socketFailed(WSAGetLastError())
        }

        var addr = sockaddr_in()
        addr.sin_family = ADDRESS_FAMILY(AF_INET)
        addr.sin_port = htons(UInt16(port))

        // Parse host IP using inet_pton (writes directly into sin_addr)
        let rc = host.withCString { hostPtr -> Int32 in
            withUnsafeMutablePointer(to: &addr.sin_addr) { ipPtr in
                inet_pton(AF_INET, hostPtr, ipPtr)
            }
        }
        if rc != 1 {
            _ = closesocket(s)
            throw SocketError.connectFailed(WSAGetLastError())
        }

        let connRc = withUnsafePointer(to: &addr) { ptr -> Int32 in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saddr in
                WinSDK.connect(s, saddr, Int32(MemoryLayout<sockaddr_in>.size))
            }
        }
        if connRc == SOCKET_ERROR {
            _ = closesocket(s)
            throw SocketError.connectFailed(WSAGetLastError())
        }
        return TCPSocket(fd: s)
    }

    // MARK: - I/O

    public func sendBytes(_ data: [UInt8]) throws -> Int {
        let count = data.count
        let sent = data.withUnsafeBufferPointer { buf -> Int32 in
            WinSDK.send(fd, buf.baseAddress, Int32(count), 0)
        }
        if sent == SOCKET_ERROR {
            throw SocketError.sendFailed(WSAGetLastError())
        }
        return Int(sent)
    }

    public func recvBytes(maxLen: Int = 65536) throws -> [UInt8] {
        var buf = [UInt8](repeating: 0, count: maxLen)
        let received = buf.withUnsafeMutableBufferPointer { ptr -> Int32 in
            WinSDK.recv(fd, ptr.baseAddress, Int32(maxLen), 0)
        }
        if received == SOCKET_ERROR {
            throw SocketError.recvFailed(WSAGetLastError())
        }
        if received == 0 {
            throw SocketError.connectionClosed
        }
        return Array(buf[0..<Int(received)])
    }

    // MARK: - Close

    public func close() {
        if !closed {
            closed = true
            _ = closesocket(fd)
        }
    }

    // MARK: - Raw HTTP receive (for WebSocket upgrade)

    public func recvHTTP(maxLen: Int = 8192) throws -> String {
        var buf = [UInt8](repeating: 0, count: maxLen)
        var total = 0

        while total < maxLen - 1 {
            let received = buf.withUnsafeMutableBufferPointer { ptr -> Int32 in
                WinSDK.recv(fd, ptr.baseAddress! + total, Int32(maxLen - total), 0)
            }
            if received == SOCKET_ERROR {
                throw SocketError.recvFailed(WSAGetLastError())
            }
            if received == 0 {
                throw SocketError.connectionClosed
            }
            total += Int(received)

            if total >= 4 {
                let s = String(decoding: buf[0..<total], as: UTF8.self)
                if s.range(of: "\r\n\r\n") != nil {
                    return s
                }
            }
        }
        throw SocketError.upgradeFailed("HTTP headers not complete")
    }
}
