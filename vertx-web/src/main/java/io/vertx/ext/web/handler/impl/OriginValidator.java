package io.vertx.ext.web.handler.impl;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;

import java.net.URI;
import java.net.URISyntaxException;

import static java.util.Objects.isNull;


class OriginValidator {

  private static final Logger log = LoggerFactory.getLogger(OriginValidator.class);

  private URI origin;

  public OriginValidator(URI origin) {
    this.origin = origin;
  }

  public boolean validate(RoutingContext ctx) {

    if(isNull(origin)) {
      return true;
    }

    //Try to get the source from the "Origin" header
    String source = ctx.request().getHeader(HttpHeaders.ORIGIN);
    if (isBlank(source)) {
      //If empty then fallback on "Referer" header
      source = ctx.request().getHeader(HttpHeaders.REFERER);
      //If this one is empty too then we trace the event and we block the request (recommendation of the article)...
      if (isBlank(source)) {
        log.trace("ORIGIN and REFERER request headers are both absent/empty");
        return false;
      }
    }

    //Compare the source against the expected target origin
    try {
      URI sourceURL = new URI(source);
      if (
        !origin.getScheme().equals(sourceURL.getScheme()) ||
          !origin.getHost().equals(sourceURL.getHost()) ||
          origin.getPort() != sourceURL.getPort()) {
        //One the part do not match so we trace the event and we block the request
        log.trace("Protocol/Host/Port do not fully match");
        return false;
      }
    } catch (URISyntaxException e) {
      log.trace("Invalid URI", e);
      return false;
    }

    return true;
  }

  private static boolean isBlank(String s) {
    return s == null || s.trim().isEmpty();
  }
}
