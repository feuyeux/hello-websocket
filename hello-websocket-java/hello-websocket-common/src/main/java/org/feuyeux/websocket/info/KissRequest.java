package org.feuyeux.websocket.info;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KissRequest {
  private Body body;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Body {
    private String type;
    private Content content;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Content {
    private String osName;
    private String osVersion;
    private String osRelease;
    private String osArchitecture;
  }
}
