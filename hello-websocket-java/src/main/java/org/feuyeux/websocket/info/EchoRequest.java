package org.feuyeux.websocket.info;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EchoRequest {

  private long id;
  private String data;

  @Override
  public String toString() {
    return String.format("[%d] %s", id, data);
  }
}
