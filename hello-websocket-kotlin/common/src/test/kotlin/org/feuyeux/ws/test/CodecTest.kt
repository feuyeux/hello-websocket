// Hello WebSocket Codec Tests - Kotlin implementation.
// 16 tests matching the test suite in all other language subprojects.
package org.feuyeux.ws.test

import org.feuyeux.ws.common.*
import kotlin.test.*

class CodecTest {

    @Test fun test_hello_byte_level() {
        // HELLO with client_language = "Go" → 14 bytes total
        val data = hello("Go").encode()
        assertEquals(14, data.size, "total frame size for HELLO('Go')")
        assertEquals(0x48.toByte(), data[0], "MAGIC")
        assertEquals(0x01.toByte(), data[1], "VERSION")
        assertEquals(MSG_HELLO, data[2], "MSG_TYPE")
        assertEquals(0x00.toByte(), data[3], "FLAGS")
        // PAYLOAD_LEN = 6 (4-byte string-length + 2 bytes "Go")
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x00.toByte(), data[5])
        assertEquals(0x00.toByte(), data[6])
        assertEquals(0x06.toByte(), data[7])
        // string length = 2
        assertEquals(0x00.toByte(), data[8])
        assertEquals(0x00.toByte(), data[9])
        assertEquals(0x00.toByte(), data[10])
        assertEquals(0x02.toByte(), data[11])
        // "Go"
        assertEquals('G'.code.toByte(), data[12])
        assertEquals('o'.code.toByte(), data[13])
    }

    @Test fun test_roundtrip_simple_types() {
        val m = Message(MSG_PING).apply { timestampMs = 1234567890L }
        val decoded = decodeMessage(m.encode())
        assertEquals(MSG_PING, decoded.type)
        assertEquals(1234567890L, decoded.timestampMs)
    }

    @Test fun test_roundtrip_echo_response() {
        val m = Message(MSG_ECHO_RESPONSE).apply {
            echoStatus = 200
            echoResults = listOf(
                EchoResult(100L, 0, mapOf("id" to "1", "data" to "hello")),
                EchoResult(200L, 1, mapOf("error" to "timeout"))
            )
        }
        val decoded = decodeMessage(m.encode())
        assertEquals(200, decoded.echoStatus)
        assertEquals(2, decoded.echoResults.size)
        assertEquals(100L, decoded.echoResults[0].idx)
        assertEquals(0.toByte(), decoded.echoResults[0].type)
        assertEquals("1", decoded.echoResults[0].kv["id"])
        assertEquals("hello", decoded.echoResults[0].kv["data"])
        assertEquals(200L, decoded.echoResults[1].idx)
        assertEquals(1.toByte(), decoded.echoResults[1].type)
        assertEquals("timeout", decoded.echoResults[1].kv["error"])
    }

    @Test fun test_roundtrip_kiss_request() {
        val m = kissRequest("Linux", "6.6.0", "6", "x86_64")
        val decoded = decodeMessage(m.encode())
        assertEquals("Linux", decoded.osName)
        assertEquals("6.6.0", decoded.osVersion)
        assertEquals("6", decoded.osRelease)
        assertEquals("x86_64", decoded.osArch)
    }

    @Test fun test_bad_magic_rejected() {
        val data = hello("Go").encode()
        data[0] = 0x00 // bad magic
        assertFailsWith<Exception> { decodeMessage(data) }
    }

    @Test fun test_bad_version_rejected() {
        val data = hello("Go").encode()
        data[1] = 0x02 // bad version
        assertFailsWith<Exception> { decodeMessage(data) }
    }

    @Test fun test_truncated_payload_rejected() {
        val data = hello("Go").encode()
        val truncated = data.copyOf(10) // cut before payload ends
        assertFailsWith<Exception> { decodeMessage(truncated) }
    }

    @Test fun test_hash_number_10_chars() {
        val hash = hashNumber(42L)
        assertEquals(10, hash.length, "hash should be 10 hex chars")
        assertTrue(hash.all { it in "0123456789abcdef" }, "hash should be hex")
    }

    @Test fun test_roundtrip_kiss_response() {
        val m = kissResponse("en_US", "UTF-8", "Asia/Shanghai")
        val decoded = decodeMessage(m.encode())
        assertEquals("en_US", decoded.kissLanguage)
        assertEquals("UTF-8", decoded.kissEncoding)
        assertEquals("Asia/Shanghai", decoded.kissTimeZone)
    }

    @Test fun test_roundtrip_disconnect() {
        val m = disconnectMsg("client shutdown")
        val decoded = decodeMessage(m.encode())
        assertEquals("client shutdown", decoded.disconnectReason)
    }

    @Test fun test_roundtrip_error() {
        val m = errorMsg(0x02, "unknown type 0xAB")
        val decoded = decodeMessage(m.encode())
        assertEquals(0x02, decoded.errorCode)
        assertEquals("unknown type 0xAB", decoded.errorMessage)
    }

    @Test fun test_roundtrip_random_number() {
        val m = randomNumberMsg(5, -123456789L)
        val decoded = decodeMessage(m.encode())
        assertEquals(5L, decoded.randomId)
        assertEquals(-123456789L, decoded.randomNumber)
    }

    @Test fun test_roundtrip_hash_response() {
        val m = hashResponseMsg(3, "abcdef0123")
        val decoded = decodeMessage(m.encode())
        assertEquals(3L, decoded.randomId)
        assertEquals("abcdef0123", decoded.hashHex)
    }

    @Test fun test_empty_string_roundtrip() {
        val m = hello("")
        val decoded = decodeMessage(m.encode())
        assertEquals("", decoded.clientLanguage)
    }

    @Test fun test_unknown_message_type_rejected() {
        val data = encodeFrame(0x55, ByteArray(0))
        assertFailsWith<Exception> { decodeMessage(data) }
    }

    @Test fun test_empty_echo_results() {
        val m = Message(MSG_ECHO_RESPONSE).apply {
            echoStatus = 200
            echoResults = emptyList()
        }
        val decoded = decodeMessage(m.encode())
        assertEquals(200, decoded.echoStatus)
        assertEquals(0, decoded.echoResults.size)
    }
}
