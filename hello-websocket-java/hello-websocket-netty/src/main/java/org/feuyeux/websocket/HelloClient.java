package org.feuyeux.websocket;

import static org.feuyeux.websocket.EchoConfig.*;
import static org.feuyeux.websocket.tools.HelloUtils.buildLinkRequests;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.client.WebSocketClientHandler;
import org.feuyeux.websocket.client.WebSocketClientInitializer;
import org.feuyeux.websocket.codec.EchoRequestCodec;
import org.feuyeux.websocket.info.EchoRequest;

@Slf4j
public class HelloClient {

  private static final int USER_NUM = 1;
  private static final int REQUEST_NUM = 3;
  private static Map<String, Channel> chMap = new ConcurrentHashMap<>();

  public Channel open(EventLoopGroup group) {
    URI uri = URI.create(getUrl());
    WebSocketClientHandshaker handShaker = getWebSocketClientHandshaker(uri);
    final WebSocketClientHandler clientHandler = new WebSocketClientHandler(handShaker);
    final WebSocketClientInitializer clientInitializer =
        new WebSocketClientInitializer(clientHandler);

    Bootstrap b = new Bootstrap();
    b.group(group).channel(NioSocketChannel.class).handler(clientInitializer);
    try {
      log.info("Connecting to server({}:{})", uri.getHost(), uri.getPort());
      Channel ch = b.connect(uri.getHost(), uri.getPort()).sync().channel();
      clientHandler.handshakeFuture().sync();
      return ch;
    } catch (Exception e) {
      log.error("", e);
    }
    return null;
  }

  private static String getUrl() {
    if (SSL) {
      return String.format("wss://%s:%d%s", host, TLS_PORT, WEBSOCKET_PATH);
    } else {
      return String.format("ws://%s:%d%s", host, TCP_PORT, WEBSOCKET_PATH);
    }
  }

  private static WebSocketClientHandshaker getWebSocketClientHandshaker(URI uri) {
    WebSocketVersion version = WebSocketVersion.V13;
    WebSocketClientHandshaker handShaker =
        WebSocketClientHandshakerFactory.newHandshaker(
            uri, version, null, true, new DefaultHttpHeaders());
    return handShaker;
  }

  public void closeChannel(Channel ch) throws InterruptedException {
    if (ch != null && ch.isActive()) {
      log.debug("Closing the connection");
      ch.writeAndFlush(new CloseWebSocketFrame());
      ch.closeFuture().sync();
    }
  }

  private static IntConsumer send(HelloClient client, Channel ch) {
    return j -> {
      try {
        client.send(ch);
      } catch (Exception e) {
        log.error("", e);
      }
    };
  }

  public void send(Channel ch) {
    if (ch != null && ch.isActive()) {
      buildLinkRequests().forEach(request -> sendBinary(ch, request));
    }
  }

  public void sendText(Channel ch, final String text) {
    if (ch != null && ch.isActive()) {
      TextWebSocketFrame textFrame = new TextWebSocketFrame(text);
      ch.writeAndFlush(textFrame);
    }
  }

  public void sendBinary(Channel ch, final EchoRequest echoRequest) {
    if (ch != null && ch.isActive()) {
      ByteBuf byteBuf = EchoRequestCodec.encode(echoRequest);
      BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(byteBuf);
      ch.writeAndFlush(binaryFrame);
    }
  }

  public static void main(String[] args) throws InterruptedException {
    final HelloClient client = new HelloClient();
    EventLoopGroup group = new NioEventLoopGroup();
    try {
      IntStream.range(0, USER_NUM).forEach(connectAndSend(client, group));
      TimeUnit.HOURS.sleep(1);
    } finally {
      chMap.forEach(
          (k, v) -> {
            try {
              client.closeChannel(v);
            } catch (InterruptedException e) {
              log.error("", e);
            }
          });
      group.shutdownGracefully();
    }
  }

  private static IntConsumer connectAndSend(HelloClient client, EventLoopGroup group) {
    return i -> {
      try {
        Channel ch = client.open(group);
        if (ch != null) {
          IntStream.range(0, REQUEST_NUM).parallel().forEach(send(client, ch));
          chMap.put(ch.id().asLongText(), ch);
        }
      } catch (Exception e) {
        log.error("", e);
      }
    };
  }
}
