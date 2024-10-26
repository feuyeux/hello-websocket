package org.feuyeux.websocket;

import static org.feuyeux.websocket.EchoConfig.*;
import static org.feuyeux.websocket.tools.HelloUtils.buildKissRequest;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.server.WebSocketServerInitializer;

@Slf4j
public class HelloServer {
  private static final Map<String, Channel> activeChannels = new ConcurrentHashMap<>();
  private static Gson gson =
      new GsonBuilder()
          .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
          .create();
  private static String kissRequest = gson.toJson(buildKissRequest());

  public static void addChannel(Channel channel) {
    activeChannels.put(channel.id().asLongText(), channel);
    Thread.ofVirtual()
        .start(
            () -> {
              while (channel.isActive()) {
                log.info("Sending kiss request to: {}", channel.remoteAddress());
                channel.writeAndFlush(new TextWebSocketFrame(kissRequest));
                try {
                  Thread.sleep(5000);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  log.error("Thread was interrupted", e);
                }
              }
            });
  }

  public static void removeChannel(Channel channel) {
    activeChannels.remove(channel.id().asLongText());
  }

  public void run() throws Exception {
    final int port = SSL ? TLS_PORT : TCP_PORT;
    final EventLoopGroup bossGroup = new NioEventLoopGroup();
    final EventLoopGroup workerGroup = new NioEventLoopGroup();
    try {
      final ServerBootstrap bootstrap = new ServerBootstrap();
      bootstrap
          .group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .handler(new LoggingHandler(LogLevel.INFO))
          .childHandler(new WebSocketServerInitializer())
          .option(ChannelOption.SO_BACKLOG, 1024)
          .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
          // Enable the keep-alive option for the child channels
          .childOption(ChannelOption.SO_KEEPALIVE, true)
          .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
      ChannelFuture bind = bootstrap.bind(port);
      Channel ch = bind.addListener(f -> log.info("EchoServer start(:{})", port)).sync().channel();
      //
      ch.closeFuture().syncUninterruptibly();
    } finally {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
      log.info("EchoServer stop");
    }
  }

  public static void main(final String[] args) throws Exception {
    log.debug("now:{}", LocalDateTime.now());
    new HelloServer().run();
  }
}
