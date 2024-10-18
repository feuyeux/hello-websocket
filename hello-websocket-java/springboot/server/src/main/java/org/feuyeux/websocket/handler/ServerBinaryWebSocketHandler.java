package org.feuyeux.websocket.handler;

import static org.feuyeux.websocket.tools.HelloUtils.buildResults;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import org.feuyeux.websocket.codec.EchoRequestCodec;
import org.feuyeux.websocket.codec.EchoResponseCodec;
import org.feuyeux.websocket.info.EchoRequest;
import org.feuyeux.websocket.info.EchoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

public class ServerBinaryWebSocketHandler extends BinaryWebSocketHandler {

  private static final Logger logger = LoggerFactory.getLogger(ServerBinaryWebSocketHandler.class);

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    logger.info("Server connection opened");
    sessions.add(session);
    TimeUnit.SECONDS.sleep(1);
    EchoResponse echoResponse =
        EchoResponse.builder().status(200).results(buildResults("1")).build();
    ByteBuf respByteBuf = EchoResponseCodec.encode(echoResponse);
    session.sendMessage(new BinaryMessage(respByteBuf.nioBuffer()));
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    logger.info("Server connection closed({})", status.getCode());
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
  public void handleBinaryMessage(WebSocketSession session, BinaryMessage message)
      throws Exception {
    long start = System.currentTimeMillis();
    ByteBuffer reqByteBuf = message.getPayload();
    // build ByteBuf from ByteBuffer
    ByteBuf byteBuf = io.netty.buffer.Unpooled.wrappedBuffer(reqByteBuf);
    EchoRequest echoRequest = EchoRequestCodec.decode(byteBuf);
    logger.info("Server received: {}", echoRequest);
    EchoResponse echoResponse =
        EchoResponse.builder().status(200).results(buildResults(echoRequest.getData())).build();
    ByteBuf respByteBuf = EchoResponseCodec.encode(echoResponse);
    session.sendMessage(new BinaryMessage(respByteBuf.nioBuffer()));
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    logger.info("Server transport error: {}", exception.getMessage());
  }
}
