import Foundation
import HelloWebSocket

// MARK: - Client State

final class ClientState: @unchecked Sendable {
    let socket: TCPSocket
    var active = true
    var connected = false
    private let lock = NSLock()

    init(socket: TCPSocket) {
        self.socket = socket
    }

    func send(_ data: [UInt8]) {
        lock.lock()
        defer { lock.unlock() }
        guard active else { return }
        do {
            let wsFrame = wsEncodeBinaryFrame(data, masked: true)
            _ = try socket.sendBytes(wsFrame)
        } catch {
            logMsg("ws-client", "Send error: \(error)")
            active = false
        }
    }

    func sendMsg(_ msgType: MsgType, payload: [UInt8]) {
        send(encodeFrame(msgType, payload: payload))
    }

    func close() {
        lock.lock()
        defer { lock.unlock() }
        active = false
        socket.close()
    }
}

// MARK: - Main

let host = ProcessInfo.processInfo.environment["WS_SERVER"] ?? "127.0.0.1"
let port = Int(ProcessInfo.processInfo.environment["WS_PORT"] ?? "") ?? WsProtocol.port

logMsg("ws-client", "Starting Swift WebSocket client [version: 1.0.0]")
let url = "ws://\(host):\(port)"
logMsg("ws-client", "Connecting to \(url)")

func tryConnect(host: String, port: Int, attempt: Int, maxAttempts: Int) {
    logMsg("ws-client", "Connection attempt \(attempt)/\(maxAttempts) to \(url)")

    do {
        let socket = try TCPSocket.createClient(host: host, port: port)
        let userId = "swift-client-\(nowMs())"
        try wsClientUpgrade(socket, host: host, port: port, userId: userId)

        let state = ClientState(socket: socket)
        state.connected = true
        logMsg("ws-client", "Connected")

        // Send HELLO
        let w = ByteWriter()
        w.writeString(WsProtocol.clientLang)
        state.sendMsg(.hello, payload: w.data())

        // Background: RANDOM_NUMBER every 5s
        Thread {
            var id: Int64 = 1
            while state.active {
                Thread.sleep(forTimeInterval: 5.0)
                guard state.active else { return }
                let num = Int64.random(in: Int64.min...Int64.max)
                let w = ByteWriter()
                w.writeI64(id)
                w.writeI64(num)
                state.sendMsg(.randomNumber, payload: w.data())
                logMsg("ws-client", "RANDOM_NUMBER id=\(id) number=\(num)")
                id += 1
            }
        }.start()

        // Receive loop
        while state.active {
            do {
                let payload = try wsReadFrame(state.socket)
                let msg = try decodeMessage(payload)
                handleMessage(state, msg)
            } catch {
                logMsg("ws-client", "Recv error: \(error)")
                state.close()
                break
            }
        }

        if state.connected {
            logMsg("ws-client", "Disconnected")
        }
        state.connected = false

        // Retry if not too many attempts
        if attempt < maxAttempts {
            Thread.sleep(forTimeInterval: 2.0)
            tryConnect(host: host, port: port, attempt: attempt + 1, maxAttempts: maxAttempts)
        } else {
            logMsg("ws-client", "Failed to connect after \(maxAttempts) attempts")
            exit(1)
        }
    } catch {
        logMsg("ws-client", "Error: \(error)")
        if attempt < maxAttempts {
            Thread.sleep(forTimeInterval: 2.0)
            tryConnect(host: host, port: port, attempt: attempt + 1, maxAttempts: maxAttempts)
        } else {
            logMsg("ws-client", "Failed to connect after \(maxAttempts) attempts")
            exit(1)
        }
    }
}

tryConnect(host: host, port: port, attempt: 1, maxAttempts: 3)

// Keep main thread alive (receive loop in tryConnect runs on main thread)
RunLoop.main.run()

// MARK: - Message Handler

func handleMessage(_ state: ClientState, _ msg: WsMessage) {
    switch msg.type {
    case .bonjour:
        logMsg("ws-client", "BONJOUR server_language=\(msg.serverLanguage ?? "")")

    case .ping:
        let w = ByteWriter()
        w.writeI64(msg.timestampMs ?? 0)
        state.sendMsg(.pong, payload: w.data())

    case .timeNotification:
        logMsg("ws-client", "TIME_NOTIFICATION ts=\(msg.timestampMs ?? 0) iso=\(msg.iso8601 ?? "")")

    case .kissRequest:
        logMsg("ws-client", "KISS_REQUEST os=\(msg.osName ?? "") ver=\(msg.osVersion ?? "") rel=\(msg.osRelease ?? "") arch=\(msg.osArch ?? "")")
        let w = ByteWriter()
        w.writeString("en_US")
        w.writeString("UTF-8")
        w.writeString("UTC")
        state.sendMsg(.kissResponse, payload: w.data())

    case .echoResponse:
        logMsg("ws-client", "ECHO_RESPONSE status=\(msg.echoStatus ?? 0) results=\(msg.echoResults?.count ?? 0)")

    case .hashResponse:
        logMsg("ws-client", "HASH_RESPONSE id=\(msg.randomId ?? 0) hash=\(msg.hashHex ?? "")")

    case .error:
        logMsg("ws-client", "ERROR code=\(msg.errorCode ?? 0) msg=\(msg.errorMessage ?? "")")

    case .disconnect:
        logMsg("ws-client", "DISCONNECT reason=\(msg.disconnectReason ?? "")")
        state.close()

    default:
        let hex = String(format: "0x%02x", msg.type.rawValue)
        logMsg("ws-client", "Unknown message type: \(hex)")
    }
}
