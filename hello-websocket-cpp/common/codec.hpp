// Hello WebSocket Protocol Codec - C++ implementation.
// Header-only binary codec implementing PROTOCOL.md.
#pragma once

#include <cstdint>
#include <cstring>
#include <string>
#include <vector>
#include <map>
#include <stdexcept>
#include <sstream>
#include <chrono>
#include <iomanip>
#include <algorithm>
#include <iostream>
#include <random>

namespace hws {

// ─── Constants ───────────────────────────────────────────────────────────

constexpr uint16_t PORT = 9898;
constexpr uint8_t MAGIC = 0x48;
constexpr uint8_t VERSION = 0x01;
constexpr size_t HEADER_LEN = 8;
constexpr const char* SERVER_LANG = "CPP";
constexpr const char* CLIENT_LANG = "CPP";

// Message types
constexpr uint8_t MSG_HELLO = 0x01;
constexpr uint8_t MSG_BONJOUR = 0x02;
constexpr uint8_t MSG_ECHO_REQUEST = 0x03;
constexpr uint8_t MSG_ECHO_RESPONSE = 0x04;
constexpr uint8_t MSG_KISS_REQUEST = 0x05;
constexpr uint8_t MSG_KISS_RESPONSE = 0x06;
constexpr uint8_t MSG_PING = 0x07;
constexpr uint8_t MSG_PONG = 0x08;
constexpr uint8_t MSG_TIME_NOTIFICATION = 0x09;
constexpr uint8_t MSG_RANDOM_NUMBER = 0x0A;
constexpr uint8_t MSG_HASH_RESPONSE = 0x0B;
constexpr uint8_t MSG_DISCONNECT = 0x0C;
constexpr uint8_t MSG_ERROR = 0x7F;

// Error codes
constexpr int32_t ERR_DECODE = 0x01;
constexpr int32_t ERR_UNKNOWN_MSG_TYPE = 0x02;
constexpr int32_t ERR_TRUNCATED_PAYLOAD = 0x03;
constexpr int32_t ERR_BAD_MAGIC = 0x04;
constexpr int32_t ERR_BAD_VERSION = 0x05;
constexpr int32_t ERR_SESSION_NOT_FOUND = 0x06;
constexpr int32_t ERR_INTERNAL = 0x07;

// Intervals (ms)
constexpr uint64_t PING_INTERVAL_MS = 1000;
constexpr uint64_t SESSION_TIMEOUT_MS = 60000;
constexpr uint64_t TIME_INTERVAL_MS = 5000;
constexpr uint64_t RANDOM_INTERVAL_MS = 5000;
constexpr uint64_t KISS_INTERVAL_MS = 5000;

// ─── ByteWriter ──────────────────────────────────────────────────────────

class ByteWriter {
public:
    void writeU8(uint8_t v) { buf_.push_back(v); }
    void writeU16(uint16_t v) { buf_.push_back((v >> 8) & 0xFF); buf_.push_back(v & 0xFF); }
    void writeU32(uint32_t v) {
        buf_.push_back((v >> 24) & 0xFF); buf_.push_back((v >> 16) & 0xFF);
        buf_.push_back((v >> 8) & 0xFF); buf_.push_back(v & 0xFF);
    }
    void writeI32(int32_t v) { writeU32(static_cast<uint32_t>(v)); }
    void writeI64(int64_t v) {
        writeU32(static_cast<uint32_t>(v >> 32));
        writeU32(static_cast<uint32_t>(v & 0xFFFFFFFF));
    }
    void writeString(const std::string& s) {
        writeU32(static_cast<uint32_t>(s.size()));
        buf_.insert(buf_.end(), s.begin(), s.end());
    }
    void writeKV(const std::map<std::string, std::string>& m) {
        writeU32(static_cast<uint32_t>(m.size()));
        for (const auto& [k, v] : m) { writeString(k); writeString(v); }
    }
    std::vector<uint8_t> data() const { return buf_; }
private:
    std::vector<uint8_t> buf_;
};

// ─── ByteReader ─────────────────────────────────────────────────────────

class ByteReader {
public:
    ByteReader(const uint8_t* data, size_t len) : data_(data), len_(len), pos_(0) {}
    size_t remaining() const { return len_ - pos_; }

