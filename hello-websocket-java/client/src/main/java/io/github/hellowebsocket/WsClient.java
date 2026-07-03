package io.github.hellowebsocket;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class WsClient extends WebSocketClient {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicLong randomId = new AtomicLong(1);
    private final Random rng = new Random();

    public WsClient(URI serverUri) {
        super(serverUri);
        this.addHeader("userId", "java-client-" + UUID.randomUUID().toString().substring(0, 8));
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        Codec.log("ws-client", "Connected");
        Codec.Message hello = Codec.hello(Codec.CLIENT_LANG);
        send(hello.encode());

        // Random number background task every 5s
        scheduler.scheduleAtFixedRate(() -> {
            long id = randomId.getAndIncrement();
            long num = rng.nextLong();
            Codec.Message rn = Codec.randomNumber(id, num);
            send(rn.encode());
            Codec.log("ws-client", "RANDOM_NUMBER id=" + id + " number=" + num);
        }, Codec.RANDOM_INTERVAL_MS, Codec.RANDOM_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onMessage(ByteBuffer message) {
        byte[] data = new byte[message.remaining()];
        message.get(data);
        onBinaryMessage(data);
    }

    @Override
    public void onMessage(String message) {
        // Only binary messages
    }

    private void onBinaryMessage(byte[] data) {
        Codec.Message msg;
        try {
            msg = Codec.decodeMessage(data);
        } catch (Codec.DecodeException e) {
            Codec.log("ws-client", "Decode error: " + e.getMessage());
            return;
        }

        switch (msg.type) {
            case Codec.MSG_BONJOUR -> {
                Codec.log("ws-client", "BONJOUR server_language=" + msg.serverLanguage);
            }
            case Codec.MSG_PING -> {
                Codec.log("ws-client", "PING ts=" + msg.timestampMs);
                Codec.Message pong = Codec.pong(msg.timestampMs);
                send(pong.encode());
                Codec.log("ws-client", "PONG ts=" + msg.timestampMs);
            }
            case Codec.MSG_TIME_NOTIFICATION -> {
                Codec.log("ws-client", "TIME_NOTIFICATION ts=" + msg.timestampMs + " iso=" + msg.iso8601);
            }
            case Codec.MSG_KISS_REQUEST -> {
                Codec.log("ws-client", "KISS_REQUEST os=" + msg.osName + " ver=" + msg.osVersion + " rel=" + msg.osRelease + " arch=" + msg.osArch);
                Codec.Message resp = Codec.kissResponse(
                    System.getProperty("user.language") + "_" + System.getProperty("user.country"),
                    "UTF-8",
                    java.time.ZoneId.systemDefault().getId()
                );
                send(resp.encode());
                Codec.log("ws-client", "KISS_RESPONSE lang=" + resp.kissLanguage + " enc=" + resp.kissEncoding + " tz=" + resp.kissTimeZone);
            }
            case Codec.MSG_ECHO_RESPONSE -> {
                Codec.log("ws-client", "ECHO_RESPONSE status=" + msg.echoStatus + " results=" + msg.echoResults.length);
                for (int i = 0; i < msg.echoResults.length; i++) {
                    Codec.EchoResult r = msg.echoResults[i];
                    Codec.log("ws-client", "  Result #" + (i + 1) + ": idx=" + r.idx() + " type=" + r.type() + " kv=" + r.kv());
                }
            }
            case Codec.MSG_HASH_RESPONSE -> {
                Codec.log("ws-client", "HASH_RESPONSE id=" + msg.randomId + " hash=" + msg.hashHex);
            }
            case Codec.MSG_ERROR -> {
                Codec.log("ws-client", "ERROR code=" + msg.errorCode + " msg=" + msg.errorMessage);
            }
            default -> {
                Codec.log("ws-client", "Unknown message type: 0x" + String.format("%02x", msg.type));
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        scheduler.shutdownNow();
        Codec.log("ws-client", "Disconnected: " + code + " " + reason);
    }

    @Override
    public void onError(Exception ex) {
        Codec.log("ws-client", "Error: " + ex.getMessage());
    }

    // ─── Main ────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String host = System.getenv("WS_SERVER");
        if (host == null || host.isEmpty()) host = "127.0.0.1";
        int port = Codec.PORT;
        String portEnv = System.getenv("WS_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try { port = Integer.parseInt(portEnv); } catch (NumberFormatException ignored) {}
        }

        Codec.log("ws-client", "Starting Java WebSocket client [version: 1.0.0]");
        String url = "ws://" + host + ":" + port;
        Codec.log("ws-client", "Connecting to " + url);

        for (int attempt = 1; attempt <= 3; attempt++) {
            Codec.log("ws-client", "Connection attempt " + attempt + "/3 to " + url);
            try {
                WsClient client = new WsClient(URI.create(url));
                client.connectBlocking();
                if (client.isOpen()) {
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        Codec.log("ws-client", "Shutting down...");
                        Codec.Message disconnect = Codec.disconnect("client shutdown");
                        client.send(disconnect.encode());
                        client.close();
                    }));
                    Thread.currentThread().join();
                    return;
                }
            } catch (Exception e) {
                Codec.log("ws-client", "Error: " + e.getMessage());
                if (attempt < 3) Thread.sleep(2000);
            }
        }
        Codec.log("ws-client", "Failed to connect after 3 attempts");
        System.exit(1);
    }
}
