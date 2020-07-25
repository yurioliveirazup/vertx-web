package io.vertx.ext.web.handler.impl;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.auth.VertxContextPRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

import javax.crypto.Mac;
import java.util.Base64;

class TokenHelper {

  private static final Base64.Encoder BASE64 = Base64.getMimeEncoder();

  private String cookiePath;
  private VertxContextPRNG random;
  private Mac mac;
  private String cookieName;
  private String headerName;
  private boolean httpOnly;

  public TokenHelper(String defaultCookiePath, VertxContextPRNG random, Mac mac, String cookieName, String headerName) {
    this.cookiePath = defaultCookiePath;
    this.random = random;
    this.mac = mac;
    this.cookieName = cookieName;
    this.headerName = headerName;
  }

  public String generateAndStoreToken(RoutingContext ctx) {
    byte[] salt = new byte[32];
    random.nextBytes(salt);

    String saltPlusToken = BASE64.encodeToString(salt) + "." + System.currentTimeMillis();
    String signature = BASE64.encodeToString(mac.doFinal(saltPlusToken.getBytes()));

    final String token = saltPlusToken + "." + signature;
    // a new token was generated add it to the cookie
    ctx.addCookie(
      Cookie.cookie(cookieName, token)
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
    String sessionToken = session.get(headerName);
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

  public void setCookiePath(String cookiePath) {
    this.cookiePath = cookiePath;
  }

  public void setCookieHttpOnly(boolean httpOnly) {
    this.httpOnly = httpOnly;
  }
}
