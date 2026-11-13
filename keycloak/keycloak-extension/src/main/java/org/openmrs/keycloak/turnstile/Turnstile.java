package org.openmrs.keycloak.turnstile;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.apache.http.client.config.RequestConfig;
import org.jboss.logging.Logger;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.models.KeycloakSession;

/**
 * Cloudflare Turnstile verification shared by the registration and reset-credentials providers.
 *
 * <p>The keys come from the {@value #SITE_KEY_ENV} and {@value #SECRET_ENV} environment variables,
 * so the secret stays out of the database and realm exports. Turnstile is only active when both
 * are set. {@value #SITEVERIFY_URL_ENV} and {@value #TIMEOUT_MILLIS_ENV} optionally override the
 * verification endpoint and the timeout for calls to it.
 *
 * <p>Turnstile is a nuisance for bots, not a gate. Only an explicit rejection of the token by
 * Cloudflare blocks a request; if Turnstile isn't configured or Cloudflare can't give a verdict
 * (network failure, outage, a bad secret), the request is allowed and a warning is logged. A
 * missing token counts as a rejection, otherwise a bot could skip the check by omitting the field.
 *
 * <p>Templates render the widget when the {@value #SITE_KEY_ATTRIBUTE} attribute is set.
 */
public final class Turnstile {

  private static final Logger LOG = Logger.getLogger(Turnstile.class);

  static final String SITE_KEY_ENV = "TURNSTILE_SITE_KEY";
  static final String SECRET_ENV = "TURNSTILE_SECRET";
  static final String SITEVERIFY_URL_ENV = "TURNSTILE_SITEVERIFY_URL";
  static final String TIMEOUT_MILLIS_ENV = "TURNSTILE_TIMEOUT_MILLIS";

  /** Form field the Turnstile widget submits its token in. */
  static final String RESPONSE_FIELD = "cf-turnstile-response";

  static final String SITE_KEY_ATTRIBUTE = "turnstileSiteKey";

  /** Message bundle key for the error shown when Cloudflare rejects the token. */
  static final String FAILED_MESSAGE = "turnstileFailed";

  static final String DEFAULT_SITEVERIFY_URL =
      "https://challenges.cloudflare.com/turnstile/v0/siteverify";

  static final int DEFAULT_TIMEOUT_MILLIS = 5000;

  /**
   * siteverify error codes that mean Cloudflare or our configuration is at fault rather than the
   * visitor, so they're treated as no verdict.
   */
  private static final Set<String> NO_VERDICT_ERRORS =
      Set.of("missing-input-secret", "invalid-input-secret", "bad-request", "internal-error");

  enum Verdict {
    PASS,
    REJECT,
    NO_VERDICT
  }

  private final String siteKey;
  private final String secret;
  private final String siteverifyUrl;
  private final int timeoutMillis;

  Turnstile(String siteKey, String secret) {
    this(siteKey, secret, DEFAULT_SITEVERIFY_URL, DEFAULT_TIMEOUT_MILLIS);
  }

  Turnstile(String siteKey, String secret, String siteverifyUrl, int timeoutMillis) {
    this.siteKey = normalize(siteKey);
    this.secret = normalize(secret);
    this.siteverifyUrl = siteverifyUrl;
    this.timeoutMillis = timeoutMillis;
  }

  /** Reads the settings from the given environment, warning if Turnstile is left inactive. */
  static Turnstile fromEnvironment(Map<String, String> env) {
    String siteverifyUrl = normalize(env.get(SITEVERIFY_URL_ENV));
    Turnstile turnstile =
        new Turnstile(
            env.get(SITE_KEY_ENV),
            env.get(SECRET_ENV),
            siteverifyUrl == null ? DEFAULT_SITEVERIFY_URL : siteverifyUrl,
            timeoutMillis(env.get(TIMEOUT_MILLIS_ENV)));
    if (!turnstile.isConfigured()) {
      LOG.warnf(
          "Turnstile is inactive and will allow all requests; set both %s and %s to enable it",
          SITE_KEY_ENV,
          SECRET_ENV);
    }
    return turnstile;
  }

  /** Parses a positive timeout, falling back to the default (with a warning) if it isn't one. */
  private static int timeoutMillis(String value) {
    String timeout = normalize(value);
    if (timeout == null) {
      return DEFAULT_TIMEOUT_MILLIS;
    }
    try {
      int millis = Integer.parseInt(timeout);
      if (millis > 0) {
        return millis;
      }
    } catch (NumberFormatException e) {
      // Falls through to the warning
    }
    LOG.warnf(
        "Ignoring invalid %s '%s'; using %dms", TIMEOUT_MILLIS_ENV, timeout, DEFAULT_TIMEOUT_MILLIS);
    return DEFAULT_TIMEOUT_MILLIS;
  }

  boolean isConfigured() {
    return siteKey != null && secret != null;
  }

  /** Returns the site key for the widget, or null if Turnstile is inactive. */
  String siteKey() {
    return isConfigured() ? siteKey : null;
  }

  /**
   * Verifies a Turnstile token with Cloudflare.
   *
   * @param token the submitted {@value #RESPONSE_FIELD} value, possibly null
   * @param remoteIp the visitor's IP address, passed to Cloudflare as a signal; may be null
   * @return false only if the request should be blocked
   */
  boolean allows(KeycloakSession session, String token, String remoteIp) {
    if (!isConfigured()) {
      return true;
    }
    if (token == null || token.isBlank()) {
      return false;
    }

    RequestConfig timeouts =
        RequestConfig.custom()
            .setConnectTimeout(timeoutMillis)
            .setSocketTimeout(timeoutMillis)
            .build();
    SimpleHttpRequest request =
        SimpleHttp.create(session)
            .withRequestConfig(timeouts)
            .doPost(siteverifyUrl)
            .param("secret", secret)
            .param("response", token);
    if (remoteIp != null) {
      request.param("remoteip", remoteIp);
    }

    Verdict verdict;
    try (SimpleHttpResponse response = request.asResponse()) {
      verdict = interpret(response.getStatus(), response.asJson());
    } catch (IOException | RuntimeException e) {
      LOG.warn("Could not verify Turnstile token; allowing request", e);
      return true;
    }
    return verdict != Verdict.REJECT;
  }

  /** Maps a siteverify HTTP response to a verdict. */
  static Verdict interpret(int status, JsonNode body) {
    if (status < 200 || status >= 300 || body == null || !body.isObject()) {
      LOG.warnf("Unexpected Turnstile siteverify response (HTTP %d); allowing request", status);
      return Verdict.NO_VERDICT;
    }
    if (body.path("success").asBoolean(false)) {
      return Verdict.PASS;
    }

    JsonNode errorCodes = body.path("error-codes");
    for (JsonNode code : errorCodes) {
      if (NO_VERDICT_ERRORS.contains(code.asText())) {
        LOG.warnf("Turnstile siteverify failed with %s; allowing request", errorCodes);
        return Verdict.NO_VERDICT;
      }
    }
    LOG.debugf("Turnstile token rejected: %s", errorCodes);
    return Verdict.REJECT;
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
