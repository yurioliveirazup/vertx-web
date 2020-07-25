package io.vertx.ext.web.handler.impl;

class RequestConfig {

  private String cookieName;
  private String headerName;

  public RequestConfig(String cookieName, String headerName) {
    this.cookieName = cookieName;
    this.headerName = headerName;
  }

  public String getCookieName() {
    return cookieName;
  }

  public String getHeaderName() {
    return headerName;
  }

  public void setCookieName(String defaultCookieName) {
    this.cookieName = defaultCookieName;
  }

  public void setHeaderName(String headerName) {
    this.headerName = headerName;
  }
}
