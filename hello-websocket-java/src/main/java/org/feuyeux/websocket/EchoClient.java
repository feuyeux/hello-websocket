package org.feuyeux.websocket;

import static org.feuyeux.websocket.config.EchoConfig.host;
import static org.feuyeux.websocket.config.EchoConfig.port;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.client.WebSocketClientHandler;
import org.feuyeux.websocket.client.WebSocketClientInitializer;
import org.feuyeux.websocket.codec.EchoRequestCodec;
import org.feuyeux.websocket.info.EchoRequest;

@Slf4j
public class EchoClient {

  private static final URI uri = URI.create(String.format("ws://%s:%d/websocket/java_client", host, port));
  private Channel ch;
  private static final EventLoopGroup group = new NioEventLoopGroup();

  public void open() throws Exception {
    Bootstrap b = new Bootstrap();
    // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
    // If you change it to V00, ping is not supported and remember to change
    // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
    WebSocketClientHandshaker handShaker = WebSocketClientHandshakerFactory.newHandshaker(
        uri,
        WebSocketVersion.V13,
        null,
        false,
        EmptyHttpHeaders.INSTANCE,
        1280000);
    final WebSocketClientHandler handler = new WebSocketClientHandler(handShaker);

    b.group(group)
        .channel(NioSocketChannel.class)
        .handler(new WebSocketClientInitializer(handler));

    log.info("WebSocket Client connecting");
    ch = b.connect(uri.getHost(), uri.getPort()).sync().channel();
    handler.handshakeFuture().sync();
  }

  public void close() throws InterruptedException {
    log.info("WebSocket Client sending close");
    ch.writeAndFlush(new CloseWebSocketFrame());
    ch.closeFuture().sync();
    group.shutdownGracefully();
  }

  public void send() {
    sendBinary(new EchoRequest(81, "わかった"));
    sendBinary(new EchoRequest(82, "알았어"));
    sendBinary(new EchoRequest(44, "Got it"));
    sendBinary(new EchoRequest(33, "Je l'ai"));
    sendBinary(new EchoRequest(7, "Понял"));
    sendBinary(new EchoRequest(30, "Το έπιασα"));
    sendText("ru:Большое спасибо");
    sendText("fr:Merci beaucoup");
    sendText("es:Muchas Gracias");
    sendText("ar:" + "شكرا جزيلا");
    sendText("he:" + "תודה רבה");
  }

  public void sendText(final String text) {
    TextWebSocketFrame textFrame = new TextWebSocketFrame(text);
    ch.writeAndFlush(textFrame);
  }

  public void sendBinary(final EchoRequest echoRequest) {
    ByteBuf byteBuf = EchoRequestCodec.encode(echoRequest);
    ch.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
  }

  public static void main(String[] args) throws Exception {
    final EchoClient client = new EchoClient();
    client.open();
    client.send();
    client.close();
  }
}
