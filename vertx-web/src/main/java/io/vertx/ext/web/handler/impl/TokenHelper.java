package io.vertx.ext.web.handler.impl;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

import javax.crypto.Mac;
import java.util.Base64;
import java.util.Objects;

class TokenHelper {

  private static final Logger log = LoggerFactory.getLogger(TokenHelper.class);

  private static final Base64.Encoder BASE64 = Base64.getMimeEncoder();

  private String cookiePath;
  private VertxContextPRNG random;
  private Mac mac;
  private RequestConfig requestConfig;
  private boolean httpOnly;

  public TokenHelper(String defaultCookiePath, VertxContextPRNG random, Mac mac, RequestConfig requestConfig) {
    this.cookiePath = defaultCookiePath;
    this.random = random;
    this.mac = mac;
    this.requestConfig = requestConfig;
  }

  public String generateAndStoreToken(RoutingContext ctx) {
    byte[] salt = new byte[32];
    random.nextBytes(salt);

    String saltPlusToken = BASE64.encodeToString(salt) + "." + System.currentTimeMillis();
    String signature = BASE64.encodeToString(mac.doFinal(saltPlusToken.getBytes()));

    final String token = saltPlusToken + "." + signature;
    // a new token was generated add it to the cookie
    ctx.addCookie(
      Cookie.cookie(requestConfig.getCookieName(), token)
        .setPath(cookiePath)
        .setHttpOnly(httpOnly)
        // it's not an option to change the same site policy
        .setSameSite(CookieSameSite.STRICT));

    return token;
  }

  //1
  public String getTokenFromSession(RoutingContext ctx) {
    Session session = ctx.session();
    if (session == null) {
      return null;
    }
    // get the token from the session
    String sessionToken = session.get(requestConfig.getHeaderName());
    if (sessionToken != null) {
      // attempt to parse the value
      int idx = sessionToken.indexOf('/');
      if (idx != -1 && session.id() != null && session.id().equals(sessionToken.substring(0, idx))) {
        return sessionToken.substring(idx + 1);
      }
    }
    // fail
    return null;
  }

  public long parseTokenToLong(String s) {
    if (StringUtils.isBlank(s)) {
      return -1;
    }

    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      log.trace("Invalid Token format", e);
      // fallback as the token is expired
      return -1;
    }
  }

  public void setCookiePath(String cookiePath) {
    this.cookiePath = cookiePath;
  }

  public void setCookieHttpOnly(boolean httpOnly) {
    this.httpOnly = httpOnly;
  }
}
