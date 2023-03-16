package org.feuyeux.websocket.client;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;

public class WebSocketClientInitializer extends ChannelInitializer<SocketChannel> {

  private ChannelHandler handler;

  public WebSocketClientInitializer(ChannelHandler handler) {
    this.handler = handler;
  }

  @Override
  public void initChannel(SocketChannel ch) {
    ChannelPipeline pipeline = ch.pipeline();
    pipeline.addLast("http-codec", new HttpClientCodec());
    pipeline.addLast("aggregator", new HttpObjectAggregator(65536));
    pipeline.addLast("ws-handler", handler);
  }
}