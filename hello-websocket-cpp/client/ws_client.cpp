// Hello WebSocket Protocol - C++ Client
// Implements the full PROTOCOL.md client lifecycle: connect, HELLO/BONJOUR,
// ping/pong, time notification, kiss request/response, random/hash, disconnect.
#include "../common/codec.hpp"
#include "../common/ws.hpp"
#include <thread>
#include <mutex>
#include <atomic>
#include <sstream>
#include <cstring>
#include <cstdlib>

using namespace hws;

bool tryConnect(const std::string& host, uint16_t port);

int main() {
    const char* hostEnv = std::getenv("WS_SERVER");
    std::string host = hostEnv ? hostEnv : "127.0.0.1";

    const char* portEnv = std::getenv("WS_PORT");
    uint16_t p = PORT;
    if (portEnv) {
        int v = atoi(portEnv);
        if (v > 0) p = static_cast<uint16_t>(v);
    }

    log("ws-client", "Starting C++ WebSocket client [version: 1.0.0]");
    std::string url = "ws://" + host + ":" + std::to_string(p);
    log("ws-client", "Connecting to " + url);

    for (int attempt = 1; attempt <= 3; attempt++) {
        log("ws-client", "Connection attempt " + std::to_string(attempt) + "/3 to " + url);
        if (tryConnect(host, p)) return 0;
        if (attempt < 3) std::this_thread::sleep_for(std::chrono::seconds(2));
    }

    log("ws-client", "Failed to connect after 3 attempts");
    return 1;
}

bool tryConnect(const std::string& host, uint16_t port) {
    WSClient ws;
    std::string userId = "cpp-client-" + std::to_string(nowMs());

    if (!ws.connect(host, port, userId)) {
        log("ws-client", "Connection failed");
        return false;
    }

    log("ws-client", "Connected");

    std::mutex sendMutex;
    std::atomic<bool> active(true);

    // Helper: send masked binary frame (client-to-server must be masked)
    auto sendFrame = [&](const std::vector<uint8_t>& data) -> bool {
        std::lock_guard<std::mutex> lock(sendMutex);
        bool result = false;
        if (active) result = wsSendBinaryMasked(ws.sock, data);
        return result;
    };

    // Send HELLO
    {
        Message m{};
        m.type = MSG_HELLO;
        m.clientLanguage = CLIENT_LANG;
        sendFrame(m.encode());
    }

    // Background: RANDOM_NUMBER every 5s
    std::thread randomThread([&]() {
        std::mt19937_64 rng(std::random_device{}());
        int64_t randomId = 1;
        while (active) {
            std::this_thread::sleep_for(std::chrono::milliseconds(RANDOM_INTERVAL_MS));
            if (!active) break;
            int64_t num = static_cast<int64_t>(rng());
            Message m{};
            m.type = MSG_RANDOM_NUMBER;
            m.randomId = randomId;
            m.randomNumber = num;
            if (!sendFrame(m.encode())) { active = false; break; }
            log("ws-client", "RANDOM_NUMBER id=" + std::to_string(randomId) + " number=" + std::to_string(num));
            randomId++;
        }
    });

    // Receive loop
    try {
        while (active) {
            WSFrame frame = wsRecvFrame(ws.sock, false);
            if (!frame.isValid || frame.isClose) break;

            Message msg;
            try {
                msg = decodeMessage(frame.payload.data(), frame.payload.size());
            } catch (const std::exception& e) {
                log("ws-client", std::string("Decode error: ") + e.what());
                continue;
            }

            switch (msg.type) {
                case MSG_BONJOUR:
                    log("ws-client", "BONJOUR server_language=" + msg.serverLanguage);
                    break;

                case MSG_PING:
                    {
                        Message pong{};
                        pong.type = MSG_PONG;
                        pong.timestampMs = msg.timestampMs;
                        sendFrame(pong.encode());
                    }
                    break;

                case MSG_TIME_NOTIFICATION:
                    log("ws-client", "TIME_NOTIFICATION ts=" + std::to_string(msg.timestampMs) + " iso=" + msg.iso8601);
                    break;

                case MSG_KISS_REQUEST:
                    log("ws-client", "KISS_REQUEST os=" + msg.osName + " arch=" + msg.osArch);
                    {
                        Message resp{};
                        resp.type = MSG_KISS_RESPONSE;
                        resp.kissLanguage = "en_US";
                        resp.kissEncoding = "UTF-8";
                        resp.kissTimeZone = "UTC";
                        sendFrame(resp.encode());
                    }
                    break;

                case MSG_ECHO_RESPONSE:
                    log("ws-client", "ECHO_RESPONSE status=" + std::to_string(msg.echoStatus) + " results=" + std::to_string(msg.echoResults.size()));
                    for (size_t i = 0; i < msg.echoResults.size(); i++) {
                        const auto& r = msg.echoResults[i];
                        std::string kvStr;
                        bool first = true;
                        for (const auto& [k, v] : r.kv) {
                            if (!first) kvStr += ", ";
                            kvStr += k + "=" + v;
                            first = false;
                        }
                        log("ws-client", "  Result #" + std::to_string(i + 1) + ": idx=" + std::to_string(r.idx) + " type=" + std::to_string(r.type) + " kv={" + kvStr + "}");
                    }
                    break;

                case MSG_HASH_RESPONSE:
                    log("ws-client", "HASH_RESPONSE id=" + std::to_string(msg.randomId) + " hash=" + msg.hashHex);
                    break;

                case MSG_ERROR:
                    log("ws-client", "ERROR code=" + std::to_string(msg.errorCode) + " msg=" + msg.errorMessage);
                    break;

                case MSG_DISCONNECT:
                    log("ws-client", "DISCONNECT reason=" + msg.disconnectReason);
                    active = false;
                    break;

                default: {
                    std::ostringstream ss;
                    ss << std::hex << static_cast<int>(msg.type);
                    log("ws-client", "Unknown message type: 0x" + ss.str());
                }
            }
        }
    } catch (const std::exception& e) {
        log("ws-client", std::string("Exception: ") + e.what());
    } catch (...) {
        log("ws-client", "Unknown exception");
    }

    // Cleanup: always join thread before returning
    active = false;
    if (randomThread.joinable()) randomThread.join();

    // Send DISCONNECT
    {
        Message m{};
        m.type = MSG_DISCONNECT;
        m.disconnectReason = "client shutdown";
        sendFrame(m.encode());
    }

    wsSendControl(ws.sock, 0x08, {}, true);
    ws.close();
    log("ws-client", "Disconnected");
    return true;
}