    uint8_t readU8() {
        check(1); return data_[pos_++];
    }
    uint16_t readU16() {
        check(2); uint16_t v = (data_[pos_] << 8) | data_[pos_+1]; pos_ += 2; return v;
    }
    uint32_t readU32() {
        check(4); uint32_t v = (static_cast<uint32_t>(data_[pos_]) << 24) | (static_cast<uint32_t>(data_[pos_+1]) << 16) | (static_cast<uint32_t>(data_[pos_+2]) << 8) | data_[pos_+3]; pos_ += 4; return v;
    }
    int32_t readI32() { return static_cast<int32_t>(readU32()); }
    int64_t readI64() {
        uint32_t hi = readU32(); uint32_t lo = readU32();
        return (static_cast<int64_t>(hi) << 32) | static_cast<int64_t>(lo);
    }
    std::string readString() {
        auto ln = readU32();
        if (pos_ + ln > len_) throw std::runtime_error("string length exceeds data");
        std::string s(reinterpret_cast<const char*>(data_ + pos_), ln);
        pos_ += ln; return s;
    }
    std::map<std::string, std::string> readKV() {
        auto count = readU32();
        if (count > remaining() / 8) throw std::runtime_error("kv count exceeds remaining payload");
        std::map<std::string, std::string> m;
        for (uint32_t i = 0; i < count; i++) {
            auto key = readString();
            auto val = readString();
            m[std::move(key)] = std::move(val);
        }
        return m;
    }
private:
    void check(size_t n) { if (pos_ + n > len_) throw std::runtime_error("unexpected end of data"); }
    const uint8_t* data_; size_t len_; size_t pos_;
};

// ─── Frame Codec ────────────────────────────────────────────────────────

inline std::vector<uint8_t> encodeFrame(uint8_t msgType, const std::vector<uint8_t>& payload) {
    std::vector<uint8_t> buf(HEADER_LEN + payload.size());
    buf[0] = MAGIC; buf[1] = VERSION; buf[2] = msgType; buf[3] = 0x00;
    uint32_t len = static_cast<uint32_t>(payload.size());
    buf[4] = (len >> 24) & 0xFF; buf[5] = (len >> 16) & 0xFF;
    buf[6] = (len >> 8) & 0xFF; buf[7] = len & 0xFF;
    std::copy(payload.begin(), payload.end(), buf.begin() + HEADER_LEN);
    return buf;
}

struct Frame { uint8_t msgType; std::vector<uint8_t> payload; };

inline Frame decodeFrame(const uint8_t* data, size_t len) {
    if (len < HEADER_LEN) throw std::runtime_error("frame too short: " + std::to_string(len));
    if (data[0] != MAGIC) throw std::runtime_error("bad magic");
    if (data[1] != VERSION) throw std::runtime_error("bad version");
    uint8_t msgType = data[2];
    uint32_t payloadLen = (static_cast<uint32_t>(data[4]) << 24) | (static_cast<uint32_t>(data[5]) << 16)
                        | (static_cast<uint32_t>(data[6]) << 8) | data[7];
    if (payloadLen != len - HEADER_LEN) throw std::runtime_error("payload length mismatch");
    return {msgType, std::vector<uint8_t>(data + HEADER_LEN, data + HEADER_LEN + payloadLen)};
}

// ─── SHA-256 (minimal implementation for hashNumber) ─────────────────────

namespace detail {
    inline uint32_t rotr(uint32_t x, uint32_t n) { return (x >> n) | (x << (32 - n)); }

