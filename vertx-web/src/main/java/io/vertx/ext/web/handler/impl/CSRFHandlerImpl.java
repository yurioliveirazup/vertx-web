/*
 * Copyright 2015 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.ext.web.handler.impl;

import io.vertx.core.Vertx;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CSRFHandler;
import io.vertx.ext.web.handler.SessionHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * @author <a href="mailto:pmlopes@gmail.com">Paulo Lopes</a>
 */
public class CSRFHandlerImpl implements CSRFHandler {

  private final TokenHelper tokenHelper;
  private final HttpRequestHandler requestHandler;
  private final RequestConfig requestConfig;

  public CSRFHandlerImpl(final Vertx vertx, final String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
      VertxContextPRNG random = VertxContextPRNG.current(vertx);
      this.requestConfig = new RequestConfig(DEFAULT_COOKIE_NAME, DEFAULT_HEADER_NAME);
      tokenHelper = new TokenHelper(DEFAULT_COOKIE_PATH, random, mac, requestConfig);
      requestHandler = new HttpRequestHandler(tokenHelper, SessionHandler.DEFAULT_SESSION_TIMEOUT, mac, requestConfig);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public CSRFHandler setOrigin(String origin) {
    requestHandler.setOrigin(origin);
    return this;
  }

  @Override
  public CSRFHandler setCookieName(String cookieName) {
    this.requestConfig.setCookieName(cookieName);
    return this;
  }

  @Override
  public CSRFHandler setCookiePath(String cookiePath) {
    tokenHelper.setCookiePath(cookiePath);
    return this;
  }

  @Override
  public CSRFHandler setCookieHttpOnly(boolean httpOnly) {
    tokenHelper.setCookieHttpOnly(httpOnly);
    return this;
  }

  @Override
  public CSRFHandler setHeaderName(String headerName) {
    this.requestConfig.setHeaderName(headerName);
    return this;
  }

  @Override
  public CSRFHandler setTimeout(long timeout) {
    requestHandler.setTimeout(timeout);
    return this;
  }

  @Override
  public CSRFHandler setNagHttps(boolean nag) {
    requestHandler.setNagHttps(nag);
    return this;
  }


  @Override
  public void handle(RoutingContext ctx) {
    requestHandler.handleRequestTo(ctx);
  }
}
