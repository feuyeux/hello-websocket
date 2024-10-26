package org.feuyeux.websocket.handler;

import static org.feuyeux.websocket.tools.HelloUtils.getRandomId;

import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.feuyeux.websocket.codec.EchoRequestCodec;
import org.feuyeux.websocket.codec.EchoResponseCodec;
import org.feuyeux.websocket.info.EchoRequest;
import org.feuyeux.websocket.info.EchoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

public class ClientBinaryWebSocketHandler extends BinaryWebSocketHandler {
  private static final Logger logger = LoggerFactory.getLogger(ClientBinaryWebSocketHandler.class);
  private String type;

  public void setType(String type) {
    this.type = type;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    InetAddress address = session.getRemoteAddress().getAddress();
    logger.info("Client connection({}) opened[{}]", address, type);
    TimeUnit.SECONDS.sleep(1);
    sendMessage(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    logger.info("Client connection closed[{}]: {}", type, status.getCode());
  }

  @Override
  public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
    ByteBuffer reqByteBuf = message.getPayload();
    // build ByteBuf from ByteBuffer
    ByteBuf byteBuf = io.netty.buffer.Unpooled.wrappedBuffer(reqByteBuf);
    EchoResponse echoResponse = EchoResponseCodec.decode(byteBuf);
    logger.info("Client received[{}]: {}", type, echoResponse);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    logger.info("Client transport error[{}]: {}", type, exception.getMessage());
  }

  private static void sendMessage(WebSocketSession session) throws IOException {
    EchoRequest echoRequest = EchoRequest.builder().meta("[JAVA]").data(getRandomId()).build();
    ByteBuf respByteBuf = EchoRequestCodec.encode(echoRequest);
    session.sendMessage(new BinaryMessage(respByteBuf.nioBuffer()));
  }
}
