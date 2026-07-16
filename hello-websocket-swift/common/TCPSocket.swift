import Foundation

#if canImport(WinSDK)
import WinSDK
import ucrt
public typealias SocketFD = SOCKET
public let INVALID_SOCK_FD: SocketFD = INVALID_SOCKET
public let SOCK_ERR: Int32 = SOCKET_ERROR
public func closeSocketFD(_ fd: SocketFD) -> Int32 { closesocket(fd) }
public func lastSocketError() -> Int32 { Int32(WSAGetLastError()) }
#elseif canImport(Glibc)
import Glibc
public typealias SocketFD = Int32
public let INVALID_SOCK_FD: SocketFD = -1
public let SOCK_ERR: Int32 = -1
public func closeSocketFD(_ fd: SocketFD) -> Int32 { Glibc.close(fd) }
public func lastSocketError() -> Int32 { Int32(errno) }
#elseif canImport(Darwin)
import Darwin
public typealias SocketFD = Int32
public let INVALID_SOCK_FD: SocketFD = -1
public let SOCK_ERR: Int32 = -1
public func closeSocketFD(_ fd: SocketFD) -> Int32 { close(fd) }
public func lastSocketError() -> Int32 { errno }
#endif

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

public final class TCPSocket: @unchecked Sendable {
    private var fd: SocketFD
    private var closed = false

    #if canImport(WinSDK)
    private static let wsaInit: Void = {
        var data = WSADATA()
        _ = WSAStartup(WORD(0x0202), &data)
    }()
    #endif

    private init(fd: SocketFD) {
        self.fd = fd
    }

    deinit {
        close()
    }

    public static func createServer(port: Int) throws -> TCPSocket {
        #if canImport(WinSDK)
        _ = wsaInit
        let s = socket(AF_INET, SOCK_STREAM, Int32(IPPROTO_TCP.rawValue))
        #elseif canImport(Glibc)
        let s = Glibc.socket(AF_INET, Int32(SOCK_STREAM.rawValue), Int32(IPPROTO_TCP))
        #else
        let s = socket(AF_INET, SOCK_STREAM, Int32(IPPROTO_TCP))
        #endif
        if s == INVALID_SOCK_FD {
            throw SocketError.socketFailed(lastSocketError())
        }

        var optval: Int32 = 1
        #if canImport(WinSDK)
        _ = setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &optval, Int32(MemoryLayout<Int32>.size))
        #else
        _ = setsockopt(s, SOL_SOCKET, SO_REUSEADDR, &optval, socklen_t(MemoryLayout<Int32>.size))
        #endif

        var addr = sockaddr_in()
        #if canImport(WinSDK)
        addr.sin_family = ADDRESS_FAMILY(AF_INET)
        #else
        addr.sin_family = sa_family_t(AF_INET)
        #endif
        addr.sin_port = UInt16(port).bigEndian