    inline void sha256(const uint8_t* data, size_t len, uint8_t out[32]) {
        static const uint32_t K[64] = {
            0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
            0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
            0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
            0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
            0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
            0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
            0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
            0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
        };
        uint32_t h[8] = {0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};

        // Pad message
        std::vector<uint8_t> msg(data, data + len);
        msg.push_back(0x80);
        while (msg.size() % 64 != 56) msg.push_back(0);
        uint64_t bitLen = static_cast<uint64_t>(len) * 8;
        for (int i = 7; i >= 0; i--) msg.push_back(static_cast<uint8_t>(bitLen >> (i * 8)));

        // Process blocks
        for (size_t off = 0; off < msg.size(); off += 64) {
            uint32_t w[64];
            for (int i = 0; i < 16; i++) {
                w[i] = (static_cast<uint32_t>(msg[off + i*4]) << 24) | (static_cast<uint32_t>(msg[off + i*4+1]) << 16)
                     | (static_cast<uint32_t>(msg[off + i*4+2]) << 8) | msg[off + i*4+3];
            }
            for (int i = 16; i < 64; i++) {
                uint32_t s0 = rotr(w[i-15], 7) ^ rotr(w[i-15], 18) ^ (w[i-15] >> 3);
                uint32_t s1 = rotr(w[i-2], 17) ^ rotr(w[i-2], 19) ^ (w[i-2] >> 10);
                w[i] = w[i-16] + s0 + w[i-7] + s1;
            }
            uint32_t a=h[0],b=h[1],c=h[2],d=h[3],e=h[4],f=h[5],g=h[6],h_=h[7];
            for (int i = 0; i < 64; i++) {
                uint32_t S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
                uint32_t ch = (e & f) ^ (~e & g);
                uint32_t temp1 = h_ + S1 + ch + K[i] + w[i];
                uint32_t S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
                uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
                uint32_t temp2 = S0 + maj;
                h_=g; g=f; f=e; e=d+temp1; d=c; c=b; b=a; a=temp1+temp2;
            }
            h[0]+=a; h[1]+=b; h[2]+=c; h[3]+=d; h[4]+=e; h[5]+=f; h[6]+=g; h[7]+=h_;
        }
        for (int i = 0; i < 8; i++) {
            out[i*4] = (h[i] >> 24) & 0xFF; out[i*4+1] = (h[i] >> 16) & 0xFF;
            out[i*4+2] = (h[i] >> 8) & 0xFF; out[i*4+3] = h[i] & 0xFF;
        }
    }
}

// ─── Message Types ───────────────────────────────────────────────────────

struct EchoResult {
    int64_t idx;
    uint8_t type;
    std::map<std::string, std::string> kv;
};

struct Message {
    uint8_t type;
    std::string clientLanguage, serverLanguage;
    int64_t echoId; std::string echoMeta, echoData;
    int32_t echoStatus; std::vector<EchoResult> echoResults;
    std::string osName, osVersion, osRelease, osArch;
    std::string kissLanguage, kissEncoding, kissTimeZone;
    int64_t timestampMs; std::string iso8601;
    int64_t randomId, randomNumber;
    std::string hashHex;
    std::string disconnectReason;
    int32_t errorCode; std::string errorMessage;

