// Hello WebSocket Transport Layer - C++ implementation.
// Minimal, dependency-free WebSocket layer (RFC 6455) for the hello-websocket protocol.
// Provides: cross-platform sockets, SHA-1, Base64, WS handshake, WS binary framing.
#pragma once

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <array>
#include <stdexcept>
#include <sstream>
#include <random>
#include <thread>
#include <mutex>
#include <atomic>
#include <algorithm>
#include <cctype>
#include <cstdlib>

// ─── Cross-Platform Socket Abstraction ────────────────────────────────────

#ifdef _WIN32
    #ifndef WIN32_LEAN_AND_MEAN
    #define WIN32_LEAN_AND_MEAN
    #endif
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #pragma comment(lib, "ws2_32.lib")
    using socket_t = SOCKET;
    #define HWS_INVALID_SOCKET INVALID_SOCKET
    #define HWS_SOCKET_ERROR SOCKET_ERROR
    inline int hws_close(socket_t s) { return ::closesocket(s); }
    inline int hws_shutdown(socket_t s) { return ::shutdown(s, SD_BOTH); }
    inline int hws_errno() { return WSAGetLastError(); }
    inline bool hws_would_block() { return WSAGetLastError() == WSAEWOULDBLOCK; }
    // Global WSA initializer
    struct WSAInitializer {
        WSAInitializer() { WSADATA d; WSAStartup(MAKEWORD(2,2), &d); }
        ~WSAInitializer() { WSACleanup(); }
    };
    static WSAInitializer g_wsa_init;
#else
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <netdb.h>
    #include <unistd.h>
    #include <fcntl.h>
    #include <errno.h>
    using socket_t = int;
    #define HWS_INVALID_SOCKET (-1)
    #define HWS_SOCKET_ERROR (-1)
    inline int hws_close(socket_t s) { return ::close(s); }
    inline int hws_shutdown(socket_t s) { return ::shutdown(s, SHUT_RDWR); }
    inline int hws_errno() { return errno; }
    inline bool hws_would_block() { return errno == EAGAIN || errno == EWOULDBLOCK; }
#endif

namespace hws {

// ─── SHA-1 (for WebSocket handshake) ──────────────────────────────────────

namespace detail {

struct SHA1 {
    uint32_t h[5] = {0x67452301, 0xEFCDAB89, 0x98BADCFE, 0x10325476, 0xC3D2E1F0};
    uint64_t bitlen = 0;
    uint8_t data[64];
    size_t datalen = 0;

    static uint32_t rotl(uint32_t x, uint32_t n) { return (x << n) | (x >> (32 - n)); }

    void transform() {
        uint32_t w[80];
        for (int i = 0; i < 16; i++) {
            w[i] = (static_cast<uint32_t>(data[i*4]) << 24) |
                   (static_cast<uint32_t>(data[i*4+1]) << 16) |
                   (static_cast<uint32_t>(data[i*4+2]) << 8) |
                   static_cast<uint32_t>(data[i*4+3]);
        }
        for (int i = 16; i < 80; i++) {
            w[i] = rotl(w[i-3] ^ w[i-8] ^ w[i-14] ^ w[i-16], 1);
        }
        uint32_t a=h[0],b=h[1],c=h[2],d=h[3],e=h[4];
        for (int i = 0; i < 80; i++) {
            uint32_t f, k;
            if (i < 20)      { f = (b & c) | (~b & d); k = 0x5A827999; }
            else if (i < 40) { f = b ^ c ^ d;          k = 0x6ED9EBA1; }
            else if (i < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDC; }
            else             { f = b ^ c ^ d;          k = 0xCA62C1D6; }
            uint32_t temp = rotl(a, 5) + f + e + k + w[i];
            e=d; d=c; c=rotl(b,30); b=a; a=temp;
        }
        h[0]+=a; h[1]+=b; h[2]+=c; h[3]+=d; h[4]+=e;
    }

    void update(const uint8_t* msg, size_t len) {
        for (size_t i = 0; i < len; i++) {
            data[datalen++] = msg[i];
            if (datalen == 64) {
                transform();
                bitlen += 512;
                datalen = 0;
            }
        }
    }

