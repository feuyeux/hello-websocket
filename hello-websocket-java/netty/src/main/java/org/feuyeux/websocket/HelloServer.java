package org.feuyeux.websocket;

import static org.feuyeux.websocket.EchoConfig.*;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.server.WebSocketServerInitializer;

@Slf4j
public class HelloServer {
  public void run() throws Exception {
    final int port = SSL ? TLS_PORT : TCP_PORT;
    final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    final EventLoopGroup workerGroup = new NioEventLoopGroup(2);
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
      ch.closeFuture().syncUninterruptibly();
    } finally {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
      log.info("EchoServer stop");
    }
  }

  public static void main(final String[] args) throws Exception {
    LocalDateTime now = LocalDateTime.now();
    log.info("{}", now);
    new HelloServer().run();
  }
}
