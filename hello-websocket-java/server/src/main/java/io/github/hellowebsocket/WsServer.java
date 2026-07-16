package io.github.hellowebsocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class WsServer extends WebSocketServer {

    public WsServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String userId = handshake.getFieldValue("userId");
        if (userId == null || userId.isEmpty()) {
            userId = "java-" + UUID.randomUUID().toString().substring(0, 8);
        }
        conn.setAttachment(new Session(userId));
        Codec.log("ws-server", "[" + userId + "] session+");
        startBackgroundTasks(conn);
    }

    private void startBackgroundTasks(WebSocket conn) {
        Session session = conn.getAttachment();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

        // PING every 1s
        scheduler.scheduleAtFixedRate(() -> {
            if (conn.isOpen()) {
                Codec.Message ping = Codec.ping(Codec.nowMs());
                conn.send(ping.encode());
            }
        }, Codec.PING_INTERVAL_MS, Codec.PING_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // TIME_NOTIFICATION every 5s
        scheduler.scheduleAtFixedRate(() -> {
            if (conn.isOpen()) {
                Codec.Message tn = Codec.timeNotif(Codec.nowMs(), Codec.nowISO());
                conn.send(tn.encode());
            }
        }, Codec.TIME_INTERVAL_MS, Codec.TIME_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // KISS_REQUEST every 5s
        scheduler.scheduleAtFixedRate(() -> {
            if (conn.isOpen()) {
                Codec.Message kr = Codec.kissRequest(
                    System.getProperty("os.name"),
                    "unknown",
                    "unknown",
                    System.getProperty("os.arch")
                );
                conn.send(kr.encode());
            }
        }, Codec.KISS_INTERVAL_MS, Codec.KISS_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // Timeout check every 5s
        scheduler.scheduleAtFixedRate(() -> {
            long last = session.lastPongTs.get();
            if (Codec.nowMs() - last > Codec.SESSION_TIMEOUT_MS) {
                Codec.log("ws-server", "[" + session.userId + "] session timeout");
                conn.close();
            }
        }, 5, 5, TimeUnit.SECONDS);

        session.scheduler = scheduler;
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        byte[] data = new byte[message.remaining()];
        message.get(data);
        onBinaryMessage(conn, data);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        // We only handle binary messages
    }

    private void onBinaryMessage(WebSocket conn, byte[] data) {
        if (data.length > 1024 * 1024) {
            conn.close(1009, "message exceeds 1 MiB");
            return;
        }
        Session session = conn.getAttachment();
        Codec.Message msg;
        try {
            msg = Codec.decodeMessage(data);
        } catch (Codec.DecodeException e) {
            Codec.log("ws-server", "Decode error: " + e.getMessage());
            boolean unknown = e.getMessage().startsWith("unknown message type");
            conn.send(Codec.error(unknown ? Codec.ERR_UNKNOWN_MSG_TYPE : Codec.ERR_DECODE, e.getMessage()).encode());
            if (!unknown) conn.close(1002, "invalid protocol frame");
            return;
        }

        switch (msg.type) {
            case Codec.MSG_HELLO -> {
                session.clientLanguage = msg.clientLanguage;
                Codec.log("ws-server", "HELLO from " + msg.clientLanguage + ", session=" + session.sessionId + ", time=" + Codec.nowMs());
                conn.send(Codec.bonjour(Codec.SERVER_LANG).encode());
            }
            case Codec.MSG_ECHO_REQUEST -> {
                Codec.log("ws-server", "ECHO_REQUEST id=" + msg.echoId + " meta=" + msg.echoMeta + " data=" + msg.echoData);
                Map<String, String> kv = new HashMap<>();
                kv.put("id", String.valueOf(msg.echoId));
                kv.put("idx", msg.echoData);
                kv.put("data", msg.echoData);
                kv.put("meta", session.clientLanguage);
                Codec.EchoResult result = new Codec.EchoResult(Codec.nowMs(), 0, kv);
                conn.send(Codec.echoResponse(200, new Codec.EchoResult[]{result}).encode());
            }
            case Codec.MSG_KISS_RESPONSE -> {
                Codec.log("ws-server", "KISS_RESPONSE lang=" + msg.kissLanguage + " enc=" + msg.kissEncoding + " tz=" + msg.kissTimeZone);
            }
            case Codec.MSG_PONG -> {
                session.lastPongTs.set(Codec.nowMs());
                Codec.log("ws-server", "PONG ts=" + msg.timestampMs);
            }
            case Codec.MSG_RANDOM_NUMBER -> {
                Codec.log("ws-server", "RANDOM_NUMBER id=" + msg.randomId + " number=" + msg.randomNumber);
                String hash = Codec.hashNumber(msg.randomNumber);
                conn.send(Codec.hashResponse(msg.randomId, hash).encode());
                Codec.log("ws-server", "HASH_RESPONSE id=" + msg.randomId + " hash=" + hash);
            }
            case Codec.MSG_DISCONNECT -> {
                Codec.log("ws-server", "DISCONNECT reason=" + msg.disconnectReason);
                conn.close();
            }
            case Codec.MSG_ERROR -> {
                Codec.log("ws-server", "ERROR code=" + msg.errorCode + " msg=" + msg.errorMessage);
            }
            default -> {
                Codec.log("ws-server", "Unknown message type: 0x" + String.format("%02x", msg.type));
                conn.send(Codec.error(Codec.ERR_UNKNOWN_MSG_TYPE, "unknown type 0x" + String.format("%02x", msg.type)).encode());
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Session session = conn.getAttachment();
        if (session != null && session.scheduler != null) {
            session.scheduler.shutdownNow();
        }
        Codec.log("ws-server", "[" + (session != null ? session.userId : "?") + "] session-");
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Codec.log("ws-server", "Error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        Codec.log("ws-server", "Server started successfully");
    }

    // ─── Session ─────────────────────────────────────────────────────────────

    static class Session {
        final String userId;
        final String sessionId = UUID.randomUUID().toString();
        final AtomicLong lastPongTs = new AtomicLong(Codec.nowMs());
        volatile String clientLanguage = "unknown";
        ScheduledExecutorService scheduler;

        Session(String userId) { this.userId = userId; }
    }

    // ─── Main ────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        int port = Codec.PORT;
        String portEnv = System.getenv("WS_PORT");
        if (portEnv != null && !portEnv.isEmpty()) {
            try { port = Integer.parseInt(portEnv); } catch (NumberFormatException ignored) {}
        }

        Codec.log("ws-server", "Starting Java WebSocket server on port " + port);
        WsServer server = new WsServer(port);
        server.setReuseAddr(true);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Codec.log("ws-server", "Shutting down...");
            try { server.stop(); } catch (Exception e) { /* ignore */ }
        }));
    }
}
