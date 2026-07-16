using System.Net.WebSockets;
using System.Threading.Channels;

namespace HelloWebSocket;

public class WsClient
{
    private const int MaxMessageBytes = 1024 * 1024;
    private readonly string _host;
    private readonly int _port;
    private readonly Random _rng = new();

    public WsClient(string host, int port) { _host = host; _port = port; }

    public async Task RunAsync()
    {
        var path = Environment.GetEnvironmentVariable("WS_PATH") ?? "/ws";
        var url = $"ws://{_host}:{_port}{path}";
        Codec.Log("ws-client", "Starting C# WebSocket client [version: 1.0.0]");
        Codec.Log("ws-client", $"Connecting to {url}");

        for (var attempt = 1; attempt <= 3; attempt++)
        {
            Codec.Log("ws-client", $"Connection attempt {attempt}/3 to {url}");
            try { await TryConnect(url); return; }
            catch (Exception e)
            {
                Codec.Log("ws-client", $"Error: {e.Message}");
                if (attempt < 3) await Task.Delay(2000);
            }
        }

        Codec.Log("ws-client", "Failed to connect after 3 attempts");
        Environment.ExitCode = 1;
    }

    private async Task TryConnect(string url)
    {
        using var ws = new ClientWebSocket();
        using var cts = new CancellationTokenSource();
        ws.Options.SetRequestHeader("userId", $"csharp-client-{Guid.NewGuid().ToString()[..8]}");
        await ws.ConnectAsync(new Uri(url), cts.Token);
        Codec.Log("ws-client", "Connected");

        var outgoing = Channel.CreateBounded<byte[]>(new BoundedChannelOptions(256)
        {
            FullMode = BoundedChannelFullMode.DropWrite,
            SingleReader = true,
            SingleWriter = false,
        });
        var sendTask = RunSendTask(ws, outgoing.Reader, cts.Token);
        outgoing.Writer.TryWrite(Codec.Hello(Codec.CLIENT_LANG).Encode());

        var randomTask = RunRandomTask(outgoing.Writer, cts.Token);
        try
        {
            while (ws.State == WebSocketState.Open)
            {
                var data = await ReceiveMessageAsync(ws, cts.Token);
                if (data is null) break;
                HandleMessage(data, outgoing.Writer);
            }
        }
        catch (OperationCanceledException) when (cts.IsCancellationRequested) { }
        catch (WebSocketException) { }
        finally
        {
            if (ws.State == WebSocketState.Open)
                outgoing.Writer.TryWrite(Codec.DisconnectMsg("client shutdown").Encode());
            outgoing.Writer.TryComplete();
            try { await sendTask; } catch (OperationCanceledException) { }
            cts.Cancel();
            try { await randomTask; } catch (OperationCanceledException) { }
            if (ws.State == WebSocketState.Open)
            {
                try { await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "shutdown", CancellationToken.None); }
                catch { ws.Abort(); }
            }
        }
    }

    private async Task RunRandomTask(ChannelWriter<byte[]> outgoing, CancellationToken ct)
    {
        var randomId = 1L;
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(Codec.RANDOM_INTERVAL_MS));
        while (await timer.WaitForNextTickAsync(ct))
        {
            var num = _rng.NextInt64();
            if (!outgoing.TryWrite(Codec.RandomNumberMsg(randomId, num).Encode())) break;
            Codec.Log("ws-client", $"RANDOM_NUMBER id={randomId} number={num}");
            randomId++;
        }
    }

    private static async Task RunSendTask(ClientWebSocket ws, ChannelReader<byte[]> outgoing, CancellationToken ct)
    {
        await foreach (var data in outgoing.ReadAllAsync(ct))
        {
            if (ws.State != WebSocketState.Open) break;
            await ws.SendAsync(data, WebSocketMessageType.Binary, true, ct);
        }
    }

    private static async Task<byte[]?> ReceiveMessageAsync(ClientWebSocket ws, CancellationToken ct)
    {
        var buffer = new byte[16 * 1024];
        using var message = new MemoryStream();
        while (true)
        {
            var result = await ws.ReceiveAsync(buffer, ct);
            if (result.MessageType == WebSocketMessageType.Close) return null;
            if (result.MessageType != WebSocketMessageType.Binary)
                throw new WebSocketException(WebSocketError.UnsupportedProtocol, "Only binary messages are supported");
            if (message.Length + result.Count > MaxMessageBytes)
                throw new WebSocketException(WebSocketError.HeaderError, "Message exceeds 1 MiB limit");
            message.Write(buffer, 0, result.Count);
            if (result.EndOfMessage) return message.ToArray();
        }
    }

    private static void HandleMessage(byte[] data, ChannelWriter<byte[]> outgoing)
    {
        Codec.Message msg;
        try { msg = Codec.DecodeMessage(data); }
        catch (Exception e) { Codec.Log("ws-client", $"Decode error: {e.Message}"); return; }

        switch (msg.Type)
        {
            case Codec.MSG_BONJOUR:
                Codec.Log("ws-client", $"BONJOUR server_language={msg.ServerLanguage}");
                break;
            case Codec.MSG_PING:
                outgoing.TryWrite(Codec.Pong(msg.TimestampMs).Encode());
                break;
            case Codec.MSG_TIME_NOTIFICATION:
                Codec.Log("ws-client", $"TIME_NOTIFICATION ts={msg.TimestampMs} iso={msg.Iso8601}");
                break;
            case Codec.MSG_KISS_REQUEST:
                outgoing.TryWrite(Codec.KissResponse("en_US", "UTF-8", TimeZoneInfo.Local.Id).Encode());
                break;
            case Codec.MSG_ECHO_RESPONSE:
                Codec.Log("ws-client", $"ECHO_RESPONSE status={msg.EchoStatus} results={msg.EchoResults!.Length}");
                break;
            case Codec.MSG_HASH_RESPONSE:
                Codec.Log("ws-client", $"HASH_RESPONSE id={msg.RandomId} hash={msg.HashHex}");
                break;
            case Codec.MSG_ERROR:
                Codec.Log("ws-client", $"ERROR code={msg.ErrorCode} msg={msg.ErrorMessage}");
                break;
        }
    }
}
