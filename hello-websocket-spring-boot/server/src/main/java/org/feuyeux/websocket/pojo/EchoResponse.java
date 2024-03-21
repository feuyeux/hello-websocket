package org.feuyeux.websocket.pojo;

public class EchoResponse {

  private long id;
  private long elapse;
  private String data;

  public EchoResponse() {}

  public EchoResponse(long id, long elapse, String data) {
    this.id = id;
    this.elapse = elapse;
    this.data = data;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getElapse() {
    return elapse;
  }

  public void setElapse(long elapse) {
    this.elapse = elapse;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  @Override
  public String toString() {
    return String.format("[%d] %s (%d)", id, data, elapse);
  }
}
