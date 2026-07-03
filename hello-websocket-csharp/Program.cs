using HelloWebSocket;

// Entry point: dispatches to server or client based on first argument
if (args.Length > 0 && args[0] == "client")
{
    var host = Environment.GetEnvironmentVariable("WS_SERVER") ?? "127.0.0.1";
    var port = int.TryParse(Environment.GetEnvironmentVariable("WS_PORT"), out var p) ? p : Codec.PORT;
    await new WsClient(host, port).RunAsync();
}
else
{
    var port = int.TryParse(Environment.GetEnvironmentVariable("WS_PORT"), out var p) ? p : Codec.PORT;
    await new WsServer(port).RunAsync();
}