    std::vector<uint8_t> encode() const {
        ByteWriter w;
        switch (type) {
            case MSG_HELLO: w.writeString(clientLanguage); return encodeFrame(MSG_HELLO, w.data());
            case MSG_BONJOUR: w.writeString(serverLanguage); return encodeFrame(MSG_BONJOUR, w.data());
            case MSG_ECHO_REQUEST: w.writeI64(echoId); w.writeString(echoMeta); w.writeString(echoData); return encodeFrame(MSG_ECHO_REQUEST, w.data());
            case MSG_ECHO_RESPONSE: {
                w.writeI32(echoStatus); w.writeU32(static_cast<uint32_t>(echoResults.size()));
                for (const auto& r : echoResults) { w.writeI64(r.idx); w.writeU8(r.type); w.writeKV(r.kv); }
                return encodeFrame(MSG_ECHO_RESPONSE, w.data());
            }
            case MSG_KISS_REQUEST: w.writeString(osName); w.writeString(osVersion); w.writeString(osRelease); w.writeString(osArch); return encodeFrame(MSG_KISS_REQUEST, w.data());
            case MSG_KISS_RESPONSE: w.writeString(kissLanguage); w.writeString(kissEncoding); w.writeString(kissTimeZone); return encodeFrame(MSG_KISS_RESPONSE, w.data());
            case MSG_PING: w.writeI64(timestampMs); return encodeFrame(MSG_PING, w.data());
            case MSG_PONG: w.writeI64(timestampMs); return encodeFrame(MSG_PONG, w.data());
            case MSG_TIME_NOTIFICATION: w.writeI64(timestampMs); w.writeString(iso8601); return encodeFrame(MSG_TIME_NOTIFICATION, w.data());
            case MSG_RANDOM_NUMBER: w.writeI64(randomId); w.writeI64(randomNumber); return encodeFrame(MSG_RANDOM_NUMBER, w.data());
            case MSG_HASH_RESPONSE: w.writeI64(randomId); w.writeString(hashHex); return encodeFrame(MSG_HASH_RESPONSE, w.data());
            case MSG_DISCONNECT: w.writeString(disconnectReason); return encodeFrame(MSG_DISCONNECT, w.data());
            case MSG_ERROR: w.writeI32(errorCode); w.writeString(errorMessage); return encodeFrame(MSG_ERROR, w.data());
            default: throw std::runtime_error("unknown message type");
        }
    }
};

inline Message decodeMessage(const uint8_t* data, size_t len) {
    auto frame = decodeFrame(data, len);
    ByteReader r(frame.payload.data(), frame.payload.size());
    Message m{}; m.type = frame.msgType;
    switch (frame.msgType) {
        case MSG_HELLO: m.clientLanguage = r.readString(); break;
        case MSG_BONJOUR: m.serverLanguage = r.readString(); break;
        case MSG_ECHO_REQUEST: m.echoId = r.readI64(); m.echoMeta = r.readString(); m.echoData = r.readString(); break;
        case MSG_ECHO_RESPONSE: {
            m.echoStatus = r.readI32();
            auto count = r.readU32();
            if (count > r.remaining() / 13) throw std::runtime_error("result count exceeds remaining payload");
            for (uint32_t i = 0; i < count; i++) {
                EchoResult er;
                er.idx = r.readI64();
                er.type = r.readU8();
                er.kv = r.readKV();
                m.echoResults.push_back(std::move(er));
            }
            break;
        }
        case MSG_KISS_REQUEST: m.osName = r.readString(); m.osVersion = r.readString(); m.osRelease = r.readString(); m.osArch = r.readString(); break;
        case MSG_KISS_RESPONSE: m.kissLanguage = r.readString(); m.kissEncoding = r.readString(); m.kissTimeZone = r.readString(); break;
        case MSG_PING: m.timestampMs = r.readI64(); break;
        case MSG_PONG: m.timestampMs = r.readI64(); break;
        case MSG_TIME_NOTIFICATION: m.timestampMs = r.readI64(); m.iso8601 = r.readString(); break;
        case MSG_RANDOM_NUMBER: m.randomId = r.readI64(); m.randomNumber = r.readI64(); break;
        case MSG_HASH_RESPONSE: m.randomId = r.readI64(); m.hashHex = r.readString(); break;
        case MSG_DISCONNECT: m.disconnectReason = r.readString(); break;
        case MSG_ERROR: m.errorCode = r.readI32(); m.errorMessage = r.readString(); break;
        default: throw std::runtime_error("unknown message type");
    }
    return m;
}

// ─── Utility ─────────────────────────────────────────────────────────────

inline int64_t nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
}

inline std::tm gmtimeSafe(std::time_t t) {
    std::tm tm{};
#ifdef _WIN32
    gmtime_s(&tm, &t);
#else
    gmtime_r(&t, &tm);
#endif
    return tm;
}

inline std::string nowISO() {
    auto t = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    std::tm tm = gmtimeSafe(t);
    std::ostringstream ss;
    ss << std::put_time(&tm, "%Y-%m-%dT%H:%M:%SZ");
    return ss.str();
}

inline std::string hashNumber(int64_t num) {
    std::string s = std::to_string(num);
    uint8_t hash[32];
    detail::sha256(reinterpret_cast<const uint8_t*>(s.data()), s.size(), hash);
    std::ostringstream ss;
    for (int i = 0; i < 5; i++) ss << std::hex << std::setw(2) << std::setfill('0') << static_cast<int>(hash[i]);
    return ss.str();
}

inline void log(const char* name, const std::string& msg) {
    auto t = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    std::tm tm = gmtimeSafe(t);
    std::cout << "[" << std::put_time(&tm, "%Y-%m-%d %H:%M:%S") << "] [INFO] [" << name << "] " << msg << "\n";
    std::cout.flush();
}

} // namespace hws
