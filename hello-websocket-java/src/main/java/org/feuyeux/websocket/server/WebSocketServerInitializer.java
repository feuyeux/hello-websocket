package org.feuyeux.websocket.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

  @Override
  public void initChannel(final SocketChannel ch) {
    final ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast("http-request-decoder", new HttpRequestDecoder());
    pipeline.addLast("aggregator", new HttpObjectAggregator(1));
    pipeline.addLast("http-response-encoder", new HttpResponseEncoder());
    pipeline.addLast("request-handler", new WebSocketServerProtocolHandler("/websocket"));
    pipeline.addLast("text-handler", new TextInboundHandler());
    pipeline.addLast("binary-handler", new BinaryInboundHandler());
  }
}
