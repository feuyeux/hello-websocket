// Hello WebSocket Protocol - C++ Server
// Implements the full PROTOCOL.md server lifecycle: handshake, background tasks,
// echo, kiss, ping/pong, time broadcast, random/hash, disconnect.
#include "../common/codec.hpp"
#include "../common/ws.hpp"
#include <thread>
#include <mutex>
#include <atomic>
#include <sstream>
#include <cstring>
#include <cstdlib>

using namespace hws;

// Forward declaration
void handleClient(socket_t clientSock, const std::string& userId);

int main() {
    const char* portEnv = std::getenv("WS_PORT");
    uint16_t p = PORT;
    if (portEnv) {
        int v = atoi(portEnv);
        if (v > 0) p = static_cast<uint16_t>(v);
    }

    log("ws-server", "Starting C++ WebSocket server on port " + std::to_string(p));

    WSServer server;
    if (!server.listen(p)) {
        log("ws-server", "Failed to bind port " + std::to_string(p));
        return 1;
    }

    log("ws-server", "Listening on 0.0.0.0:" + std::to_string(p));

    while (true) {
        std::string userId;
        socket_t clientSock = server.accept(userId);
        if (clientSock == HWS_INVALID_SOCKET) continue;

        std::thread([clientSock, userId]() {
            handleClient(clientSock, userId);
        }).detach();
    }

    server.close();
    return 0;
}

