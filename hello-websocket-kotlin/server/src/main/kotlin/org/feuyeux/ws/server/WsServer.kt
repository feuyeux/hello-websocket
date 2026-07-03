// Hello WebSocket Protocol - Kotlin Server
// Implements the full PROTOCOL.md server lifecycle: handshake, background tasks,
// echo, kiss, ping/pong, time broadcast, random/hash, disconnect.
package org.feuyeux.ws.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.feuyeux.ws.common.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

fun main() {
    val port = System.getenv("WS_PORT")?.toIntOrNull() ?: PORT

    log("ws-server", "Starting Kotlin WebSocket server on port $port")

    embeddedServer(Netty, port, host = "0.0.0.0") {
        install(WebSockets)
        routing {
            webSocket("/") {
                handleClient(this)
            }
        }
    }.start(wait = true)
}

suspend fun handleClient(ws: WebSocketServerSession) = coroutineScope {
    val userId = ws.call.request.headers["userId"] ?: "kotlin-${nowMs()}"
    val sessionId = nowMs().toString()
    var clientLanguage = "unknown"
    val lastPongTs = AtomicLong(nowMs())
    val active = AtomicBoolean(true)

    log("ws-server", "[$userId] session+")

    // Background: PING every 1s
    val pingJob = launch {
        while (active.get()) {
            delay(PING_INTERVAL_MS)
            if (!active.get()) break
            try {
                ws.send(Frame.Binary(true, ping(nowMs()).encode()))
            } catch (_: Exception) { active.set(false); break }
        }
    }

    // Background: TIME_NOTIFICATION every 5s
    val timeJob = launch {
        while (active.get()) {
            delay(TIME_INTERVAL_MS)
            if (!active.get()) break
            try {
                ws.send(Frame.Binary(true, timeNotif(nowMs(), nowISO()).encode()))
            } catch (_: Exception) { active.set(false); break }
        }
    }

    // Background: KISS_REQUEST every 5s
    val kissJob = launch {
        while (active.get()) {
            delay(KISS_INTERVAL_MS)
            if (!active.get()) break
            try {
                val os = System.getProperty("os.name") ?: "unknown"
                val ver = System.getProperty("os.version") ?: "unknown"
                val arch = System.getProperty("os.arch") ?: "unknown"
                ws.send(Frame.Binary(true, kissRequest(os, ver, "unknown", arch).encode()))
            } catch (_: Exception) { active.set(false); break }
        }
    }

    // Background: timeout check every 5s
    val timeoutJob = launch {
        while (active.get()) {
            delay(5000)
            if (!active.get()) break
            if (nowMs() - lastPongTs.get() > SESSION_TIMEOUT_MS) {
                log("ws-server", "[$userId] session timeout")
                active.set(false)
                break
            }
        }
    }

    // Receive loop
    try {
        for (frame in ws.incoming) {
            if (frame !is Frame.Binary) continue
            val data = frame.readBytes()

            val msg: Message
            try {
                msg = decodeMessage(data)
            } catch (e: Exception) {
                log("ws-server", "Decode error: ${e.message}")
                ws.send(Frame.Binary(true, errorMsg(ERR_DECODE, e.message ?: "decode error").encode()))
                continue
            }

            when (msg.type) {
                MSG_HELLO -> {
                    clientLanguage = msg.clientLanguage
                    log("ws-server", "HELLO from $clientLanguage, session=$sessionId, time=${nowMs()}")
                    ws.send(Frame.Binary(true, bonjour(SERVER_LANG).encode()))
                }

                MSG_ECHO_REQUEST -> {
                    log("ws-server", "ECHO_REQUEST id=${msg.echoId} meta=${msg.echoMeta} data=${msg.echoData}")
                    val resp = Message(MSG_ECHO_RESPONSE).apply {
                        echoStatus = 200
                        echoResults = listOf(EchoResult(nowMs(), 0, mapOf(
                            "id" to msg.echoId.toString(),
                            "data" to msg.echoData,
                            "meta" to msg.echoMeta,
                            "lang" to clientLanguage
                        )))
                    }
                    ws.send(Frame.Binary(true, resp.encode()))
                }

                MSG_KISS_RESPONSE -> {
                    log("ws-server", "KISS_RESPONSE lang=${msg.kissLanguage} enc=${msg.kissEncoding} tz=${msg.kissTimeZone}")
                }

                MSG_PONG -> {
                    lastPongTs.set(msg.timestampMs)
                }

                MSG_RANDOM_NUMBER -> {
                    log("ws-server", "RANDOM_NUMBER id=${msg.randomId} number=${msg.randomNumber}")
                    val hash = hashNumber(msg.randomNumber)
                    ws.send(Frame.Binary(true, hashResponseMsg(msg.randomId, hash).encode()))
                }

                MSG_DISCONNECT -> {
                    log("ws-server", "DISCONNECT reason=${msg.disconnectReason}")
                    active.set(false)
                }

                MSG_ERROR -> {
                    log("ws-server", "ERROR code=${msg.errorCode} msg=${msg.errorMessage}")
                }

                else -> {
                    val hex = "0x${msg.type.toInt().toString(16)}"
                    log("ws-server", "Unknown message type: $hex")
                    ws.send(Frame.Binary(true, errorMsg(ERR_UNKNOWN_MSG_TYPE, "unknown type $hex").encode()))
                }
            }
        }
    } catch (e: Exception) {
        log("ws-server", "[$userId] exception: ${e.message}")
    }

    // Cleanup
    active.set(false)
    pingJob.cancel()
    timeJob.cancel()
    kissJob.cancel()
    timeoutJob.cancel()
    try { ws.close(CloseReason(CloseReason.Codes.NORMAL, "server shutdown")) } catch (_: Exception) {}
    log("ws-server", "[$userId] session-")
}
