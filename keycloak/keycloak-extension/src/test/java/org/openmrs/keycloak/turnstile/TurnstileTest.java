package org.openmrs.keycloak.turnstile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.client.config.RequestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.http.simple.SimpleHttp;
import org.keycloak.http.simple.SimpleHttpRequest;
import org.keycloak.http.simple.SimpleHttpResponse;
import org.keycloak.models.KeycloakSession;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class TurnstileTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Turnstile turnstile = new Turnstile("site", "secret");

  private static JsonNode json(String body) throws IOException {
    return MAPPER.readTree(body);
  }

  @Nested
  @DisplayName("Configuration")
  class ConfigurationTests {

    @Test
    @DisplayName("reads both keys from the environment, trimming whitespace")
    void readsEnvironment() {
      Turnstile fromEnv =
          Turnstile.fromEnvironment(
              Map.of(Turnstile.SITE_KEY_ENV, " site ", Turnstile.SECRET_ENV, "secret"));
      assertTrue(fromEnv.isConfigured());
      assertEquals("site", fromEnv.siteKey());
    }

    @Test
    @DisplayName("is inactive unless both keys are set")
    void inactiveUnlessBothSet() {
      Map<String, String> secretOnly = new HashMap<>();
      secretOnly.put(Turnstile.SITE_KEY_ENV, " ");
      secretOnly.put(Turnstile.SECRET_ENV, "secret");

      for (Map<String, String> env :
          List.of(Map.<String, String>of(), Map.of(Turnstile.SITE_KEY_ENV, "site"), secretOnly)) {
        Turnstile partial = Turnstile.fromEnvironment(env);
        assertFalse(partial.isConfigured(), env.toString());
        assertNull(partial.siteKey(), env.toString());
      }
    }
  }

  @Nested
  @DisplayName("interpret()")
  class InterpretTests {

    @Test
    @DisplayName("passes a successful verification")
    void passesSuccess() throws IOException {
      assertEquals(Turnstile.Verdict.PASS, Turnstile.interpret(200, json("{\"success\":true}")));
    }

    @Test
    @DisplayName("rejects invalid, expired and reused tokens")
    void rejectsBadTokens() throws IOException {
      for (String code : new String[] {"invalid-input-response", "timeout-or-duplicate"}) {
        assertEquals(
            Turnstile.Verdict.REJECT,
            Turnstile.interpret(
                200, json("{\"success\":false,\"error-codes\":[\"" + code + "\"]}")),
            code);
      }
    }

    @Test
    @DisplayName("gives no verdict for server-side and configuration errors")
    void noVerdictForOurOrCloudflaresErrors() throws IOException {
      for (String code :
          new String[] {"internal-error", "invalid-input-secret", "missing-input-secret"}) {
        assertEquals(
            Turnstile.Verdict.NO_VERDICT,
            Turnstile.interpret(
                200, json("{\"success\":false,\"error-codes\":[\"" + code + "\"]}")),
            code);
      }
    }

    @Test
    @DisplayName("gives no verdict for non-2xx or malformed responses")
    void noVerdictForBadResponses() throws IOException {
      assertEquals(Turnstile.Verdict.NO_VERDICT, Turnstile.interpret(503, json("{}")));
      assertEquals(Turnstile.Verdict.NO_VERDICT, Turnstile.interpret(200, json("[]")));
      assertEquals(Turnstile.Verdict.NO_VERDICT, Turnstile.interpret(200, null));
    }
  }

  @Nested
  @DisplayName("allows()")
  class AllowsTests {

    private final KeycloakSession session = mock(KeycloakSession.class);

    @Test
    @DisplayName("allows everything when inactive, without calling Cloudflare")
    void allowsWhenUnconfigured() {
      try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class)) {
        assertTrue(new Turnstile(null, null).allows(session, null, null));
        // A secret without a site key would otherwise block everyone, as no widget renders
        assertTrue(new Turnstile(" ", "secret").allows(session, null, null));
        http.verifyNoInteractions();
      }
    }

    @Test
    @DisplayName("blocks a missing token without calling Cloudflare")
    void blocksMissingToken() {
      try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class)) {
        assertFalse(turnstile.allows(session, null, "10.0.0.1"));
        assertFalse(turnstile.allows(session, "  ", "10.0.0.1"));
        http.verifyNoInteractions();
      }
    }

    @Test
    @DisplayName("sends the secret, token and IP, and follows Cloudflare's verdict")
    void followsVerdict() throws IOException {
      SimpleHttpRequest request = mockRequest(200, "{\"success\":false}");
      try (MockedStatic<SimpleHttp> http = mockSimpleHttp(request)) {
        assertFalse(turnstile.allows(session, "token", "10.0.0.1"));
      }
      verify(request).param("secret", "secret");
      verify(request).param("response", "token");
      verify(request).param("remoteip", "10.0.0.1");
    }

    @Test
    @DisplayName("omits remoteip when the IP is unknown")
    void omitsMissingIp() throws IOException {
      SimpleHttpRequest request = mockRequest(200, "{\"success\":true}");
      try (MockedStatic<SimpleHttp> http = mockSimpleHttp(request)) {
        assertTrue(turnstile.allows(session, "token", null));
      }
      verify(request, never()).param(eq("remoteip"), any());
    }

    @Test
    @DisplayName("allows the request when Cloudflare can't be reached")
    void allowsOnNetworkFailure() throws IOException {
      SimpleHttpRequest request = mockRequest(200, "{}");
      when(request.asResponse()).thenThrow(new IOException("connect timed out"));
      try (MockedStatic<SimpleHttp> http = mockSimpleHttp(request)) {
        assertTrue(turnstile.allows(session, "token", "10.0.0.1"));
      }
    }

    private SimpleHttpRequest mockRequest(int status, String body) throws IOException {
      SimpleHttpRequest request = mock(SimpleHttpRequest.class);
      when(request.param(anyString(), anyString())).thenReturn(request);
      SimpleHttpResponse response = mock(SimpleHttpResponse.class);
      lenient().when(response.getStatus()).thenReturn(status);
      lenient().when(response.asJson()).thenReturn(json(body));
      lenient().when(request.asResponse()).thenReturn(response);
      return request;
    }

    @Test
    @DisplayName("uses Cloudflare's endpoint and a 5s timeout by default")
    void defaultEndpointAndTimeout() throws IOException {
      assertEndpointAndTimeout(
          Map.of(Turnstile.SITE_KEY_ENV, "site", Turnstile.SECRET_ENV, "secret"),
          Turnstile.DEFAULT_SITEVERIFY_URL,
          5000);
    }

    @Test
    @DisplayName("uses the endpoint and timeout from the environment")
    void configuredEndpointAndTimeout() throws IOException {
      assertEndpointAndTimeout(
          Map.of(
              Turnstile.SITE_KEY_ENV, "site",
              Turnstile.SECRET_ENV, "secret",
              Turnstile.SITEVERIFY_URL_ENV, "http://mock/siteverify",
              Turnstile.TIMEOUT_MILLIS_ENV, "1500"),
          "http://mock/siteverify",
          1500);
    }

    @Test
    @DisplayName("falls back to the default timeout when the configured one is invalid")
    void invalidTimeout() throws IOException {
      for (String timeout : new String[] {"soon", "0", "-5"}) {
        assertEndpointAndTimeout(
            Map.of(
                Turnstile.SITE_KEY_ENV, "site",
                Turnstile.SECRET_ENV, "secret",
                Turnstile.TIMEOUT_MILLIS_ENV, timeout),
            Turnstile.DEFAULT_SITEVERIFY_URL,
            5000);
      }
    }

    private void assertEndpointAndTimeout(Map<String, String> env, String url, int timeoutMillis)
        throws IOException {
      SimpleHttpRequest request = mockRequest(200, "{\"success\":true}");
      SimpleHttp simpleHttp = mock(SimpleHttp.class);
      when(simpleHttp.withRequestConfig(any())).thenReturn(simpleHttp);
      when(simpleHttp.doPost(url)).thenReturn(request);
      try (MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class)) {
        http.when(() -> SimpleHttp.create(session)).thenReturn(simpleHttp);
        assertTrue(Turnstile.fromEnvironment(env).allows(session, "token", null));
      }

      ArgumentCaptor<RequestConfig> config = ArgumentCaptor.forClass(RequestConfig.class);
      verify(simpleHttp).withRequestConfig(config.capture());
      assertEquals(timeoutMillis, config.getValue().getConnectTimeout());
      assertEquals(timeoutMillis, config.getValue().getSocketTimeout());
    }

    private MockedStatic<SimpleHttp> mockSimpleHttp(SimpleHttpRequest request) {
      SimpleHttp simpleHttp = mock(SimpleHttp.class);
      when(simpleHttp.withRequestConfig(any())).thenReturn(simpleHttp);
      when(simpleHttp.doPost(Turnstile.DEFAULT_SITEVERIFY_URL)).thenReturn(request);
      MockedStatic<SimpleHttp> http = mockStatic(SimpleHttp.class);
      http.when(() -> SimpleHttp.create(session)).thenReturn(simpleHttp);
      return http;
    }
  }
}
