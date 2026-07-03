// Hello WebSocket Protocol Codec Tests - C++
// 16 tests matching the test pattern across all language implementations.
#include "../common/codec.hpp"
#include <cassert>
#include <vector>
#include <string>
#include <iostream>
#include <sstream>
#include <stdexcept>

using namespace hws;

static int tests_run = 0;
static int tests_passed = 0;

#define TEST(name) void name()
#define RUN_TEST(name) do { \
    tests_run++; \
    std::cout << "  [RUN] " #name " ... "; \
    try { name(); tests_passed++; std::cout << "PASS" << std::endl; } \
    catch (const std::exception& e) { std::cout << "FAIL: " << e.what() << std::endl; } \
    catch (...) { std::cout << "FAIL: unknown exception" << std::endl; } \
} while(0)

#define EXPECT_EQ(a, b) do { \
    if (!((a) == (b))) { \
        std::ostringstream _ss; _ss << "EXPECT_EQ failed at line " << __LINE__ << ": " #a " != " #b; \
        throw std::runtime_error(_ss.str()); \
    } \
} while(0)

#define EXPECT_TRUE(a) do { \
    if (!(a)) { \
        std::ostringstream _ss; _ss << "EXPECT_TRUE failed at line " << __LINE__ << ": " #a; \
        throw std::runtime_error(_ss.str()); \
} } while(0)

#define EXPECT_THROWS(expr) do { \
    bool _caught = false; \
    try { expr; } catch (...) { _caught = true; } \
    if (!_caught) { \
        std::ostringstream _ss; _ss << "EXPECT_THROWS failed at line " << __LINE__ << ": " #expr " did not throw"; \
        throw std::runtime_error(_ss.str()); \
} } while(0)

// ─── Helper: check byte arrays are equal ──────────────────────────────────

bool bytesEqual(const std::vector<uint8_t>& a, const std::vector<uint8_t>& b) {
    return a == b;
}

// ─── Tests ────────────────────────────────────────────────────────────────

TEST(test_hello_byte_level) {
    Message m{};
    m.type = MSG_HELLO;
    m.clientLanguage = "Go";
    auto data = m.encode();
    std::vector<uint8_t> expected = {
        0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x06,
        0x00, 0x00, 0x00, 0x02, 0x47, 0x6F
    };
    EXPECT_TRUE(bytesEqual(data, expected));
}

TEST(test_roundtrip_simple_types) {
    // HELLO
    { Message m{}; m.type = MSG_HELLO; m.clientLanguage = "Dart";
      auto enc = m.encode();
      auto d = decodeMessage(enc.data(), enc.size());
      EXPECT_EQ(d.type, (uint8_t)MSG_HELLO); EXPECT_EQ(d.clientLanguage, std::string("Dart")); }

    // BONJOUR
    { Message m{}; m.type = MSG_BONJOUR; m.serverLanguage = "Java";
      auto enc = m.encode();
      auto d = decodeMessage(enc.data(), enc.size());
      EXPECT_EQ(d.type, (uint8_t)MSG_BONJOUR); EXPECT_EQ(d.serverLanguage, std::string("Java")); }

    // ECHO_REQUEST
    { Message m{}; m.type = MSG_ECHO_REQUEST; m.echoId = 42; m.echoMeta = "Python"; m.echoData = "hello";
      auto enc = m.encode();
      auto d = decodeMessage(enc.data(), enc.size());
      EXPECT_EQ(d.type, (uint8_t)MSG_ECHO_REQUEST); EXPECT_EQ(d.echoId, (int64_t)42);
      EXPECT_EQ(d.echoMeta, std::string("Python")); EXPECT_EQ(d.echoData, std::string("hello")); }

    // PING
    { Message m{}; m.type = MSG_PING; m.timestampMs = 1700000000000LL;
      auto enc = m.encode();
      auto d = decodeMessage(enc.data(), enc.size());
      EXPECT_EQ(d.type, (uint8_t)MSG_PING); EXPECT_EQ(d.timestampMs, (int64_t)1700000000000LL); }

    // PONG
    { Message m{}; m.type = MSG_PONG; m.timestampMs = 1700000000001LL;
      auto enc = m.encode();
      auto d = decodeMessage(enc.data(), enc.size());
      EXPECT_EQ(d.type, (uint8_t)MSG_PONG); EXPECT_EQ(d.timestampMs, (int64_t)1700000000001LL); }

    // TIME_NOTIFICATION
    { Message m{}; m.type = MSG_TIME_NOTIFICATION; m.timestampMs = 1700000000000LL; m.iso8601 = "2023-11-14T22:13:20Z";
      auto enc = m.encode();
      auto d = decodeMessage(enc.data(), enc.size());
      EXPECT_EQ(d.type, (uint8_t)MSG_TIME_NOTIFICATION);
      EXPECT_EQ(d.timestampMs, (int64_t)1700000000000LL);
      EXPECT_EQ(d.iso8601, std::string("2023-11-14T22:13:20Z")); }
}

TEST(test_roundtrip_echo_response) {
    Message m{};
    m.type = MSG_ECHO_RESPONSE;
    m.echoStatus = 200;
    EchoResult r;
    r.idx = 123;
    r.type = 0;
    r.kv["id"] = "1";
    r.kv["data"] = "Hello";
    m.echoResults.push_back(r);
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.type, (uint8_t)MSG_ECHO_RESPONSE);
    EXPECT_EQ(d.echoStatus, 200);
    EXPECT_EQ(d.echoResults.size(), (size_t)1);
    EXPECT_EQ(d.echoResults[0].kv["id"], std::string("1"));
    EXPECT_EQ(d.echoResults[0].kv["data"], std::string("Hello"));
}

