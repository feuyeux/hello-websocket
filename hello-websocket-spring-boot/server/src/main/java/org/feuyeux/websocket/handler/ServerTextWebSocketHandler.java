package org.feuyeux.websocket.handler;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.HtmlUtils;

public class ServerTextWebSocketHandler extends TextWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(ServerTextWebSocketHandler.class);

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    logger.info("Server connection opened");
    sessions.add(session);
    TimeUnit.SECONDS.sleep(1);
    // Send a message to the client
    TextMessage message = new TextMessage("Bonjour");
    logger.info("Server sends: {}", message);
    session.sendMessage(message);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    logger.info("Server connection closed: {}", status);
    sessions.remove(session);
  }

  @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
  void sendPeriodicMessages() throws IOException {
    for (WebSocketSession session : sessions) {
      if (session.isOpen()) {
        String broadcast = "server periodic message " + LocalTime.now();
        logger.info("Server sends: {}", broadcast);
        session.sendMessage(new TextMessage(broadcast));
      }
    }
  }

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    String request = message.getPayload();
    logger.info("Server received: {}", request);
    //
    String response = String.format("response from server to '%s'", HtmlUtils.htmlEscape(request));
    logger.info("Server sends: {}", response);
    session.sendMessage(new TextMessage(response));
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    logger.info("Server transport error: {}", exception.getMessage());
  }
}
