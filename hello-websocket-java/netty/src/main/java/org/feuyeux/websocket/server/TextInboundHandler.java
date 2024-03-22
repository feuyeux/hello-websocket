package org.feuyeux.websocket.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TextInboundHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame textWebSocketFrame) {
    try {
      final String x = textWebSocketFrame.text();
      log.info("received[T]: {}", x);
      final String[] y = x.split(":");
      if (y.length > 1) {
        ctx.writeAndFlush(new TextWebSocketFrame(y[0] + ":" + y[1].toUpperCase()));
      } else {
        ctx.writeAndFlush(new TextWebSocketFrame(y[0].toUpperCase()));
      }
    } catch (Exception e) {
      log.error("", e);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("TextInbound", cause);
  }
}
