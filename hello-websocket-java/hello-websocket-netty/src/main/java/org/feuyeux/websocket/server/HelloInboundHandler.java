package org.feuyeux.websocket.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.HelloServer;

@Slf4j
public abstract class HelloInboundHandler<T> extends SimpleChannelInboundHandler<T> {

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    log.debug("Connection established: {}", ctx.channel().remoteAddress());
    HelloServer.addChannel(ctx.channel());
    super.channelActive(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    log.info("Connection closed: {}", ctx.channel().remoteAddress());
    HelloServer.removeChannel(ctx.channel());
    super.channelInactive(ctx);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("Caught exception", cause);
  }
}
