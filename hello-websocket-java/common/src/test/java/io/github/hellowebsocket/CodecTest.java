package io.github.hellowebsocket;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class CodecTest {

    @Test
    @DisplayName("HELLO worked example byte-level match")
    void testHelloWorkedExample() {
        Codec.Message msg = Codec.hello("Go");
        byte[] data = msg.encode();
        byte[] expected = {
            0x48, 0x01, 0x01, 0x00,
            0x00, 0x00, 0x00, 0x06,
            0x00, 0x00, 0x00, 0x02,
            0x47, 0x6F
        };
        assertArrayEquals(expected, data);
    }

    @Test
    @DisplayName("Round-trip all simple message types")
    void testRoundTripAll() throws Codec.DecodeException {
        Codec.Message[] messages = {
            Codec.hello("Java"),
            Codec.bonjour("Go"),
            Codec.echoRequest(42, "Python", "hello"),
            Codec.ping(1700000000000L),
            Codec.pong(1700000000001L),
            Codec.timeNotif(1700000000000L, "2023-11-14T22:13:20Z"),
            Codec.randomNumber(99, 42),
            Codec.hashResponse(99, "7688b6ef5a"),
            Codec.disconnect("bye"),
            Codec.error(Codec.ERR_UNKNOWN_MSG_TYPE, "bad type"),
        };
        for (Codec.Message orig : messages) {
            Codec.Message decoded = Codec.decodeMessage(orig.encode());
            assertEquals(orig.type, decoded.type);
        }
    }

    @Test
    @DisplayName("Round-trip EchoResponse")
    void testRoundTripEchoResponse() throws Codec.DecodeException {
        Map<String, String> kv = new HashMap<>();
        kv.put("id", "1");
        kv.put("data", "Hello");
        Codec.EchoResult result = new Codec.EchoResult(123, 0, kv);
        Codec.Message orig = Codec.echoResponse(200, new Codec.EchoResult[]{result});
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(Codec.MSG_ECHO_RESPONSE, decoded.type);
        assertEquals(200, decoded.echoStatus);
        assertEquals(1, decoded.echoResults.length);
        assertEquals("1", decoded.echoResults[0].kv().get("id"));
    }

    @Test
    @DisplayName("Round-trip Kiss")
    void testRoundTripKiss() throws Codec.DecodeException {
        Codec.Message orig = Codec.kissRequest("Linux", "6.6", "arch", "AMD64");
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(Codec.MSG_KISS_REQUEST, decoded.type);
        assertEquals("Linux", decoded.osName);
        assertEquals("AMD64", decoded.osArch);
    }

    @Test
    @DisplayName("Bad magic rejected")
    void testBadMagic() {
        byte[] data = {0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertThrows(Codec.DecodeException.class, () -> Codec.decodeFrame(data));
    }

    @Test
    @DisplayName("Bad version rejected")
    void testBadVersion() {
        byte[] data = {0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertThrows(Codec.DecodeException.class, () -> Codec.decodeFrame(data));
    }

    @Test
    @DisplayName("Truncated payload rejected")
    void testTruncatedPayload() {
        byte[] data = {0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, (byte)0xFF};
        assertThrows(Codec.DecodeException.class, () -> Codec.decodeFrame(data));
    }

    @Test
    @DisplayName("Hash number produces 10-char hex")
    void testHashNumber() {
        String h = Codec.hashNumber(42);
        assertEquals(10, h.length());
        assertEquals(h, Codec.hashNumber(42));
    }

    @Test
    @DisplayName("Round-trip KissResponse")
    void testRoundTripKissResponse() throws Codec.DecodeException {
        Codec.Message orig = Codec.kissResponse("en_US", "UTF-8", "UTC");
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(Codec.MSG_KISS_RESPONSE, decoded.type);
        assertEquals("en_US", decoded.kissLanguage);
        assertEquals("UTF-8", decoded.kissEncoding);
        assertEquals("UTC", decoded.kissTimeZone);
    }

    @Test
    @DisplayName("Round-trip Disconnect")
    void testRoundTripDisconnect() throws Codec.DecodeException {
        Codec.Message orig = Codec.disconnect("test reason");
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(Codec.MSG_DISCONNECT, decoded.type);
        assertEquals("test reason", decoded.disconnectReason);
    }

    @Test
    @DisplayName("Round-trip Error")
    void testRoundTripError() throws Codec.DecodeException {
        Codec.Message orig = Codec.error(Codec.ERR_DECODE, "decode failed");
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(Codec.MSG_ERROR, decoded.type);
        assertEquals(Codec.ERR_DECODE, decoded.errorCode);
        assertEquals("decode failed", decoded.errorMessage);
    }

    @Test
    @DisplayName("Round-trip RandomNumber")
    void testRoundTripRandomNumber() throws Codec.DecodeException {
        Codec.Message orig = Codec.randomNumber(5, 99999);
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(Codec.MSG_RANDOM_NUMBER, decoded.type);
        assertEquals(5, decoded.randomId);
        assertEquals(99999, decoded.randomNumber);
    }

    @Test
    @DisplayName("Round-trip HashResponse")
    void testRoundTripHashResponse() throws Codec.DecodeException {
        Codec.Message orig = Codec.hashResponse(7, "abcdef1234");
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(Codec.MSG_HASH_RESPONSE, decoded.type);
        assertEquals(7, decoded.randomId);
        assertEquals("abcdef1234", decoded.hashHex);
    }

    @Test
    @DisplayName("Empty string round-trip")
    void testEmptyString() throws Codec.DecodeException {
        Codec.Message orig = Codec.disconnect("");
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals("", decoded.disconnectReason);
    }

    @Test
    @DisplayName("Unknown message type rejected")
    void testUnknownMsgType() {
        byte[] data = {0x48, 0x01, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00};
        assertThrows(Codec.DecodeException.class, () -> Codec.decodeMessage(data));
    }

    @Test
    @DisplayName("Empty EchoResponse results array")
    void testEmptyEchoResults() throws Codec.DecodeException {
        Codec.Message orig = Codec.echoResponse(204, new Codec.EchoResult[]{});
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(204, decoded.echoStatus);
        assertEquals(0, decoded.echoResults.length);
    }

    @Test
    @DisplayName("EchoResponse with multiple results")
    void testMultipleEchoResults() throws Codec.DecodeException {
        Map<String, String> kv1 = new HashMap<>();
        kv1.put("a", "1");
        Map<String, String> kv2 = new HashMap<>();
        kv2.put("b", "2");
        Codec.EchoResult[] results = {
            new Codec.EchoResult(1, 0, kv1),
            new Codec.EchoResult(2, 1, kv2),
        };
        Codec.Message orig = Codec.echoResponse(200, results);
        Codec.Message decoded = Codec.decodeMessage(orig.encode());
        assertEquals(2, decoded.echoResults.length);
        assertEquals("1", decoded.echoResults[0].kv().get("a"));
        assertEquals("2", decoded.echoResults[1].kv().get("b"));
    }
}
