// Hello WebSocket Protocol - Kotlin Client
// Implements the full PROTOCOL.md client lifecycle: connect, HELLO/BONJOUR,
// ping/pong, time notification, kiss request/response, random/hash, disconnect.
package org.feuyeux.ws.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.feuyeux.ws.common.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

fun main() = runBlocking {
    val host = System.getenv("WS_SERVER") ?: "127.0.0.1"
    val port = System.getenv("WS_PORT")?.toIntOrNull() ?: PORT

    log("ws-client", "Starting Kotlin WebSocket client [version: 1.0.0]")
    val url = "ws://$host:$port"
    log("ws-client", "Connecting to $url")

    for (attempt in 1..3) {
        log("ws-client", "Connection attempt $attempt/3 to $url")
        try {
            if (tryConnect(host, port)) return@runBlocking
        } catch (e: Exception) {
            log("ws-client", "Error: ${e.message}")
        }
        if (attempt < 3) delay(2000)
    }

    log("ws-client", "Failed to connect after 3 attempts")
}

suspend fun tryConnect(host: String, port: Int): Boolean {
    val client = HttpClient(CIO) {
        install(WebSockets)
    }

    val userId = "kotlin-client-${nowMs()}"
    val active = AtomicBoolean(true)

    try {
        client.webSocket(
            method = HttpMethod.Get,
            host = host,
            port = port,
            path = "/",
            request = { header("userId", userId) }
        ) {
            log("ws-client", "Connected")

            // Send HELLO
            send(Frame.Binary(true, hello(CLIENT_LANG).encode()))

            // Background: RANDOM_NUMBER every 5s
            var randomId = 1L
            val randomJob = launch {
                while (active.get()) {
                    delay(RANDOM_INTERVAL_MS)
                    if (!active.get()) break
                    val num = Random.nextLong()
                    try {
                        send(Frame.Binary(true, randomNumberMsg(randomId, num).encode()))
                        log("ws-client", "RANDOM_NUMBER id=$randomId number=$num")
                        randomId++
                    } catch (_: Exception) { active.set(false); break }
                }
            }

            // Receive loop
            try {
                for (frame in incoming) {
                    if (frame !is Frame.Binary) continue
                    val data = frame.readBytes()

                    val msg: Message
                    try {
                        msg = decodeMessage(data)
                    } catch (e: Exception) {
                        log("ws-client", "Decode error: ${e.message}")
                        continue
                    }

                    when (msg.type) {
                        MSG_BONJOUR -> {
                            log("ws-client", "BONJOUR server_language=${msg.serverLanguage}")
                        }

                        MSG_PING -> {
                            send(Frame.Binary(true, pong(msg.timestampMs).encode()))
                        }

                        MSG_TIME_NOTIFICATION -> {
                            log("ws-client", "TIME_NOTIFICATION ts=${msg.timestampMs} iso=${msg.iso8601}")
                        }

                        MSG_KISS_REQUEST -> {
                            log("ws-client", "KISS_REQUEST os=${msg.osName} ver=${msg.osVersion} rel=${msg.osRelease} arch=${msg.osArch}")
                            send(Frame.Binary(true, kissResponse("en_US", "UTF-8", "UTC").encode()))
                        }

                        MSG_ECHO_RESPONSE -> {
                            log("ws-client", "ECHO_RESPONSE status=${msg.echoStatus} results=${msg.echoResults.size}")
                            for ((i, r) in msg.echoResults.withIndex()) {
                                log("ws-client", "  Result #${i + 1}: idx=${r.idx} type=${r.type} kv=${r.kv}")
                            }
                        }

                        MSG_HASH_RESPONSE -> {
                            log("ws-client", "HASH_RESPONSE id=${msg.randomId} hash=${msg.hashHex}")
                        }

                        MSG_ERROR -> {
                            log("ws-client", "ERROR code=${msg.errorCode} msg=${msg.errorMessage}")
                        }

                        MSG_DISCONNECT -> {
                            log("ws-client", "DISCONNECT reason=${msg.disconnectReason}")
                            active.set(false)
                        }

                        else -> {
                            val hex = "0x${msg.type.toInt().toString(16)}"
                            log("ws-client", "Unknown message type: $hex")
                        }
                    }
                }
            } catch (e: Exception) {
                log("ws-client", "Exception: ${e.message}")
            }

            // Cleanup
            active.set(false)
            randomJob.cancel()

            // Send DISCONNECT
            try {
                send(Frame.Binary(true, disconnectMsg("client shutdown").encode()))
                close(CloseReason(CloseReason.Codes.NORMAL, "client shutdown"))
            } catch (_: Exception) {}

            log("ws-client", "Disconnected")
        }
    } finally {
        client.close()
    }

    return true
}
