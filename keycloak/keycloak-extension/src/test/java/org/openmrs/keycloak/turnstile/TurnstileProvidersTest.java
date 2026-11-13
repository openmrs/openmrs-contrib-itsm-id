package org.openmrs.keycloak.turnstile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.sessions.AuthenticationSessionModel;

class TurnstileProvidersTest {

  private final KeycloakSession session = mock(KeycloakSession.class);
  private final HttpRequest httpRequest = mock(HttpRequest.class);
  private final ClientConnection connection = mock(ClientConnection.class);
  private final LoginFormsProvider form = mock(LoginFormsProvider.class, RETURNS_SELF);
  private final Turnstile turnstile = mock(Turnstile.class);
  private final MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();

  @BeforeEach
  void setUp() {
    when(httpRequest.getDecodedFormParameters()).thenReturn(formData);
    when(connection.getRemoteAddr()).thenReturn("10.0.0.1");
    formData.putSingle("username", " alice ");
    formData.putSingle(Turnstile.RESPONSE_FIELD, "token");
    lenient().when(turnstile.siteKey()).thenReturn("site-key");
  }

  private void verdict(boolean allowed) {
    when(turnstile.allows(session, "token", "10.0.0.1")).thenReturn(allowed);
  }

  @Nested
  @DisplayName("Registration form action")
  class RegistrationTests {

    private final TurnstileRegistrationFormAction action =
        new TurnstileRegistrationFormAction(turnstile);
    private final ValidationContext context = mock(ValidationContext.class);

    @BeforeEach
    void setUp() {
      when(context.getSession()).thenReturn(session);
      when(context.getHttpRequest()).thenReturn(httpRequest);
      when(context.getConnection()).thenReturn(connection);
    }

    @Test
    @DisplayName("exposes the site key to the template")
    void buildPageSetsSiteKey() {
      action.buildPage(mock(FormContext.class), form);
      verify(form).setAttribute(Turnstile.SITE_KEY_ATTRIBUTE, "site-key");
    }

    @Test
    @DisplayName("doesn't render the widget when unconfigured")
    void buildPageWithoutConfig() {
      when(turnstile.siteKey()).thenReturn(null);
      action.buildPage(mock(FormContext.class), form);
      verifyNoInteractions(form);
    }

    @Test
    @DisplayName("succeeds when Turnstile allows the request")
    void validateSuccess() {
      verdict(true);
      action.validate(context);
      verify(context).success();
      verify(context, never()).validationError(any(), anyList());
    }

    @Test
    @DisplayName("re-renders the form without the spent token when rejected")
    void validateRejected() {
      verdict(false);
      action.validate(context);
      verify(context, never()).success();
      verify(context).error(Errors.INVALID_REGISTRATION);
      verify(context).validationError(eq(formData), anyList());
      verify(context).excludeOtherErrors();
      assertFalse(formData.containsKey(Turnstile.RESPONSE_FIELD));
    }
  }

  @Nested
  @DisplayName("Reset credentials step")
  class ResetTests {

    private final AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
    private final AuthenticationSessionModel authSession = mock(AuthenticationSessionModel.class);
    private final TurnstileResetCredentialChooseUser authenticator =
        new TurnstileResetCredentialChooseUser(turnstile);

    @BeforeEach
    void setUp() {
      when(context.getSession()).thenReturn(session);
      when(context.getHttpRequest()).thenReturn(httpRequest);
      when(context.getConnection()).thenReturn(connection);
      when(context.getAuthenticationSession()).thenReturn(authSession);
      when(context.form()).thenReturn(form);
    }

    @Test
    @DisplayName("is its own provider, configured from the environment rather than the console")
    void identity() {
      assertEquals("reset-credentials-turnstile", authenticator.getId());
      assertFalse(authenticator.isConfigurable());
      assertSame(authenticator, authenticator.create(session));
    }

    @Test
    @DisplayName("re-renders the reset form with an error and the username when rejected")
    void actionRejected() {
      Response page = mock(Response.class);
      when(form.createPasswordReset()).thenReturn(page);

      verdict(false);
      authenticator.action(context);

      verify(form).setAttribute(Turnstile.SITE_KEY_ATTRIBUTE, "site-key");
      verify(form).setError(Turnstile.FAILED_MESSAGE);
      verify(authSession).setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, "alice");
      verify(context).challenge(page);
      verify(context, never()).failureChallenge(any(), any());
    }
  }
}
