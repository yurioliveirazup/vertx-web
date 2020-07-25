package io.vertx.ext.web.handler.impl;

import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;

import javax.crypto.Mac;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;

class HttpRequestHandler {

  private static final Logger log = LoggerFactory.getLogger(HttpRequestHandler.class);

  private static final Base64.Encoder BASE64 = Base64.getMimeEncoder();

  private TokenHelper tokenHelper;
  private long timeout;
  private Mac mac;
  private RequestConfig requestConfig;
  private URI origin;
  private boolean nagHttps;

  public HttpRequestHandler(TokenHelper tokenHelper, long timeout, Mac mac, RequestConfig requestConfig) {
    this.tokenHelper = tokenHelper;
    this.timeout = timeout;
    this.mac = mac;
    this.requestConfig = requestConfig;
  }

  public void handleRequestTo(RoutingContext ctx) {
    if (nagHttps) {
      String uri = ctx.request().absoluteURI();
      if (uri != null && !uri.startsWith("https:")) {
        log.trace("Using session cookies without https could make you susceptible to session hijacking: " + uri);
      }
    }

    HttpMethod method = ctx.request().method();
    Session session = ctx.session();

    // if we're being strict with the origin
    // ensure that they are always valid
    if (!isValidOrigin(ctx)) {
      ctx.fail(403);
      return;
    }

    switch (method.name()) {
      case "GET":
        final String token;

        if (session == null) {
          // if there's no session to store values, tokens are issued on every request
          token = tokenHelper.generateAndStoreToken(ctx);
        } else {
          // get the token from the session, this also considers the fact
          // that the token might be invalid as it was issued for a previous session id
          // session id's change on session upgrades (unauthenticated -> authenticated; role change; etc...)
          String sessionToken = tokenHelper.getTokenFromSession(ctx);
          // when there's no token in the session, then we behave just like when there is no session
          // create a new token, but we also store it in the session for the next runs
          if (sessionToken == null) {
            token = tokenHelper.generateAndStoreToken(ctx);
            // storing will include the session id too. The reason is that if a session is upgraded
            // we don't want to allow the token to be valid anymore
            session.put(requestConfig.getHeaderName(), session.id() + "/" + token);
          } else {
            String[] parts = sessionToken.split("\\.");
            final long ts = tokenHelper.parseTokenToLong(parts[1]);

            if (ts == -1) {
              // fallback as the token is expired
              token = tokenHelper.generateAndStoreToken(ctx);
            } else {
              if (!(System.currentTimeMillis() > ts + timeout)) {
                // we're still on the same session, no need to regenerate the token
                // also note that the token isn't expired, so it can be reused
                token = sessionToken;
                // in this case specifically we don't issue the token as it is unchanged
                // the user agent still has it from the previous interaction.
              } else {
                // fallback as the token is expired
                token = tokenHelper.generateAndStoreToken(ctx);
              }
            }
          }
        }
        // put the token in the context for users who prefer to render the token directly on the HTML
        ctx.put(requestConfig.getHeaderName(), token);
        ctx.next();
        break;
      case "POST":
      case "PUT":
      case "DELETE":
      case "PATCH":
        if (isValidRequest(ctx)) {
          // it matches, so refresh the token to avoid replay attacks
          token = tokenHelper.generateAndStoreToken(ctx);
          // put the token in the context for users who prefer to
          // render the token directly on the HTML
          ctx.put(requestConfig.getHeaderName(), token);
          ctx.next();
        } else {
          ctx.fail(403);
        }
        break;
      default:
        // ignore other methods
        ctx.next();
        break;
    }
  }

  private boolean isValidOrigin(RoutingContext  ctx) {
    /* Verifying Same Origin with Standard Headers */

    return new OriginValidator(origin).validateOriginBy(ctx);
  }

  private boolean isValidRequest(RoutingContext ctx) {

    /* Verifying CSRF token using "Double Submit Cookie" approach */
    final Cookie cookie = ctx.getCookie(requestConfig.getCookieName());

    String header = ctx.request().getHeader(requestConfig.getHeaderName());
    if (header == null) {
      // fallback to form attributes
      header = ctx.request().getFormAttribute(requestConfig.getHeaderName());
    }

    // both the header and the cookie must be present, not null and not empty
    if (header == null || cookie == null || StringUtils.isBlank(header) || StringUtils.isBlank(cookie.getValue())) {
      log.trace("Token provided via HTTP Header/Form is absent/empty");
      return false;
    }

    //Verify that token from header and one from cookie are the same
    if (!header.equals(cookie.getValue())) {
      log.trace("Token provided via HTTP Header and via Cookie are not equal");
      return false;
    }

    if (ctx.session() != null) {
      Session session = ctx.session();

      // get the token from the session
      String sessionToken = session.get(requestConfig.getHeaderName());
      if (sessionToken != null) {
        // attempt to parse the value
        int idx = sessionToken.indexOf('/');
        if (idx != -1 && session.id() != null && session.id().equals(sessionToken.substring(0, idx))) {
          String challenge = sessionToken.substring(idx + 1);
          // the challenge must match the user-agent input
          if (!challenge.equals(header)) {
            log.trace("Token has been used or is outdated");
            return false;
          }
        } else {
          log.trace("Token has been issued for a different session");
          return false;
        }
      } else {
        log.trace("No Token has been added to the session");
        return false;
      }
    }

    String[] tokens = header.split("\\.");
    if (tokens.length != 3) {
      return false;
    }

    byte[] saltPlusToken = (tokens[0] + "." + tokens[1]).getBytes();

    synchronized (mac) {
      saltPlusToken = mac.doFinal(saltPlusToken);
    }

    String signature = BASE64.encodeToString(saltPlusToken);

    if(!signature.equals(tokens[2])) {
      log.trace("Token signature does not match");
      return false;
    }

    // this token has been used and we discard it to avoid replay attacks
    if (ctx.session() != null) {
      ctx.session().remove(requestConfig.getHeaderName());
    }

    final long ts = tokenHelper.parseTokenToLong(tokens[1]);

    if (ts == -1) {
      return false;
    }

    // validate validity
    return !(System.currentTimeMillis() > ts + timeout);
  }

  public void setOrigin(String origin) {
    try {
      this.origin = new URI(origin);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  public void setNagHttps(boolean nag) {
    this.nagHttps = nag;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }
}