void handleClient(socket_t clientSock, const std::string& userId) {
    std::string sessionId = std::to_string(nowMs());
    std::string clientLanguage = "unknown";

    std::atomic_flag sendLock = ATOMIC_FLAG_INIT;
    std::atomic<bool> active(true);
    std::atomic<int64_t> lastPongTs(nowMs());

    log("ws-server", "[" + userId + "] session+");

    // Helper to send binary frame (thread-safe)
    auto sendFrame = [&](const std::vector<uint8_t>& data) -> bool {
        while (sendLock.test_and_set(std::memory_order_acquire)) {}
        bool result = false;
        if (active) result = wsSendBinary(clientSock, data);
        sendLock.clear(std::memory_order_release);
        return result;
    };

    // Background: PING every 1s
    std::thread pingThread([&]() {
        while (active) {
            std::this_thread::sleep_for(std::chrono::milliseconds(PING_INTERVAL_MS));
            if (!active) break;
            Message m{};
            m.type = MSG_PING;
            m.timestampMs = nowMs();
            if (!sendFrame(m.encode())) { active = false; break; }
        }
    });

    // Background: TIME_NOTIFICATION every 5s
    std::thread timeThread([&]() {
        while (active) {
            std::this_thread::sleep_for(std::chrono::milliseconds(TIME_INTERVAL_MS));
            if (!active) break;
            Message m{};
            m.type = MSG_TIME_NOTIFICATION;
            m.timestampMs = nowMs();
            m.iso8601 = nowISO();
            if (!sendFrame(m.encode())) { active = false; break; }
        }
    });

    // Background: KISS_REQUEST every 5s
    std::thread kissThread([&]() {
        while (active) {
            std::this_thread::sleep_for(std::chrono::milliseconds(KISS_INTERVAL_MS));
            if (!active) break;
            Message m{};
            m.type = MSG_KISS_REQUEST;
#ifdef _WIN32
            m.osName = "Windows";
            m.osVersion = "unknown";
            m.osRelease = "unknown";
            m.osArch = "AMD64";
#else
            m.osName = "Linux";
            m.osVersion = "unknown";
            m.osRelease = "unknown";
            m.osArch = "x86_64";
#endif
            if (!sendFrame(m.encode())) { active = false; break; }
        }
    });

    // Background: timeout check every 5s
    std::thread timeoutThread([&]() {
        while (active) {
            std::this_thread::sleep_for(std::chrono::seconds(5));
            if (!active) break;
            if (nowMs() - lastPongTs.load() > static_cast<int64_t>(SESSION_TIMEOUT_MS)) {
                log("ws-server", "[" + userId + "] session timeout");
                active = false;
                break;
            }
        }
    });

    // Receive loop — wrapped so threads are ALWAYS joined before return
    try {
        while (active) {
            WSFrame frame = wsRecvFrame(clientSock);
            if (!frame.isValid || frame.isClose) break;

            Message msg;
            try {
                msg = decodeMessage(frame.payload.data(), frame.payload.size());
            } catch (const std::exception& e) {
                Message err{};
                err.type = MSG_ERROR;
                err.errorCode = ERR_DECODE;
                err.errorMessage = e.what();
                sendFrame(err.encode());
                continue;
            }

            switch (msg.type) {
                case MSG_HELLO:
                    clientLanguage = msg.clientLanguage;
                    log("ws-server", "HELLO from " + clientLanguage + ", session=" + sessionId);
                    {
                        Message bonjour{};
                        bonjour.type = MSG_BONJOUR;
                        bonjour.serverLanguage = SERVER_LANG;
                        sendFrame(bonjour.encode());
                    }
                    break;

                case MSG_ECHO_REQUEST:
                    log("ws-server", "ECHO_REQUEST id=" + std::to_string(msg.echoId));
                    {
                        Message resp{};
                        resp.type = MSG_ECHO_RESPONSE;
                        resp.echoStatus = 200;
                        EchoResult r;
                        r.idx = nowMs();
                        r.type = 0;
                        r.kv["id"] = std::to_string(msg.echoId);
                        r.kv["data"] = msg.echoData;
                        r.kv["meta"] = msg.echoMeta;
                        r.kv["lang"] = clientLanguage;
                        resp.echoResults.push_back(r);
                        sendFrame(resp.encode());
                    }
                    break;

                case MSG_KISS_RESPONSE:
                    log("ws-server", "KISS_RESPONSE lang=" + msg.kissLanguage + " enc=" + msg.kissEncoding + " tz=" + msg.kissTimeZone);
                    break;

                case MSG_PONG:
                    lastPongTs.store(msg.timestampMs);
                    break;

                case MSG_RANDOM_NUMBER:
                    log("ws-server", "RANDOM_NUMBER id=" + std::to_string(msg.randomId) + " number=" + std::to_string(msg.randomNumber));
                    {
                        std::string hash = hashNumber(msg.randomNumber);
                        Message resp{};
                        resp.type = MSG_HASH_RESPONSE;
                        resp.randomId = msg.randomId;
                        resp.hashHex = hash;
                        sendFrame(resp.encode());
                    }
                    break;

                case MSG_DISCONNECT:
                    log("ws-server", "DISCONNECT reason=" + msg.disconnectReason);
                    active = false;
                    break;

                case MSG_ERROR:
                    log("ws-server", "ERROR code=" + std::to_string(msg.errorCode) + " msg=" + msg.errorMessage);
                    break;

                default: {
                    std::ostringstream ss;
                    ss << std::hex << static_cast<int>(msg.type);
                    log("ws-server", "Unknown message type: 0x" + ss.str());
                    Message err{};
                    err.type = MSG_ERROR;
                    err.errorCode = ERR_UNKNOWN_MSG_TYPE;
                    err.errorMessage = "unknown type 0x" + ss.str();
                    sendFrame(err.encode());
                }
            }
        }
    } catch (const std::exception& e) {
        log("ws-server", "[" + userId + "] exception: " + e.what());
    } catch (...) {
        log("ws-server", "[" + userId + "] unknown exception");
    }

    // Cleanup: always join threads before returning
    active = false;
    hws_close(clientSock);  // unblock any blocked sends in background threads
    if (pingThread.joinable()) pingThread.join();
    if (timeThread.joinable()) timeThread.join();
    if (kissThread.joinable()) kissThread.join();
    if (timeoutThread.joinable()) timeoutThread.join();

    log("ws-server", "[" + userId + "] session-");
}
