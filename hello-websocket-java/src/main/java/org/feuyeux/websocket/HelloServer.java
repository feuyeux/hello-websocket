package org.feuyeux.websocket;

import static org.feuyeux.websocket.config.EchoConfig.*;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
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
    final int port = Integer.parseInt(System.getProperty("port", SSL ? "9899" : "9898"));

    final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    final EventLoopGroup workerGroup = new NioEventLoopGroup(2);
    try {
      final ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .handler(new LoggingHandler(LogLevel.INFO))
          .childHandler(new WebSocketServerInitializer());
      ChannelFuture bind = b.bind(port);
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
