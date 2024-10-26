package org.feuyeux.websocket.client;

import static org.feuyeux.websocket.EchoConfig.SSL;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.SslContextBuilder;
import java.security.KeyStore;
import javax.net.ssl.TrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.HelloClient;

@Slf4j
public class WebSocketClientInitializer extends ChannelInitializer<SocketChannel> {

  private final ChannelHandler handler;

  public WebSocketClientInitializer(ChannelHandler handler) {
    this.handler = handler;
  }

  @Override
  public void initChannel(SocketChannel ch) {
    ChannelPipeline pipeline = ch.pipeline();
    if (SSL) {
      try {
        KeyStore truststore = KeyStore.getInstance("JKS");
        truststore.load(
            HelloClient.class.getResourceAsStream("/HelloTruststore.jks"),
            "changeit".toCharArray());
        TrustManagerFactory trustManagerFactory =
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(truststore);
        pipeline.addLast(
            SslContextBuilder.forClient()
                .trustManager(trustManagerFactory)
                .build()
                .newHandler(ch.alloc()));
      } catch (Exception e) {
        log.error("", e);
      }
    }
    pipeline.addLast("http-codec", new HttpClientCodec());
    pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
    // WebSocketClientCompressionHandler.INSTANCE
    pipeline.addLast("ws-handler", handler);
  }
}
