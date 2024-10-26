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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.client.WebSocketClientHandler;
import org.feuyeux.websocket.client.WebSocketClientInitializer;
import org.feuyeux.websocket.codec.EchoRequestCodec;
import org.feuyeux.websocket.info.EchoRequest;

@Slf4j
public class HelloClient {

  public Channel open(EventLoopGroup group) {
    String URL;
    if (SSL) {
      URL = String.format("wss://%s:%d%s", host, TLS_PORT, WEBSOCKET_PATH);
    } else {
      URL = String.format("ws://%s:%d%s", host, TCP_PORT, WEBSOCKET_PATH);
    }
    URI uri = URI.create(URL);
    WebSocketVersion version = WebSocketVersion.V13;
    WebSocketClientHandshaker handShaker =
        WebSocketClientHandshakerFactory.newHandshaker(
            uri, version, null, true, new DefaultHttpHeaders());
    final WebSocketClientHandler clientHandler = new WebSocketClientHandler(handShaker);
    final WebSocketClientInitializer clientInitializer =
        new WebSocketClientInitializer(clientHandler);

    Bootstrap b = new Bootstrap();
    b.group(group).channel(NioSocketChannel.class).handler(clientInitializer);
    try {
      log.info("Connecting to server({})", URL);
      Channel ch = b.connect(uri.getHost(), uri.getPort()).sync().channel();
      clientHandler.handshakeFuture().sync();
      return ch;
    } catch (Exception e) {
      log.error("", e);
    }
    return null;
  }

  public void closeChannel(Channel ch) throws InterruptedException {
    if (ch != null && ch.isActive()) {
      log.info("Closing the connection");
      ch.writeAndFlush(new CloseWebSocketFrame());
      ch.closeFuture().sync();
    }
  }

  AtomicLong id = new AtomicLong();

  public void send(Channel ch) {
    if (ch != null && ch.isActive()) {
      buildLinkRequests()
          .forEach(
              request -> {
                sendBinary(ch, request);
              });
      sendText(ch, ch.toString() + id.incrementAndGet());
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

  static final int USERS = 1;
  static final int REQUESTS = 3;

  public static void main(String[] args) {
    final HelloClient client = new HelloClient();
    EventLoopGroup group = new NioEventLoopGroup();
    IntStream.range(0, USERS)
        .parallel()
        .forEach(
            i -> {
              try {
                Channel ch = client.open(group);
                if (ch != null) {
                  IntStream.range(0, REQUESTS)
                      .parallel()
                      .forEach(
                          j -> {
                            try {
                              client.send(ch);
                            } catch (Exception e) {
                              log.error("", e);
                            }
                          });
                  try {
                    client.closeChannel(ch);
                  } catch (InterruptedException e) {
                    log.error("", e);
                  }
                }
              } catch (Exception e) {
                log.error("", e);
              }
            });
    group.shutdownGracefully();
  }
}
