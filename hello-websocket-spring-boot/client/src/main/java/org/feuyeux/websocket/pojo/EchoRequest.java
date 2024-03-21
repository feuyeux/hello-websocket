package org.feuyeux.websocket.pojo;

public class EchoRequest {

  private long id;
  private String data;

  public EchoRequest() {}

  public EchoRequest(long id, String data) {
    this.id = id;
    this.data = data;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return String.format("[%d] %s", id, data);
  }
}
