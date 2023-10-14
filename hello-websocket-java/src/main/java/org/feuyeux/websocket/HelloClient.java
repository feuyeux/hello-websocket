package org.feuyeux.websocket;

import static org.feuyeux.websocket.config.EchoConfig.*;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.client.WebSocketClientHandler;
import org.feuyeux.websocket.client.WebSocketClientInitializer;
import org.feuyeux.websocket.codec.EchoRequestCodec;
import org.feuyeux.websocket.info.EchoRequest;

/**
 * Echo Client <a
 * href="https://github.com/netty/netty/blob/4.1/example/src/main/java/io/netty/example/http/websocketx/client/WebSocketClient.java">Sample</a>
 */
@Slf4j
public class HelloClient {

  public Channel open(EventLoopGroup group) {
    String URL;
    if (SSL) {
      URL = String.format("wss://%s:9899%s", host, WEBSOCKET_PATH);
    } else {
      URL = String.format("ws://%s:9898%s", host, WEBSOCKET_PATH);
    }
    URI uri = URI.create(URL);
    // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
    // If you change it to V00, ping is not supported and remember to change
    // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
    WebSocketClientHandshaker handShaker =
        WebSocketClientHandshakerFactory.newHandshaker(
            uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());
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

  public void close(Channel ch) throws InterruptedException {
    log.info("Closing the connection");
    ch.writeAndFlush(new CloseWebSocketFrame());
    ch.closeFuture().sync();
  }

  public void send(Channel ch) {
    log.info("Sending messages");
    sendBinary(ch, new EchoRequest(81, "わかった"));
    sendBinary(ch, new EchoRequest(82, "알았어"));
    sendBinary(ch, new EchoRequest(44, "Got it"));
    sendBinary(ch, new EchoRequest(33, "Je l'ai"));
    sendBinary(ch, new EchoRequest(7, "Понял"));
    sendBinary(ch, new EchoRequest(30, "Το έπιασα"));
    sendText(ch, "ru:Большое спасибо");
    sendText(ch, "fr:Merci beaucoup");
    sendText(ch, "es:Muchas Gracias");
    sendText(ch, "ar:" + "شكرا جزيلا");
    sendText(ch, "he:" + "תודה רבה");
    log.info("Complete to send");
  }

  public void sendText(Channel ch, final String text) {
    TextWebSocketFrame textFrame = new TextWebSocketFrame(text);
    ch.writeAndFlush(textFrame);
  }

  public void sendBinary(Channel ch, final EchoRequest echoRequest) {
    ByteBuf byteBuf = EchoRequestCodec.encode(echoRequest);
    BinaryWebSocketFrame binaryFrame = new BinaryWebSocketFrame(byteBuf);
    ch.writeAndFlush(binaryFrame);
  }

  public static void main(String[] args) throws Exception {
    EventLoopGroup group = new NioEventLoopGroup();
    final HelloClient client = new HelloClient();
    Channel ch = client.open(group);
    if (ch != null) {
      client.send(ch);
      client.close(ch);
    }
    group.shutdownGracefully();
  }
}
