import Foundation
import HelloWebSocket

// MARK: - Session

final class Session: @unchecked Sendable {
    let socket: TCPSocket
    let userId: String
    let sessionId: String
    var clientLanguage = "unknown"
    let connectedAt: Int64
    var lastPongTs: Int64
    var active = true
    private let lock = NSLock()

    init(socket: TCPSocket, userId: String) {
        self.socket = socket
        self.userId = userId.isEmpty ? "swift-\(nowMs())" : userId
        self.sessionId = String(nowMs())
        self.connectedAt = nowMs()
        self.lastPongTs = nowMs()
    }

    func send(_ data: [UInt8]) {
        lock.lock()
        defer { lock.unlock() }
        guard active else { return }
        do {
            let wsFrame = wsEncodeBinaryFrame(data, masked: false)
            _ = try socket.sendBytes(wsFrame)
        } catch {
            active = false
        }
    }

    func sendMsg(_ msgType: MsgType, payload: [UInt8]) {
        send(encodeFrame(msgType, payload: payload))
    }

    func close() {
        lock.lock()
        defer { lock.unlock() }
        if active {
            active = false
            socket.close()
        }
    }
}

// MARK: - Server

let port = Int(ProcessInfo.processInfo.environment["WS_PORT"] ?? "") ?? WsProtocol.port

logMsg("ws-server", "Starting Swift WebSocket server on port \(port)")
let serverSocket = try TCPSocket.createServer(port: port)

while true {
    let clientSocket: TCPSocket
    do {
        clientSocket = try serverSocket.accept()
    } catch {
        logMsg("ws-server", "Accept error: \(error)")
        continue
    }

    let userId: String
    do {
        userId = try wsServerUpgrade(clientSocket)
    } catch {
        logMsg("ws-server", "Upgrade error: \(error)")
        clientSocket.close()
        continue
    }

    let session = Session(socket: clientSocket, userId: userId)
    logMsg("ws-server", "[\(session.userId)] session+")

    // Background: PING every 1s
    Thread {
        while session.active {
            Thread.sleep(forTimeInterval: 1.0)
            guard session.active else { return }
            let w = ByteWriter()
            w.writeI64(nowMs())
            session.sendMsg(.ping, payload: w.data())
        }
    }.start()

    // Background: TIME_NOTIFICATION every 5s
    Thread {
        while session.active {
            Thread.sleep(forTimeInterval: 5.0)
            guard session.active else { return }
            let w = ByteWriter()
            w.writeI64(nowMs())
            w.writeString(nowISO())
            session.sendMsg(.timeNotification, payload: w.data())
        }
    }.start()

    // Background: KISS_REQUEST every 5s
    Thread {
        while session.active {
            Thread.sleep(forTimeInterval: 5.0)
            guard session.active else { return }
            let w = ByteWriter()
            w.writeString("Windows NT")
            w.writeString("unknown")
            w.writeString("unknown")
            w.writeString("AMD64")
            session.sendMsg(.kissRequest, payload: w.data())
        }
    }.start()

    // Background: timeout check every 5s
    Thread {
        while session.active {
            Thread.sleep(forTimeInterval: 5.0)
            guard session.active else { return }
            if nowMs() - session.lastPongTs > 60000 {
                logMsg("ws-server", "[\(session.userId)] session timeout")
                session.close()
                return
            }
        }
    }.start()

    // Receive loop
    Thread {
        while session.active {
            do {
                let payload = try wsReadFrame(session.socket)
                let msg = try decodeMessage(payload)
                handleMessage(session, msg)
            } catch {
                session.close()
                break
            }
        }
        logMsg("ws-server", "[\(session.userId)] session-")
    }.start()
}

// MARK: - Message Handler

func handleMessage(_ session: Session, _ msg: WsMessage) {
    switch msg.type {
    case .hello:
        session.clientLanguage = msg.clientLanguage ?? ""
        logMsg("ws-server", "HELLO from \(session.clientLanguage), session=\(session.sessionId), time=\(nowMs())")
        let w = ByteWriter()
        w.writeString(WsProtocol.serverLang)
        session.sendMsg(.bonjour, payload: w.data())

    case .echoRequest:
        logMsg("ws-server", "ECHO_REQUEST id=\(msg.echoId ?? 0) meta=\(msg.echoMeta ?? "") data=\(msg.echoData ?? "")")
        let w = ByteWriter()
        w.writeI32(200)
        w.writeU32(1)
        w.writeI64(nowMs())
        w.writeU8(0)
        w.writeKv([
            ("id", String(msg.echoId ?? 0)),
            ("data", msg.echoData ?? ""),
            ("meta", msg.echoMeta ?? ""),
            ("lang", session.clientLanguage),
        ])
        session.sendMsg(.echoResponse, payload: w.data())

    case .kissResponse:
        logMsg("ws-server", "KISS_RESPONSE lang=\(msg.kissLanguage ?? "") enc=\(msg.kissEncoding ?? "") tz=\(msg.kissTimeZone ?? "")")

    case .pong:
        session.lastPongTs = msg.timestampMs ?? nowMs()

    case .randomNumber:
        let num = msg.randomNumber ?? 0
        let id = msg.randomId ?? 0
        logMsg("ws-server", "RANDOM_NUMBER id=\(id) number=\(num)")
        let hash = hashNumber(num)
        let w = ByteWriter()
        w.writeI64(id)
        w.writeString(hash)
        session.sendMsg(.hashResponse, payload: w.data())

    case .disconnect:
        logMsg("ws-server", "DISCONNECT reason=\(msg.disconnectReason ?? "")")
        session.close()

    case .error:
        logMsg("ws-server", "ERROR code=\(msg.errorCode ?? 0) msg=\(msg.errorMessage ?? "")")

    default:
        let hex = String(format: "0x%02x", msg.type.rawValue)
        logMsg("ws-server", "Unknown message type: \(hex)")
        let w = ByteWriter()
        w.writeI32(ErrCode.unknownMsgType.rawValue)
        w.writeString("unknown type \(hex)")
        session.sendMsg(.error, payload: w.data())
    }
}

