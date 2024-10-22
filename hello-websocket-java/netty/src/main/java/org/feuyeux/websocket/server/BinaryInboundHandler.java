package org.feuyeux.websocket.server;

import static org.feuyeux.websocket.tools.HelloUtils.buildResults;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.feuyeux.websocket.codec.EchoRequestCodec;
import org.feuyeux.websocket.codec.EchoResponseCodec;
import org.feuyeux.websocket.info.EchoRequest;
import org.feuyeux.websocket.info.EchoResponse;

@Slf4j
public class BinaryInboundHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

  @Override
  protected void channelRead0(
      ChannelHandlerContext ctx, BinaryWebSocketFrame binaryWebSocketFrame) {
    try {
      long start = System.currentTimeMillis();
      ByteBuf reqByteBuf = binaryWebSocketFrame.content();
      EchoRequest echoRequest = EchoRequestCodec.decode(reqByteBuf);
      log.info("received[B]: {}", echoRequest);
      EchoResponse echoResponse =
          EchoResponse.builder().status(200).results(buildResults(echoRequest.getData())).build();
      ByteBuf respByteBuf = EchoResponseCodec.encode(echoResponse);
      ctx.writeAndFlush(new BinaryWebSocketFrame(respByteBuf));
      long end = System.currentTimeMillis();
      log.info("elapsed[B]: {} ms", end - start);
    } catch (Exception e) {
      log.error("", e);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error("BinaryInbound", cause);
  }
}
