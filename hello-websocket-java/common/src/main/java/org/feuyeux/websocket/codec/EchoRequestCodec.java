package org.feuyeux.websocket.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import org.feuyeux.websocket.info.EchoRequest;

public class EchoRequestCodec {

  // 编码方法
  public static ByteBuf encode(EchoRequest request) {
    ByteBuf buf = Unpooled.buffer();
    // 写入 id，假设 id 是 long 类型，占 8 字节
    buf.writeLong(request.getId());
    // 写入 meta 的长度，使用 Varint 编码
    encodeVarint32(buf, request.getMeta().getBytes(StandardCharsets.UTF_8).length);
    // 写入 meta 的内容
    buf.writeBytes(request.getMeta().getBytes(StandardCharsets.UTF_8));
    // 写入 data 的长度，使用 Varint 编码
    encodeVarint32(buf, request.getData().getBytes(StandardCharsets.UTF_8).length);
    // 写入 data 的内容
    buf.writeBytes(request.getData().getBytes(StandardCharsets.UTF_8));
    return buf;
  }

  // 解码方法
  public static EchoRequest decode(ByteBuf buf) {
    // 读取 id
    long id = buf.readLong();
    // 读取 meta 的长度，使用 Varint 解码
    int metaLength = decodeVarint32(buf);
    // 读取 meta 的内容
    byte[] metaBytes = new byte[metaLength];
    buf.readBytes(metaBytes);
    String meta = new String(metaBytes, StandardCharsets.UTF_8);
    // 读取 data 的长度，使用 Varint 解码
    int dataLength = decodeVarint32(buf);
    // 读取 data 的内容
    byte[] dataBytes = new byte[dataLength];
    buf.readBytes(dataBytes);
    String data = new String(dataBytes, StandardCharsets.UTF_8);
    // 创建 EchoRequest 对象并返回
    return new EchoRequest(id, meta, data);
  }

  // Varint 编码方法
  public static void encodeVarint32(ByteBuf buf, int value) {
    while (true) {
      if ((value & 0xFFFFFF80) == 0) {
        buf.writeByte(value);
        return;
      } else {
        buf.writeByte((value & 0x7F) | 0x80);
        value >>>= 7;
      }
    }
  }

  // Varint 解码方法
  public static int decodeVarint32(ByteBuf buf) {
    int value = 0;
    int shift = 0;
    byte b;
    do {
      b = buf.readByte();
      value |= (b & 0x7F) << shift;
      if ((b & 0x80) == 0) {
        break;
      }
      shift += 7;
    } while (shift < 32);
    return value;
  }
}
