package org.feuyeux.websocket.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ClientTextWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ClientTextWebSocketHandler.class);
    private String type;

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("Client connection opened[{}]", type);
        TimeUnit.SECONDS.sleep(1);
        // Send a message to the server
        String payload = String.format("Hello %s", type);
        TextMessage message = new TextMessage(payload);
        logger.info("Client sends: {}", message.getPayload());
        session.sendMessage(message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("Client connection closed[{}]: {}", type, status);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        logger.info("Client received[{}]: {}", type, payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.info("Client transport error[{}]: {}", type, exception.getMessage());
    }
}