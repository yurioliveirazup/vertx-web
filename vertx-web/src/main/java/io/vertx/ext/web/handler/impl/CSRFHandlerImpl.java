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
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
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

  private static final Logger log = LoggerFactory.getLogger(CSRFHandlerImpl.class);

  private final TokenHelper tokenHelper;
  private final HttpRequestHandler requestHandler;

  private boolean nagHttps;
  private String cookieName = DEFAULT_COOKIE_NAME;
  private String headerName = DEFAULT_HEADER_NAME;
  private long timeout = SessionHandler.DEFAULT_SESSION_TIMEOUT;

  public CSRFHandlerImpl(final Vertx vertx, final String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
      VertxContextPRNG random = VertxContextPRNG.current(vertx);
      tokenHelper = new TokenHelper(DEFAULT_COOKIE_PATH, random, mac, cookieName, headerName);
      requestHandler = new HttpRequestHandler(tokenHelper, timeout, mac, cookieName, headerName);
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
    this.cookieName = cookieName;
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
    this.headerName = headerName;
    return this;
  }

  @Override
  public CSRFHandler setTimeout(long timeout) {
    this.timeout = timeout;
    return this;
  }

  @Override
  public CSRFHandler setNagHttps(boolean nag) {
    requestHandler.setNagHttps(nag);
    return this;
  }


  @Override
  public void handle(RoutingContext ctx) {
    requestHandler.handle(ctx);
  }
}
