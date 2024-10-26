package org.feuyeux.websocket.info;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EchoResponse {

  private int status;
  private List<EchoResult> results;

  @Override
  public String toString() {
    return String.format("[%d] %s", status, results);
  }
}
