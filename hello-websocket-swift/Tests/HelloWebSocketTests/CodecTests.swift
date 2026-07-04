import Testing
@testable import HelloWebSocket

@Suite struct CodecTests {

    // MARK: - Writer Tests

    @Test func writeU8() {
        let w = ByteWriter()
        w.writeU8(0x48)
        #expect(w.data() == [0x48])
    }

    @Test func writeU16() {
        let w = ByteWriter()
        w.writeU16(0x0102)
        #expect(w.data() == [0x01, 0x02])
    }

    @Test func writeU32() {
        let w = ByteWriter()
        w.writeU32(0x01020304)
        #expect(w.data() == [0x01, 0x02, 0x03, 0x04])
    }

    @Test func writeI64Positive() {
        let w = ByteWriter()
        w.writeI64(0x0102030405060708)
        #expect(w.data() == [0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08])
    }

    @Test func writeI64Negative() {
        let w = ByteWriter()
        w.writeI64(-1)
        #expect(w.data() == [0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF])
    }

    @Test func writeString() {
        let w = ByteWriter()
        w.writeString("Go")
        #expect(w.data() == [0x00, 0x00, 0x00, 0x02, 0x47, 0x6F])
    }

    @Test func writeKv() {
        let w = ByteWriter()
        w.writeKv([("a", "1")])
        #expect(w.data() == [
            0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x01, 0x61,
            0x00, 0x00, 0x00, 0x01, 0x31
        ])
    }

    // MARK: - Reader Tests

    @Test func readU8() throws {
        let r = ByteReader([0x48])
        #expect(try r.readU8() == 0x48)
    }

    @Test func readU32() throws {
        let r = ByteReader([0x01, 0x02, 0x03, 0x04])
        #expect(try r.readU32() == 0x01020304)
    }

    @Test func readI64Positive() throws {
        let r = ByteReader([0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08])
        #expect(try r.readI64() == 0x0102030405060708)
    }

    @Test func readI64Negative() throws {
        let r = ByteReader([0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF])
        #expect(try r.readI64() == -1)
    }

    @Test func readString() throws {
        let r = ByteReader([0x00, 0x00, 0x00, 0x02, 0x47, 0x6F])
        #expect(try r.readString() == "Go")
    }

    @Test func readKv() throws {
        let r = ByteReader([
            0x00, 0x00, 0x00, 0x01,
            0x00, 0x00, 0x00, 0x01, 0x61,
            0x00, 0x00, 0x00, 0x01, 0x31
        ])
        let kv = try r.readKv()
        #expect(kv.count == 1)
        #expect(kv[0].0 == "a")
        #expect(kv[0].1 == "1")
    }

    // MARK: - Frame Codec Tests

    @Test func encodeFrameTest() {
        let payload: [UInt8] = [0x47, 0x6F]
        let frame = HelloWebSocket.encodeFrame(.hello, payload: payload)
        #expect(frame == [0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x02, 0x47, 0x6F])
    }

    @Test func decodeFrameTest() throws {
        let data: [UInt8] = [0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x02, 0x47, 0x6F]
        let (msgType, payload) = try HelloWebSocket.decodeFrame(data)
        #expect(msgType == .hello)
        #expect(payload == [0x47, 0x6F])
    }

    @Test func decodeFrameBadMagic() {
        let data: [UInt8] = [0x49, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00]
        #expect(throws: CodecError.self) { _ = try decodeFrame(data) }
    }

    @Test func decodeFrameBadVersion() {
        let data: [UInt8] = [0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00]
        #expect(throws: CodecError.self) { _ = try decodeFrame(data) }
    }

    @Test func decodeFrameTruncated() {
        let data: [UInt8] = [0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00]
        #expect(throws: CodecError.self) { _ = try decodeFrame(data) }
    }

    // MARK: - Message Tests

    @Test func helloEncodeDecode() throws {
        let w = ByteWriter()
        w.writeString("SWIFT")
        let frame = HelloWebSocket.encodeFrame(.hello, payload: w.data())
        let msg = try HelloWebSocket.decodeMessage(frame)
        #expect(msg.type == MsgType.hello)
        #expect(msg.clientLanguage == "SWIFT")
    }

    @Test func hashNumberTest() {
        // SHA256("0") = 5feceb66ffc86f38d952786c6d696c79c2dbc239...
        #expect(HelloWebSocket.hashNumber(0) == "5feceb66ff")
        // SHA256("42") = 73475cb40a568e8da8a045ced1...
        #expect(HelloWebSocket.hashNumber(42) == "73475cb40a")
        // SHA256("-1") = 1bad6b8cf9e5b1d9...
        #expect(HelloWebSocket.hashNumber(-1) == "1bad6b8cf9")
    }

    @Test func largeI64() throws {
        let values: [Int64] = [
            9223372036854775807,
            -9223372036854775808,
            4068648446728183402,
            -9107021915669125824
        ]
        for v in values {
            let w = ByteWriter()
            w.writeI64(v)
            let r = ByteReader(w.data())
            #expect(try r.readI64() == v)
        }
    }

    @Test func echoResponseDecode() throws {
        let w = ByteWriter()
        w.writeI32(200)
        w.writeU32(1)
        w.writeI64(12345)
        w.writeU8(0)
        w.writeKv([("id", "1"), ("data", "hello")])
        let frame = HelloWebSocket.encodeFrame(.echoResponse, payload: w.data())
        let msg = try HelloWebSocket.decodeMessage(frame)
        #expect(msg.type == MsgType.echoResponse)
        #expect(msg.echoStatus == 200)
        #expect(msg.echoResults?.count == 1)
        #expect(msg.echoResults?[0].idx == 12345)
        #expect(msg.echoResults?[0].type == 0)
    }

    @Test func timeNotificationDecode() throws {
        let w = ByteWriter()
        w.writeI64(1234567890)
        w.writeString("2026-07-03T14:22:01Z")
        let frame = HelloWebSocket.encodeFrame(.timeNotification, payload: w.data())
        let msg = try HelloWebSocket.decodeMessage(frame)
        #expect(msg.type == MsgType.timeNotification)
        #expect(msg.timestampMs == 1234567890)
        #expect(msg.iso8601 == "2026-07-03T14:22:01Z")
    }
}
