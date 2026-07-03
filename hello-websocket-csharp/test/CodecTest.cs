using HelloWebSocket;
using Xunit;

namespace CodecTest;

public class CodecTest
{
    [Fact]
    public void TestHelloWorkedExample()
    {
        var msg = Codec.Hello("Go");
        var data = msg.Encode();
        byte[] expected = [0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00, 0x02, 0x47, 0x6F];
        Assert.Equal(expected, data);
    }

    [Fact]
    public void TestRoundTripAll()
    {
        var messages = new Codec.Message[]
        {
            Codec.Hello("C#"),
            Codec.Bonjour("Go"),
            Codec.EchoRequestMsg(42, "Python", "hello"),
            Codec.Ping(1700000000000),
            Codec.Pong(1700000000001),
            Codec.TimeNotif(1700000000000, "2023-11-14T22:13:20Z"),
            Codec.RandomNumberMsg(99, 42),
            Codec.HashResponseMsg(99, "7688b6ef5a"),
            Codec.DisconnectMsg("bye"),
            Codec.ErrorMsg(Codec.ERR_UNKNOWN_MSG_TYPE, "bad type"),
        };
        foreach (var orig in messages)
        {
            var decoded = Codec.DecodeMessage(orig.Encode());
            Assert.Equal(orig.Type, decoded.Type);
        }
    }

    [Fact]
    public void TestRoundTripEchoResponse()
    {
        var kv = new Dictionary<string, string> { ["id"] = "1", ["data"] = "Hello" };
        var orig = Codec.EchoResponseMsg(200, new[] { new Codec.EchoResult(123, 0, kv) });
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(Codec.MSG_ECHO_RESPONSE, decoded.Type);
        Assert.Equal(200, decoded.EchoStatus);
        Assert.Single(decoded.EchoResults!);
        Assert.Equal("1", decoded.EchoResults![0].Kv["id"]);
    }

    [Fact]
    public void TestRoundTripKiss()
    {
        var orig = Codec.KissRequest("Linux", "6.6", "arch", "AMD64");
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(Codec.MSG_KISS_REQUEST, decoded.Type);
        Assert.Equal("Linux", decoded.OsName);
        Assert.Equal("AMD64", decoded.OsArch);
    }

    [Fact]
    public void TestBadMagic()
    {
        var data = new byte[] { 0x00, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00 };
        Assert.Throws<Exception>(() => Codec.DecodeFrame(data));
    }

    [Fact]
    public void TestBadVersion()
    {
        var data = new byte[] { 0x48, 0x02, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00 };
        Assert.Throws<Exception>(() => Codec.DecodeFrame(data));
    }

    [Fact]
    public void TestTruncatedPayload()
    {
        var data = new byte[] { 0x48, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0xFF };
        Assert.Throws<Exception>(() => Codec.DecodeFrame(data));
    }

    [Fact]
    public void TestHashNumber()
    {
        var h = Codec.HashNumber(42);
        Assert.Equal(10, h.Length);
        Assert.Equal(h, Codec.HashNumber(42));
    }

    [Fact]
    public void TestRoundTripKissResponse()
    {
        var orig = Codec.KissResponse("en_US", "UTF-8", "UTC");
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(Codec.MSG_KISS_RESPONSE, decoded.Type);
        Assert.Equal("en_US", decoded.KissLanguage);
        Assert.Equal("UTF-8", decoded.KissEncoding);
        Assert.Equal("UTC", decoded.KissTimeZone);
    }

    [Fact]
    public void TestRoundTripDisconnect()
    {
        var orig = Codec.DisconnectMsg("test reason");
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(Codec.MSG_DISCONNECT, decoded.Type);
        Assert.Equal("test reason", decoded.DisconnectReason);
    }

    [Fact]
    public void TestRoundTripError()
    {
        var orig = Codec.ErrorMsg(Codec.ERR_DECODE, "decode failed");
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(Codec.MSG_ERROR, decoded.Type);
        Assert.Equal(Codec.ERR_DECODE, decoded.ErrorCode);
        Assert.Equal("decode failed", decoded.ErrorMessage);
    }

    [Fact]
    public void TestRoundTripRandomNumber()
    {
        var orig = Codec.RandomNumberMsg(5, 99999);
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(Codec.MSG_RANDOM_NUMBER, decoded.Type);
        Assert.Equal(5, decoded.RandomId);
        Assert.Equal(99999, decoded.RandomNumber);
    }

    [Fact]
    public void TestRoundTripHashResponse()
    {
        var orig = Codec.HashResponseMsg(7, "abcdef1234");
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(Codec.MSG_HASH_RESPONSE, decoded.Type);
        Assert.Equal(7, decoded.RandomId);
        Assert.Equal("abcdef1234", decoded.HashHex);
    }

    [Fact]
    public void TestEmptyString()
    {
        var orig = Codec.DisconnectMsg("");
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal("", decoded.DisconnectReason);
    }

    [Fact]
    public void TestUnknownMsgType()
    {
        var data = new byte[] { 0x48, 0x01, 0x55, 0x00, 0x00, 0x00, 0x00, 0x00 };
        Assert.Throws<Exception>(() => Codec.DecodeMessage(data));
    }

    [Fact]
    public void TestEmptyEchoResults()
    {
        var orig = Codec.EchoResponseMsg(204, Array.Empty<Codec.EchoResult>());
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(204, decoded.EchoStatus);
        Assert.Empty(decoded.EchoResults!);
    }

    [Fact]
    public void TestMultipleEchoResults()
    {
        var results = new[]
        {
            new Codec.EchoResult(1, 0, new Dictionary<string, string> { ["a"] = "1" }),
            new Codec.EchoResult(2, 1, new Dictionary<string, string> { ["b"] = "2" }),
        };
        var orig = Codec.EchoResponseMsg(200, results);
        var decoded = Codec.DecodeMessage(orig.Encode());
        Assert.Equal(2, decoded.EchoResults!.Length);
        Assert.Equal("1", decoded.EchoResults[0].Kv["a"]);
        Assert.Equal("2", decoded.EchoResults[1].Kv["b"]);
    }
}
