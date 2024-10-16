package org.feuyeux.websocket.info;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EchoRequest {
  private long id;
  private String meta;
  private String data;

  @Override
  public String toString() {
    return String.format("[%d] %s %s", id, data, meta);
  }
}