    std::array<uint8_t,20> final() {
        // Save total message length in bits BEFORE padding
        uint64_t totalBits = bitlen + static_cast<uint64_t>(datalen) * 8;
        // Append 0x80
        data[datalen++] = 0x80;
        // Pad to 56 bytes mod 64
        if (datalen > 56) {
            while (datalen < 64) data[datalen++] = 0;
            transform();
            datalen = 0;
        }
        while (datalen < 56) data[datalen++] = 0;
        // Append total message length in bits (big-endian)
        for (int i = 0; i < 8; i++) data[56+i] = static_cast<uint8_t>((totalBits >> ((7-i)*8)) & 0xFF);
        transform();
        std::array<uint8_t,20> out;
        for (int i = 0; i < 5; i++) {
            out[i*4]   = (h[i] >> 24) & 0xFF;
            out[i*4+1] = (h[i] >> 16) & 0xFF;
            out[i*4+2] = (h[i] >> 8) & 0xFF;
            out[i*4+3] = h[i] & 0xFF;
        }
        return out;
    }
};

} // namespace detail

// ─── Base64 ───────────────────────────────────────────────────────────────

inline std::string base64Encode(const uint8_t* data, size_t len) {
    static const char table[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    for (size_t i = 0; i < len; i += 3) {
        uint32_t n = static_cast<uint32_t>(data[i]) << 16;
        if (i + 1 < len) n |= static_cast<uint32_t>(data[i+1]) << 8;
        if (i + 2 < len) n |= static_cast<uint32_t>(data[i+2]);
        out += table[(n >> 18) & 0x3F];
        out += table[(n >> 12) & 0x3F];
        out += (i + 1 < len) ? table[(n >> 6) & 0x3F] : '=';
        out += (i + 2 < len) ? table[n & 0x3F] : '=';
    }
    return out;
}

inline std::string base64Encode(const std::string& s) {
    return base64Encode(reinterpret_cast<const uint8_t*>(s.data()), s.size());
}

// ─── WebSocket Handshake Helpers ─────────────────────────────────────────

inline std::string wsAcceptKey(const std::string& clientKey) {
    std::string combined = clientKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    detail::SHA1 sha;
    sha.update(reinterpret_cast<const uint8_t*>(combined.data()), combined.size());
    auto hash = sha.final();
    return base64Encode(hash.data(), hash.size());
}

inline std::string generateWSKey() {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 255);
    uint8_t buf[16];
    for (int i = 0; i < 16; i++) buf[i] = static_cast<uint8_t>(dis(gen));
    return base64Encode(buf, 16);
}

// Extract header value from HTTP text (case-insensitive key)
inline std::string extractHeader(const std::string& httpText, const std::string& key) {
    std::string search = "\r\n" + key + ":";
    size_t pos = 0;
    std::string lowerText = httpText;
    std::string lowerKey = search;
    // lowercase the search
    for (auto& c : lowerKey) c = static_cast<char>(tolower(c));
    // lowercase the text
    for (auto& c : lowerText) c = static_cast<char>(tolower(c));
    pos = lowerText.find(lowerKey);
    if (pos == std::string::npos) return "";
    pos += lowerKey.size();
    while (pos < lowerText.size() && (lowerText[pos] == ' ' || lowerText[pos] == '\t')) pos++;
    size_t end = lowerText.find("\r\n", pos);
    if (end == std::string::npos) end = lowerText.size();
    return httpText.substr(pos, end - pos);
}

// ─── TCP Socket Helpers ───────────────────────────────────────────────────

inline bool recvAll(socket_t sock, void* buf, size_t len) {
    auto* p = static_cast<char*>(buf);
    size_t total = 0;
    while (total < len) {
        int n = ::recv(sock, p + total, static_cast<int>(len - total), 0);
        if (n <= 0) return false;
        total += static_cast<size_t>(n);
    }
    return true;
}

inline bool sendAll(socket_t sock, const void* buf, size_t len) {
    auto* p = static_cast<const char*>(buf);
    size_t total = 0;
    while (total < len) {
        int n = ::send(sock, p + total, static_cast<int>(len - total), 0);
        if (n <= 0) return false;
        total += static_cast<size_t>(n);
    }
    return true;
}

// Read until we find \r\n\r\n (end of HTTP headers)
inline std::string recvHttpHeaders(socket_t sock) {
    std::string buf;
    char ch;
    while (true) {
        int n = ::recv(sock, &ch, 1, 0);
        if (n <= 0) return "";
        buf += ch;
        if (buf.size() >= 4 && buf.substr(buf.size()-4) == "\r\n\r\n") break;
        if (buf.size() > 8192) return ""; // safety limit
    }
    return buf;
}

// ─── WebSocket Frame Helpers ──────────────────────────────────────────────

// Send a binary WebSocket frame (server-to-client, unmasked)
inline bool wsSendBinary(socket_t sock, const uint8_t* data, size_t len) {
    std::vector<uint8_t> frame;
    frame.push_back(0x82); // FIN + binary opcode

    if (len < 126) {
        frame.push_back(static_cast<uint8_t>(len));
    } else if (len <= 65535) {
        frame.push_back(126);
        frame.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));
        frame.push_back(static_cast<uint8_t>(len & 0xFF));
    } else {
        frame.push_back(127);
        uint64_t l = len;
        for (int i = 7; i >= 0; i--) frame.push_back(static_cast<uint8_t>((l >> (i*8)) & 0xFF));
    }
    frame.insert(frame.end(), data, data + len);
    return sendAll(sock, frame.data(), frame.size());
}

