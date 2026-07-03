using System.Net;
using System.Net.WebSockets;
using System.Runtime.InteropServices;

namespace HelloWebSocket;

public class WsServer
{
    private readonly HttpListener _listener = new();
    private readonly CancellationTokenSource _cts = new();

    public WsServer(int port)
    {
        _listener.Prefixes.Add($"http://127.0.0.1:{port}/ws/");
    }

    public async Task RunAsync()
    {
        _listener.Start();
        Codec.Log("ws-server", $"Starting C# WebSocket server on port {Codec.PORT}");

        try
        {
            while (!_cts.Token.IsCancellationRequested)
            {
                var ctx = await _listener.GetContextAsync();
                _ = HandleConnectionAsync(ctx);
            }
        }
        catch (HttpListenerException) when (_cts.IsCancellationRequested) { }
    }

    private async Task HandleConnectionAsync(HttpListenerContext ctx)
    {
        if (!ctx.Request.IsWebSocketRequest)
        {
            ctx.Response.StatusCode = 400;
            ctx.Response.Close();
            return;
        }

        var userId = ctx.Request.Headers["userId"] ?? $"csharp-{Guid.NewGuid().ToString()[..8]}";

        var wsCtx = await ctx.AcceptWebSocketAsync(null);
        var ws = wsCtx.WebSocket;

        var session = new Session(userId);
        Codec.Log("ws-server", $"[{userId}] session+");

        // Background tasks
        var pingTask = RunPingTask(ws, session, _cts.Token);
        var timeTask = RunTimeTask(ws, _cts.Token);
        var kissTask = RunKissTask(ws, _cts.Token);
        var timeoutTask = RunTimeoutTask(ws, session, _cts.Token);

        // Receive loop
        var buffer = new byte[65536];
        try
        {
            while (ws.State == WebSocketState.Open)
            {
                var result = await ws.ReceiveAsync(buffer, _cts.Token);
                if (result.MessageType == WebSocketMessageType.Close)
                    break;

                if (result.Count > 0)
                {
                    var data = new byte[result.Count];
                    Array.Copy(buffer, data, result.Count);
                    HandleMessage(ws, session, data);
                }
            }
        }
        catch (WebSocketException) { }
        finally
        {
            _cts.Cancel();
            try { await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "closing", CancellationToken.None); } catch { }
            Codec.Log("ws-server", $"[{userId}] session-");
        }
    }

    private void HandleMessage(WebSocket ws, Session session, byte[] data)
    {
        Codec.Message msg;
        try { msg = Codec.DecodeMessage(data); }
        catch (Exception e)
        {
            Codec.Log("ws-server", $"Decode error: {e.Message}");
            _ = ws.SendAsync(Codec.ErrorMsg(Codec.ERR_DECODE, e.Message).Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
            return;
        }

        switch (msg.Type)
        {
            case Codec.MSG_HELLO:
                session.ClientLanguage = msg.ClientLanguage!;
                Codec.Log("ws-server", $"HELLO from {msg.ClientLanguage}, session={session.SessionId}, time={Codec.NowMs()}");
                _ = ws.SendAsync(Codec.Bonjour(Codec.SERVER_LANG).Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
                break;

            case Codec.MSG_ECHO_REQUEST:
                Codec.Log("ws-server", $"ECHO_REQUEST id={msg.EchoId} meta={msg.EchoMeta} data={msg.EchoData}");
                var kv = new Dictionary<string, string>
                {
                    ["id"] = msg.EchoId.ToString(),
                    ["idx"] = msg.EchoData!,
                    ["data"] = msg.EchoData!,
                    ["meta"] = session.ClientLanguage,
                };
                var resp = Codec.EchoResponseMsg(200, new[] { new Codec.EchoResult(Codec.NowMs(), 0, kv) });
                _ = ws.SendAsync(resp.Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
                break;

            case Codec.MSG_KISS_RESPONSE:
                Codec.Log("ws-server", $"KISS_RESPONSE lang={msg.KissLanguage} enc={msg.KissEncoding} tz={msg.KissTimeZone}");
                break;

            case Codec.MSG_PONG:
                session.LastPongTs = msg.TimestampMs;
                Codec.Log("ws-server", $"PONG ts={msg.TimestampMs}");
                break;

            case Codec.MSG_RANDOM_NUMBER:
                Codec.Log("ws-server", $"RANDOM_NUMBER id={msg.RandomId} number={msg.RandomNumber}");
                var hash = Codec.HashNumber(msg.RandomNumber);
                _ = ws.SendAsync(Codec.HashResponseMsg(msg.RandomId, hash).Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
                Codec.Log("ws-server", $"HASH_RESPONSE id={msg.RandomId} hash={hash}");
                break;

            case Codec.MSG_DISCONNECT:
                Codec.Log("ws-server", $"DISCONNECT reason={msg.DisconnectReason}");
                _ = ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "disconnect", CancellationToken.None);
                break;

            case Codec.MSG_ERROR:
                Codec.Log("ws-server", $"ERROR code={msg.ErrorCode} msg={msg.ErrorMessage}");
                break;

            default:
                Codec.Log("ws-server", $"Unknown message type: 0x{msg.Type:x2}");
                _ = ws.SendAsync(Codec.ErrorMsg(Codec.ERR_UNKNOWN_MSG_TYPE, $"unknown type 0x{msg.Type:x2}").Encode(), WebSocketMessageType.Binary, true, CancellationToken.None);
                break;
        }
    }

    private async Task RunPingTask(WebSocket ws, Session session, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && ws.State == WebSocketState.Open)
        {
            await Task.Delay((int)Codec.PING_INTERVAL_MS, ct);
            if (ws.State == WebSocketState.Open)
                await ws.SendAsync(Codec.Ping(Codec.NowMs()).Encode(), WebSocketMessageType.Binary, true, ct);
        }
    }

    private async Task RunTimeTask(WebSocket ws, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && ws.State == WebSocketState.Open)
        {
            await Task.Delay((int)Codec.TIME_INTERVAL_MS, ct);
            if (ws.State == WebSocketState.Open)
                await ws.SendAsync(Codec.TimeNotif(Codec.NowMs(), Codec.NowISO()).Encode(), WebSocketMessageType.Binary, true, ct);
        }
    }

    private async Task RunKissTask(WebSocket ws, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && ws.State == WebSocketState.Open)
        {
            await Task.Delay((int)Codec.KISS_INTERVAL_MS, ct);
            if (ws.State == WebSocketState.Open)
                await ws.SendAsync(Codec.KissRequest(Environment.OSVersion.Platform.ToString(), "unknown", "unknown", RuntimeInformation.OSArchitecture.ToString()).Encode(), WebSocketMessageType.Binary, true, ct);
        }
    }

    private async Task RunTimeoutTask(WebSocket ws, Session session, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested && ws.State == WebSocketState.Open)
        {
            await Task.Delay(5000, ct);
            if (Codec.NowMs() - session.LastPongTs > Codec.SESSION_TIMEOUT_MS)
            {
                Codec.Log("ws-server", $"[{session.UserId}] session timeout");
                _ = ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "timeout", ct);
                return;
            }
        }
    }

    class Session(string userId)
    {
        public string UserId { get; } = userId;
        public string SessionId { get; } = Guid.NewGuid().ToString();
        public string ClientLanguage { get; set; } = "unknown";
        public long LastPongTs { get; set; } = Codec.NowMs();
    }
}
