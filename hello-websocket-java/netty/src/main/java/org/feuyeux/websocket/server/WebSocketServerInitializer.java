package org.feuyeux.websocket.server;

import static org.feuyeux.websocket.config.EchoConfig.*;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.HelloServer;

@Slf4j
public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

  @Override
  public void initChannel(final SocketChannel ch) {
    final ChannelPipeline pipeline = ch.pipeline();
    if (SSL) {
      try {
        pipeline.addLast(createSSLContext().newHandler(ch.alloc()));
      } catch (Exception e) {
        log.error("", e);
      }
    }
    pipeline.addLast("http-request-decoder", new HttpRequestDecoder());
    pipeline.addLast("aggregator", new HttpObjectAggregator(1));
    pipeline.addLast("http-response-encoder", new HttpResponseEncoder());
    pipeline.addLast("request-handler", new WebSocketServerProtocolHandler(WEBSOCKET_PATH));
    //
    pipeline.addLast("text-handler", new TextInboundHandler());
    pipeline.addLast("binary-handler", new BinaryInboundHandler());
  }

  private SslContext createSSLContext() throws Exception {
    KeyStore keystore = KeyStore.getInstance("JKS");
    keystore.load(
        HelloServer.class.getResourceAsStream("/HelloKeystore.jks"), "changeit".toCharArray());
    KeyManagerFactory keyManagerFactory =
        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keystore, "changeit".toCharArray());
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
    return SslContextBuilder.forServer(keyManagerFactory).build();
  }
}