inline bool wsSendBinary(socket_t sock, const std::vector<uint8_t>& data) {
    return wsSendBinary(sock, data.data(), data.size());
}

// Send a binary WebSocket frame (client-to-server, masked)
inline bool wsSendBinaryMasked(socket_t sock, const uint8_t* data, size_t len) {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 255);
    uint8_t mask[4];
    for (int i = 0; i < 4; i++) mask[i] = static_cast<uint8_t>(dis(gen));

    std::vector<uint8_t> frame;
    frame.push_back(0x82); // FIN + binary opcode
    if (len < 126) {
        frame.push_back(static_cast<uint8_t>(0x80 | len)); // MASK bit set
    } else if (len <= 65535) {
        frame.push_back(0x80 | 126);
        frame.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));
        frame.push_back(static_cast<uint8_t>(len & 0xFF));
    } else {
        frame.push_back(0x80 | 127);
        uint64_t l = len;
        for (int i = 7; i >= 0; i--) frame.push_back(static_cast<uint8_t>((l >> (i*8)) & 0xFF));
    }
    frame.insert(frame.end(), mask, mask + 4);
    for (size_t i = 0; i < len; i++) {
        frame.push_back(data[i] ^ mask[i % 4]);
    }
    return sendAll(sock, frame.data(), frame.size());
}

inline bool wsSendBinaryMasked(socket_t sock, const std::vector<uint8_t>& data) {
    return wsSendBinaryMasked(sock, data.data(), data.size());
}

// Read one WebSocket frame (returns payload; handles unmasking)
// Returns empty vector on connection close or error.
// Sets isClose=true if a close frame was received.
struct WSFrame {
    uint8_t opcode;
    std::vector<uint8_t> payload;
    bool isClose = false;
    bool isValid = false;
};

inline bool wsSendControl(socket_t sock, uint8_t opcode, const std::vector<uint8_t>& payload, bool masked) {
    if (payload.size() > 125) return false;
    std::vector<uint8_t> out;
    out.reserve(2 + (masked ? 4 : 0) + payload.size());
    out.push_back(static_cast<uint8_t>(0x80 | (opcode & 0x0F)));
    out.push_back(static_cast<uint8_t>((masked ? 0x80 : 0x00) | payload.size()));
    uint8_t key[4] = {0, 0, 0, 0};
    if (masked) {
        std::random_device rd;
        for (auto& b : key) b = static_cast<uint8_t>(rd());
        out.insert(out.end(), key, key + 4);
    }
    for (size_t i = 0; i < payload.size(); ++i) {
        out.push_back(masked ? static_cast<uint8_t>(payload[i] ^ key[i % 4]) : payload[i]);
    }
    return sendAll(sock, out.data(), out.size());
}