TEST(test_roundtrip_kiss_request) {
    Message m{};
    m.type = MSG_KISS_REQUEST;
    m.osName = "Linux";
    m.osVersion = "6.6";
    m.osRelease = "arch";
    m.osArch = "AMD64";
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.type, (uint8_t)MSG_KISS_REQUEST);
    EXPECT_EQ(d.osName, std::string("Linux"));
    EXPECT_EQ(d.osArch, std::string("AMD64"));
}

TEST(test_bad_magic_rejected) {
    std::vector<uint8_t> data = {0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00};
    EXPECT_THROWS(decodeFrame(data.data(), data.size()));
}

TEST(test_bad_version_rejected) {
    std::vector<uint8_t> data = {0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00};
    EXPECT_THROWS(decodeFrame(data.data(), data.size()));
}

TEST(test_truncated_payload_rejected) {
    std::vector<uint8_t> data = {0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF};
    EXPECT_THROWS(decodeFrame(data.data(), data.size()));
}

TEST(test_hash_number_10_chars) {
    auto h = hashNumber(42);
    EXPECT_EQ(h.size(), (size_t)10);
    EXPECT_EQ(h, hashNumber(42));
}

TEST(test_roundtrip_kiss_response) {
    Message m{};
    m.type = MSG_KISS_RESPONSE;
    m.kissLanguage = "en_US";
    m.kissEncoding = "UTF-8";
    m.kissTimeZone = "UTC";
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.type, (uint8_t)MSG_KISS_RESPONSE);
    EXPECT_EQ(d.kissLanguage, std::string("en_US"));
    EXPECT_EQ(d.kissEncoding, std::string("UTF-8"));
    EXPECT_EQ(d.kissTimeZone, std::string("UTC"));
}

TEST(test_roundtrip_disconnect) {
    Message m{};
    m.type = MSG_DISCONNECT;
    m.disconnectReason = "test reason";
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.type, (uint8_t)MSG_DISCONNECT);
    EXPECT_EQ(d.disconnectReason, std::string("test reason"));
}

TEST(test_roundtrip_error) {
    Message m{};
    m.type = MSG_ERROR;
    m.errorCode = ERR_DECODE;
    m.errorMessage = "decode failed";
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.type, (uint8_t)MSG_ERROR);
    EXPECT_EQ(d.errorCode, ERR_DECODE);
    EXPECT_EQ(d.errorMessage, std::string("decode failed"));
}

TEST(test_roundtrip_random_number) {
    Message m{};
    m.type = MSG_RANDOM_NUMBER;
    m.randomId = 5;
    m.randomNumber = 99999;
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.type, (uint8_t)MSG_RANDOM_NUMBER);
    EXPECT_EQ(d.randomId, (int64_t)5);
    EXPECT_EQ(d.randomNumber, (int64_t)99999);
}

TEST(test_roundtrip_hash_response) {
    Message m{};
    m.type = MSG_HASH_RESPONSE;
    m.randomId = 7;
    m.hashHex = "abcdef1234";
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.type, (uint8_t)MSG_HASH_RESPONSE);
    EXPECT_EQ(d.randomId, (int64_t)7);
    EXPECT_EQ(d.hashHex, std::string("abcdef1234"));
}

TEST(test_empty_string_roundtrip) {
    Message m{};
    m.type = MSG_DISCONNECT;
    m.disconnectReason = "";
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.disconnectReason, std::string(""));
}

TEST(test_unknown_message_type_rejected) {
    std::vector<uint8_t> data = {0x48, 0x01, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00};
    EXPECT_THROWS(decodeMessage(data.data(), data.size()));
}

TEST(test_empty_echo_results) {
    Message m{};
    m.type = MSG_ECHO_RESPONSE;
    m.echoStatus = 204;
    auto enc = m.encode();
    auto d = decodeMessage(enc.data(), enc.size());
    EXPECT_EQ(d.echoStatus, 204);
    EXPECT_EQ(d.echoResults.size(), (size_t)0);
}

// ─── Main ─────────────────────────────────────────────────────────────────

int main() {
    std::cout << "═══ Hello WebSocket C++ Codec Tests ═══" << std::endl;
    std::cout << "Running 16 tests..." << std::endl << std::endl;

    RUN_TEST(test_hello_byte_level);
    RUN_TEST(test_roundtrip_simple_types);
    RUN_TEST(test_roundtrip_echo_response);
    RUN_TEST(test_roundtrip_kiss_request);
    RUN_TEST(test_bad_magic_rejected);
    RUN_TEST(test_bad_version_rejected);
    RUN_TEST(test_truncated_payload_rejected);
    RUN_TEST(test_hash_number_10_chars);
    RUN_TEST(test_roundtrip_kiss_response);
    RUN_TEST(test_roundtrip_disconnect);
    RUN_TEST(test_roundtrip_error);
    RUN_TEST(test_roundtrip_random_number);
    RUN_TEST(test_roundtrip_hash_response);
    RUN_TEST(test_empty_string_roundtrip);
    RUN_TEST(test_unknown_message_type_rejected);
    RUN_TEST(test_empty_echo_results);

    std::cout << std::endl;
    std::cout << "═══ Results: " << tests_passed << "/" << tests_run << " passed ═══" << std::endl;

    return (tests_passed == tests_run) ? 0 : 1;
}
