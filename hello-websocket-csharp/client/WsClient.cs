using System.Net.WebSockets;

namespace HelloWebSocket;

public class WsClient
{
    private readonly string _host;
    private readonly int _port;
    private readonly Random _rng = new();

    public WsClient(string host, int port) { _host = host; _port = port; }

    public async Task RunAsync()
    {
        var url = $"ws://{_host}:{_port}/ws";
        Codec.Log("ws-client", "Starting C# WebSocket client [version: 1.0.0]");
        Codec.Log("ws-client", $"Connecting to {url}");

        for (int attempt = 1; attempt <= 3; attempt++)
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
        Environment.Exit(1);
    }

    private async Task TryConnect(string url)
    {
        using var ws = new ClientWebSocket();
        ws.Options.SetRequestHeader("userId", $"csharp-client-{Guid.NewGuid().ToString()[..8]}");
        await ws.ConnectAsync(new Uri(url), CancellationToken.None);
        Codec.Log("ws-client", "Connected");

        // Send HELLO
        await ws.SendAsync(Codec.Hello(Codec.CLIENT_LANG).Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);

        // Random number background task
        var randomId = 1L;
        var randomTask = Task.Run(async () =>
        {
            while (ws.State == WebSocketState.Open)
            {
                await Task.Delay((int)Codec.RANDOM_INTERVAL_MS);
                if (ws.State != WebSocketState.Open) break;
                var num = _rng.NextInt64();
                await ws.SendAsync(Codec.RandomNumberMsg(randomId, num).Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
                Codec.Log("ws-client", $"RANDOM_NUMBER id={randomId} number={num}");
                randomId++;
            }
        });

        // Receive loop
        var buffer = new byte[65536];
        try
        {
            while (ws.State == WebSocketState.Open)
            {
                var result = await ws.ReceiveAsync(buffer, CancellationToken.None);
                if (result.MessageType == WebSocketMessageType.Close) break;
                if (result.Count > 0)
                {
                    var data = new byte[result.Count];
                    Array.Copy(buffer, data, result.Count);
                    HandleMessage(ws, data);
                }
            }
        }
        catch (WebSocketException) { }

        // Send DISCONNECT
        if (ws.State == WebSocketState.Open)
        {
            await ws.SendAsync(Codec.DisconnectMsg("client shutdown").Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
            await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "shutdown", CancellationToken.None);
        }
    }

    private void HandleMessage(ClientWebSocket ws, byte[] data)
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
                Codec.Log("ws-client", $"PING ts={msg.TimestampMs}");
                _ = ws.SendAsync(Codec.Pong(msg.TimestampMs).Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
                Codec.Log("ws-client", $"PONG ts={msg.TimestampMs}");
                break;

            case Codec.MSG_TIME_NOTIFICATION:
                Codec.Log("ws-client", $"TIME_NOTIFICATION ts={msg.TimestampMs} iso={msg.Iso8601}");
                break;

            case Codec.MSG_KISS_REQUEST:
                Codec.Log("ws-client", $"KISS_REQUEST os={msg.OsName} ver={msg.OsVersion} rel={msg.OsRelease} arch={msg.OsArch}");
                _ = ws.SendAsync(Codec.KissResponse("en_US", "UTF-8", TimeZoneInfo.Local.Id).Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
                Codec.Log("ws-client", "KISS_RESPONSE sent");
                break;

            case Codec.MSG_ECHO_RESPONSE:
                Codec.Log("ws-client", $"ECHO_RESPONSE status={msg.EchoStatus} results={msg.EchoResults!.Length}");
                for (int i = 0; i < msg.EchoResults.Length; i++)
                {
                    var r = msg.EchoResults[i];
                    Codec.Log("ws-client", $"  Result #{i + 1}: idx={r.Idx} type={r.Type} kv={string.Join(",", r.Kv.Select(k => $"{k.Key}={k.Value}"))}");
                }
                break;

            case Codec.MSG_HASH_RESPONSE:
                Codec.Log("ws-client", $"HASH_RESPONSE id={msg.RandomId} hash={msg.HashHex}");
                break;

            case Codec.MSG_ERROR:
                Codec.Log("ws-client", $"ERROR code={msg.ErrorCode} msg={msg.ErrorMessage}");
                break;

            default:
                Codec.Log("ws-client", $"Unknown message type: 0x{msg.Type:x2}");
                break;
        }
    }
}
