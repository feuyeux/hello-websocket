using System.Collections.Concurrent;
using System.Net;
using System.Net.WebSockets;
using System.Runtime.InteropServices;
using System.Threading.Channels;

namespace HelloWebSocket;

public class WsServer
{
    private const int MaxMessageBytes = 1024 * 1024;
    private readonly HttpListener _listener = new();
    private readonly CancellationTokenSource _serverCts = new();
    private readonly ConcurrentDictionary<Guid, Task> _connections = new();
    private readonly int _port;
    private readonly string _path;

    public WsServer(int port)
    {
        _port = port;
        _path = Environment.GetEnvironmentVariable("WS_PATH") ?? "/ws";
        _listener.Prefixes.Add($"http://*:{port}/");
    }

    public async Task RunAsync()
    {
        _listener.Start();
        Codec.Log("ws-server", $"Starting C# WebSocket server on port {_port}, path {_path}");

        try
        {
            while (!_serverCts.Token.IsCancellationRequested)
            {
                var ctx = await _listener.GetContextAsync();
                var id = Guid.NewGuid();
                var task = HandleConnectionAsync(ctx);
                _connections[id] = task;
                _ = task.ContinueWith(completed => _connections.TryRemove(id, out var removed), TaskScheduler.Default);
            }
        }
        catch (HttpListenerException) when (_serverCts.IsCancellationRequested) { }
        finally
        {
            await Task.WhenAll(_connections.Values);
        }
    }