inline WSFrame wsRecvFrame(socket_t sock, bool expectMasked) {
    constexpr uint64_t MAX_MESSAGE_SIZE = 1024 * 1024;
    WSFrame message;
    bool fragmented = false;

    while (true) {
        WSFrame frame;
        uint8_t hdr[2];
        if (!recvAll(sock, hdr, 2)) return message;
        const bool fin = (hdr[0] & 0x80) != 0;
        if ((hdr[0] & 0x70) != 0) return message;
        frame.opcode = hdr[0] & 0x0F;
        const bool masked = (hdr[1] & 0x80) != 0;
        if (masked != expectMasked) return message;
        uint64_t payloadLen = hdr[1] & 0x7F;

        if (payloadLen == 126) {
            uint8_t ext[2];
            if (!recvAll(sock, ext, 2)) return message;
            payloadLen = (static_cast<uint64_t>(ext[0]) << 8) | ext[1];
        } else if (payloadLen == 127) {
            uint8_t ext[8];
            if (!recvAll(sock, ext, 8) || (ext[0] & 0x80) != 0) return message;
            payloadLen = 0;
            for (uint8_t b : ext) payloadLen = (payloadLen << 8) | b;
        }

        const bool control = frame.opcode >= 0x08;
        if ((control && (!fin || payloadLen > 125)) || payloadLen > MAX_MESSAGE_SIZE ||
            message.payload.size() + payloadLen > MAX_MESSAGE_SIZE) return message;

        uint8_t mask[4] = {0, 0, 0, 0};
        if (masked && !recvAll(sock, mask, 4)) return message;
        frame.payload.resize(static_cast<size_t>(payloadLen));
        if (payloadLen > 0 && !recvAll(sock, frame.payload.data(), frame.payload.size())) return message;
        if (masked) {
            for (size_t i = 0; i < frame.payload.size(); ++i) frame.payload[i] ^= mask[i % 4];
        }

        if (frame.opcode == 0x08) {
            wsSendControl(sock, 0x08, frame.payload, !expectMasked);
            frame.isClose = true;
            frame.isValid = true;
            return frame;
        }
        if (frame.opcode == 0x09) {
            if (!wsSendControl(sock, 0x0A, frame.payload, !expectMasked)) return message;
            continue;
        }
        if (frame.opcode == 0x0A) continue;

        if (frame.opcode == 0x02 && !fragmented) {
            message.opcode = frame.opcode;
            message.payload = std::move(frame.payload);
            if (fin) { message.isValid = true; return message; }
            fragmented = true;
            continue;
        }
        if (frame.opcode == 0x00 && fragmented) {
            message.payload.insert(message.payload.end(), frame.payload.begin(), frame.payload.end());
            if (fin) { message.isValid = true; return message; }
            continue;
        }
        return WSFrame{};
    }
}

// Send a WebSocket close frame
inline bool wsSendClose(socket_t sock, const std::string& reason = "") {
    std::vector<uint8_t> frame;
    frame.push_back(0x88); // FIN + close
    if (reason.empty()) {
        frame.push_back(0x00);
    } else {
        size_t len = reason.size();
        if (len < 126) {
            frame.push_back(static_cast<uint8_t>(len));
        } else {
            frame.push_back(126);
            frame.push_back(static_cast<uint8_t>((len >> 8) & 0xFF));
            frame.push_back(static_cast<uint8_t>(len & 0xFF));
        }
        frame.insert(frame.end(), reason.begin(), reason.end());
    }
    return sendAll(sock, frame.data(), frame.size());
}

// ─── WebSocket Server (TCP accept + WS handshake) ────────────────────────

struct WSServer {
    socket_t listenSock = HWS_INVALID_SOCKET;

