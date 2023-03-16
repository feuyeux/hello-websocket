package org.feuyeux.websocket;

import static org.feuyeux.websocket.config.EchoConfig.host;
import static org.feuyeux.websocket.config.EchoConfig.port;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.server.WebSocketServerInitializer;

@Slf4j
public class EchoServer {

  private Channel ch;

  public void run() throws Exception {
    final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    final EventLoopGroup workerGroup = new NioEventLoopGroup(2);
    try {
      final ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .childHandler(new WebSocketServerInitializer());
      ChannelFuture bind = b.bind(host, port);
      ch = bind.addListener(f -> log.info("EchoServer start({}:{})", host, port)).sync().channel();
      ch.closeFuture().sync();
    } finally {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
      log.info("EchoServer stop");
    }
  }

  public static void main(final String[] args) throws Exception {
    LocalDateTime now = LocalDateTime.now();
    log.info("{}", now);
    new EchoServer().run();
  }
}