    private async Task HandleConnectionAsync(HttpListenerContext ctx)
    {
        if (!ctx.Request.IsWebSocketRequest || ctx.Request.Url?.AbsolutePath != _path)
        {
            ctx.Response.StatusCode = ctx.Request.Url?.AbsolutePath == _path ? 400 : 404;
            ctx.Response.Close();
            return;
        }

        var userId = SanitizeLogValue(ctx.Request.Headers["userId"]) ?? $"csharp-{Guid.NewGuid().ToString()[..8]}";
        using var connectionCts = CancellationTokenSource.CreateLinkedTokenSource(_serverCts.Token);
        var ct = connectionCts.Token;
        WebSocket? ws = null;

        try
        {
            var wsCtx = await ctx.AcceptWebSocketAsync(null);
            ws = wsCtx.WebSocket;
            var session = new Session(userId);
            var outgoing = Channel.CreateBounded<byte[]>(new BoundedChannelOptions(256)
            {
                FullMode = BoundedChannelFullMode.DropWrite,
                SingleReader = true,
                SingleWriter = false,
            });

            Codec.Log("ws-server", $"[{userId}] session+");

            var sendTask = RunSendTask(ws, outgoing.Reader, ct);
            var pingTask = RunPeriodicTask(Codec.PING_INTERVAL_MS, () => Codec.Ping(Codec.NowMs()).Encode(), outgoing.Writer, ct);
            var timeTask = RunPeriodicTask(Codec.TIME_INTERVAL_MS, () => Codec.TimeNotif(Codec.NowMs(), Codec.NowISO()).Encode(), outgoing.Writer, ct);
            var kissTask = RunPeriodicTask(Codec.KISS_INTERVAL_MS,
                () => Codec.KissRequest(Environment.OSVersion.Platform.ToString(), Environment.OSVersion.VersionString,
                    Environment.OSVersion.Version.ToString(), RuntimeInformation.OSArchitecture.ToString()).Encode(),
                outgoing.Writer, ct);
            var timeoutTask = RunTimeoutTask(ws, session, connectionCts, ct);

            try
            {
                while (!ct.IsCancellationRequested && ws.State == WebSocketState.Open)
                {
                    var data = await ReceiveMessageAsync(ws, ct);
                    if (data is null) break;
                    if (!await HandleMessageAsync(ws, session, data, outgoing.Writer, ct)) break;
                }
            }
            catch (OperationCanceledException) when (ct.IsCancellationRequested) { }
            catch (WebSocketException) { }
            finally
            {
                connectionCts.Cancel();
                outgoing.Writer.TryComplete();
                try { await Task.WhenAll(sendTask, pingTask, timeTask, kissTask, timeoutTask); }
                catch (OperationCanceledException) { }
            }
        }
        catch (Exception e)
        {
            Codec.Log("ws-server", $"[{userId}] connection error: {e.Message}");
        }
        finally
        {
            if (ws is { State: WebSocketState.Open or WebSocketState.CloseReceived })
            {
                try { await ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "closing", CancellationToken.None); }
                catch { ws.Abort(); }
            }
            ws?.Dispose();
            Codec.Log("ws-server", $"[{userId}] session-");
        }
    }

    private static async Task<byte[]?> ReceiveMessageAsync(WebSocket ws, CancellationToken ct)
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

    private static async Task<bool> HandleMessageAsync(
        WebSocket ws, Session session, byte[] data, ChannelWriter<byte[]> outgoing, CancellationToken ct)
    {
        Codec.Message msg;
        try
        {
            msg = Codec.DecodeMessage(data);
        }
        catch (Exception e)
        {
            var unknown = e.Message.StartsWith("unknown message type", StringComparison.Ordinal);
            outgoing.TryWrite(Codec.ErrorMsg(unknown ? Codec.ERR_UNKNOWN_MSG_TYPE : Codec.ERR_DECODE, e.Message).Encode());
            if (!unknown)
            {
                try { await ws.CloseOutputAsync(WebSocketCloseStatus.ProtocolError, "invalid protocol frame", ct); } catch { }
                return false;
            }
            return true;
        }

        switch (msg.Type)
        {
            case Codec.MSG_HELLO:
                session.ClientLanguage = msg.ClientLanguage!;
                Codec.Log("ws-server", $"HELLO from {msg.ClientLanguage}, session={session.SessionId}, time={Codec.NowMs()}");
                outgoing.TryWrite(Codec.Bonjour(Codec.SERVER_LANG).Encode());
                break;

            case Codec.MSG_ECHO_REQUEST:
                var kv = new Dictionary<string, string>
                {
                    ["id"] = msg.EchoId.ToString(),
                    ["idx"] = msg.EchoData!,
                    ["data"] = msg.EchoData!,
                    ["meta"] = session.ClientLanguage,
                };
                outgoing.TryWrite(Codec.EchoResponseMsg(200,
                    new[] { new Codec.EchoResult(Codec.NowMs(), 0, kv) }).Encode());
                break;

            case Codec.MSG_KISS_RESPONSE:
                Codec.Log("ws-server", $"KISS_RESPONSE lang={msg.KissLanguage} enc={msg.KissEncoding} tz={msg.KissTimeZone}");
                break;

            case Codec.MSG_PONG:
                session.MarkPongReceived();
                break;

            case Codec.MSG_RANDOM_NUMBER:
                var hash = Codec.HashNumber(msg.RandomNumber);
                outgoing.TryWrite(Codec.HashResponseMsg(msg.RandomId, hash).Encode());
                break;

            case Codec.MSG_DISCONNECT:
                Codec.Log("ws-server", $"DISCONNECT reason={msg.DisconnectReason}");
                return false;

            case Codec.MSG_ERROR:
                Codec.Log("ws-server", $"ERROR code={msg.ErrorCode} msg={msg.ErrorMessage}");
                break;

            default:
                outgoing.TryWrite(Codec.ErrorMsg(Codec.ERR_UNKNOWN_MSG_TYPE, $"unknown type 0x{msg.Type:x2}").Encode());
                break;
        }

        return true;
    }

    private static async Task RunSendTask(WebSocket ws, ChannelReader<byte[]> outgoing, CancellationToken ct)
    {
        await foreach (var data in outgoing.ReadAllAsync(ct))
        {
            if (ws.State != WebSocketState.Open) break;
            await ws.SendAsync(data, WebSocketMessageType.Binary, true, ct);
        }
    }

    private static async Task RunPeriodicTask(
        long intervalMs, Func<byte[]> createMessage, ChannelWriter<byte[]> outgoing, CancellationToken ct)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromMilliseconds(intervalMs));
        while (await timer.WaitForNextTickAsync(ct))
        {
            if (!outgoing.TryWrite(createMessage())) break;
        }
    }

    private static async Task RunTimeoutTask(
        WebSocket ws, Session session, CancellationTokenSource connectionCts, CancellationToken ct)
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(5));
        while (await timer.WaitForNextTickAsync(ct))
        {
            if (Codec.NowMs() - session.LastPongReceivedAt > Codec.SESSION_TIMEOUT_MS)
            {
                Codec.Log("ws-server", $"[{session.UserId}] session timeout");
                connectionCts.Cancel();
                ws.Abort();
                return;
            }
        }
    }

    private static string? SanitizeLogValue(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;
        return new string(value.Where(c => !char.IsControl(c)).Take(128).ToArray());
    }

    private sealed class Session(string userId)
    {
        private long _lastPongReceivedAt = Codec.NowMs();
        public string UserId { get; } = userId;
        public string SessionId { get; } = Guid.NewGuid().ToString();
        public string ClientLanguage { get; set; } = "unknown";
        public long LastPongReceivedAt => Interlocked.Read(ref _lastPongReceivedAt);
        public void MarkPongReceived() => Interlocked.Exchange(ref _lastPongReceivedAt, Codec.NowMs());
    }
}