    bool listen(uint16_t port) {
        listenSock = ::socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (listenSock == HWS_INVALID_SOCKET) return false;

        // Allow address reuse
        int opt = 1;
        ::setsockopt(listenSock, SOL_SOCKET, SO_REUSEADDR,
                     reinterpret_cast<const char*>(&opt), sizeof(opt));

        struct sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_addr.s_addr = htonl(INADDR_ANY);
        addr.sin_port = htons(port);

        if (::bind(listenSock, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr)) == HWS_SOCKET_ERROR) {
            hws_close(listenSock);
            listenSock = HWS_INVALID_SOCKET;
            return false;
        }
        if (::listen(listenSock, 5) == HWS_SOCKET_ERROR) {
            hws_close(listenSock);
            listenSock = HWS_INVALID_SOCKET;
            return false;
        }
        return true;
    }

    // Accept a connection and perform WebSocket handshake.
    // Returns the client socket, or HWS_INVALID_SOCKET on failure.
    // userId is extracted from the HTTP headers (or generated).
    socket_t accept(std::string& userId) {
        struct sockaddr_in clientAddr{};
#ifdef _WIN32
        int addrLen = sizeof(clientAddr);
#else
        socklen_t addrLen = sizeof(clientAddr);
#endif
        socket_t clientSock = ::accept(listenSock,
            reinterpret_cast<struct sockaddr*>(&clientAddr), &addrLen);
        if (clientSock == HWS_INVALID_SOCKET) return HWS_INVALID_SOCKET;

        // Read HTTP headers
        std::string httpText = recvHttpHeaders(clientSock);
        if (httpText.empty()) {
            hws_close(clientSock);
            return HWS_INVALID_SOCKET;
        }
        const std::string expectedPath = std::getenv("WS_PATH") ? std::getenv("WS_PATH") : "/ws";
        if (httpText.rfind("GET " + expectedPath + " HTTP/1.1\r\n", 0) != 0 ||
            extractHeader(httpText, "Upgrade") != "websocket") {
            hws_close(clientSock);
            return HWS_INVALID_SOCKET;
        }

        // Extract Sec-WebSocket-Key
        std::string wsKey = extractHeader(httpText, "Sec-WebSocket-Key");
        if (wsKey.empty()) {
            hws_close(clientSock);
            return HWS_INVALID_SOCKET;
        }

        // Extract userId header
        userId = extractHeader(httpText, "userId");
        userId.erase(std::remove_if(userId.begin(), userId.end(), [](unsigned char c) { return std::iscntrl(c); }), userId.end());
        if (userId.size() > 128) userId.resize(128);
        if (userId.empty()) {
            userId = "cpp-" + std::to_string(nowMs());
        }

        // Compute accept key
        std::string acceptKey = wsAcceptKey(wsKey);

        // Send 101 response
        std::string response =
            "HTTP/1.1 101 Switching Protocols\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            "Sec-WebSocket-Accept: " + acceptKey + "\r\n"
            "\r\n";
        if (!sendAll(clientSock, response.data(), response.size())) {
            hws_close(clientSock);
            return HWS_INVALID_SOCKET;
        }

        return clientSock;
    }

    void close() {
        if (listenSock != HWS_INVALID_SOCKET) {
            hws_close(listenSock);
            listenSock = HWS_INVALID_SOCKET;
        }
    }
};

// ─── WebSocket Client (TCP connect + WS handshake) ───────────────────────

struct WSClient {
    socket_t sock = HWS_INVALID_SOCKET;

    bool connect(const std::string& host, uint16_t port, const std::string& userId) {
        sock = ::socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (sock == HWS_INVALID_SOCKET) return false;

        struct sockaddr_in addr{};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(port);
        if (inet_pton(AF_INET, host.c_str(), &addr.sin_addr) <= 0) {
            // Try hostname resolution
            struct addrinfo hints{}, *res;
            memset(&hints, 0, sizeof(hints));
            hints.ai_family = AF_INET;
            hints.ai_socktype = SOCK_STREAM;
            std::string portStr = std::to_string(port);
            if (getaddrinfo(host.c_str(), portStr.c_str(), &hints, &res) != 0) {
                hws_close(sock);
                sock = HWS_INVALID_SOCKET;
                return false;
            }
            // Copy the first result
            memcpy(&addr, res->ai_addr, sizeof(struct sockaddr_in));
            freeaddrinfo(res);
        }

        if (::connect(sock, reinterpret_cast<struct sockaddr*>(&addr), sizeof(addr)) == HWS_SOCKET_ERROR) {
            hws_close(sock);
            sock = HWS_INVALID_SOCKET;
            return false;
        }

        // Send WebSocket upgrade request
        std::string wsKey = generateWSKey();
        const std::string path = std::getenv("WS_PATH") ? std::getenv("WS_PATH") : "/ws";
        std::string request =
            "GET " + path + " HTTP/1.1\r\n"
            "Host: " + host + ":" + std::to_string(port) + "\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            "Sec-WebSocket-Key: " + wsKey + "\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            "userId: " + userId + "\r\n"
            "\r\n";
        if (!sendAll(sock, request.data(), request.size())) {
            hws_close(sock);
            sock = HWS_INVALID_SOCKET;
            return false;
        }

        // Receive 101 response
        std::string response = recvHttpHeaders(sock);
        if (response.empty()) {
            hws_close(sock);
            sock = HWS_INVALID_SOCKET;
            return false;
        }

        if (response.rfind("HTTP/1.1 101 ", 0) != 0 || extractHeader(response, "Sec-WebSocket-Accept") != wsAcceptKey(wsKey)) {
            hws_close(sock);
            sock = HWS_INVALID_SOCKET;
            return false;
        }

        return true;
    }

    void close() {
        if (sock != HWS_INVALID_SOCKET) {
            hws_close(sock);
            sock = HWS_INVALID_SOCKET;
        }
    }
};

} // namespace hws