        let rc = withUnsafePointer(to: &addr) { ptr -> Int32 in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saddr in
                #if canImport(WinSDK)
                return WinSDK.bind(s, saddr, Int32(MemoryLayout<sockaddr_in>.size))
                #else
                return bind(s, saddr, socklen_t(MemoryLayout<sockaddr_in>.size))
                #endif
            }
        }
        if rc == SOCK_ERR {
            _ = closeSocketFD(s)
            throw SocketError.bindFailed(lastSocketError())
        }

        let listenRc: Int32
        #if canImport(WinSDK)
        listenRc = WinSDK.listen(s, 5)
        #else
        listenRc = listen(s, 5)
        #endif
        if listenRc == SOCK_ERR {
            _ = closeSocketFD(s)
            throw SocketError.listenFailed(lastSocketError())
        }
        return TCPSocket(fd: s)
    }

    public func accept() throws -> TCPSocket {
        var addr = sockaddr_in()
        var addrLen = socklen_t(MemoryLayout<sockaddr_in>.size)
        let clientFd = withUnsafeMutablePointer(to: &addr) { ptr -> SocketFD in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saddr in
                #if canImport(WinSDK)
                return WinSDK.accept(fd, saddr, &addrLen)
                #elseif canImport(Glibc)
                return Glibc.accept(fd, saddr, &addrLen)
                #else
                return Darwin.accept(fd, saddr, &addrLen)
                #endif
            }
        }
        if clientFd == INVALID_SOCK_FD {
            throw SocketError.acceptFailed(lastSocketError())
        }
        return TCPSocket(fd: clientFd)
    }

    public static func createClient(host: String, port: Int) throws -> TCPSocket {
        #if canImport(WinSDK)
        _ = wsaInit
        let s = socket(AF_INET, SOCK_STREAM, Int32(IPPROTO_TCP.rawValue))
        #elseif canImport(Glibc)
        let s = Glibc.socket(AF_INET, Int32(SOCK_STREAM.rawValue), Int32(IPPROTO_TCP))
        #else
        let s = socket(AF_INET, SOCK_STREAM, Int32(IPPROTO_TCP))
        #endif
        if s == INVALID_SOCK_FD {
            throw SocketError.socketFailed(lastSocketError())
        }

        var addr = sockaddr_in()
        #if canImport(WinSDK)
        addr.sin_family = ADDRESS_FAMILY(AF_INET)
        #else
        addr.sin_family = sa_family_t(AF_INET)
        #endif
        addr.sin_port = UInt16(port).bigEndian

        let rc = host.withCString { hostPtr -> Int32 in
            withUnsafeMutablePointer(to: &addr.sin_addr) { ipPtr in
                inet_pton(AF_INET, hostPtr, ipPtr)
            }
        }
        if rc != 1 {
            _ = closeSocketFD(s)
            throw SocketError.connectFailed(lastSocketError())
        }

        let connRc = withUnsafePointer(to: &addr) { ptr -> Int32 in
            ptr.withMemoryRebound(to: sockaddr.self, capacity: 1) { saddr in
                #if canImport(WinSDK)
                return WinSDK.connect(s, saddr, Int32(MemoryLayout<sockaddr_in>.size))
                #else
                return connect(s, saddr, socklen_t(MemoryLayout<sockaddr_in>.size))
                #endif
            }
        }
        if connRc == SOCK_ERR {
            _ = closeSocketFD(s)
            throw SocketError.connectFailed(lastSocketError())
        }
        return TCPSocket(fd: s)
    }

    public func sendBytes(_ data: [UInt8]) throws -> Int {
        let count = data.count
        let sent = data.withUnsafeBufferPointer { buf -> Int in
            #if canImport(WinSDK)
            return Int(WinSDK.send(fd, buf.baseAddress, Int32(count), 0))
            #else
            return send(fd, buf.baseAddress, count, 0)
            #endif
        }
        if sent == Int(SOCK_ERR) {
            throw SocketError.sendFailed(lastSocketError())
        }
        return sent
    }

    public func recvBytes(maxLen: Int = 65536) throws -> [UInt8] {
        var buf = [UInt8](repeating: 0, count: maxLen)
        let received = buf.withUnsafeMutableBufferPointer { ptr -> Int in
            #if canImport(WinSDK)
            return Int(WinSDK.recv(fd, ptr.baseAddress, Int32(maxLen), 0))
            #else
            return recv(fd, ptr.baseAddress, maxLen, 0)
            #endif
        }
        if received == Int(SOCK_ERR) {
            throw SocketError.recvFailed(lastSocketError())
        }
        if received == 0 {
            throw SocketError.connectionClosed
        }
        return Array(buf[0..<received])
    }

    public func close() {
        if !closed {
            closed = true
            _ = closeSocketFD(fd)
        }
    }

    public func recvHTTP(maxLen: Int = 8192) throws -> String {
        var buf = [UInt8](repeating: 0, count: maxLen)
        var total = 0

        while total < maxLen - 1 {
            let received = buf.withUnsafeMutableBufferPointer { ptr -> Int in
                #if canImport(WinSDK)
                return Int(WinSDK.recv(fd, ptr.baseAddress! + total, Int32(maxLen - total), 0))
                #else
                return recv(fd, ptr.baseAddress! + total, maxLen - total, 0)
                #endif
            }
            if received == Int(SOCK_ERR) {
                throw SocketError.recvFailed(lastSocketError())
            }
            if received == 0 {
                throw SocketError.connectionClosed
            }
            total += received

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
