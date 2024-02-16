package org.feuyeux.websocket.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import java.util.concurrent.TimeUnit;

public class ClientStompSessionHandler extends StompSessionHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ClientStompSessionHandler.class);

    @Override
    public void afterConnected(StompSession session, StompHeaders headers) {
        try {
            logger.info("Client connected[websocket-sockjs-stomp]: headers {}", headers);
            // Subscribe to the server's topic
            session.subscribe("/hello/subscribe", this);
            session.subscribe("/queue/responses", this);
            session.subscribe("/queue/errors", this);
            session.subscribe("/topic/callback", this);
            TimeUnit.SECONDS.sleep(1);
            // Send a message to the server
            String message = "Hello websocket-sockjs-stomp";
            logger.info("Client sends: {}", message);
            session.send("/hello/send", message);
        } catch (Exception e) {
            logger.error("Client error:", e);
        }
    }

    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        logger.info("Client received: {}", payload);
        // logger.debug("Client received: payload {}, headers {}", payload, headers);
    }

    @Override
    public void handleException(StompSession session, StompCommand command,
                                StompHeaders headers, byte[] payload, Throwable exception) {
        logger.error("Client error: exception {}, command {}, payload {}, headers {}",
                exception.getMessage(), command, payload, headers);
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        logger.error("Client transport error: error {}", exception.getMessage());
    }
}