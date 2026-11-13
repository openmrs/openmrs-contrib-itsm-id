package org.openmrs.keycloak.turnstile;

import jakarta.ws.rs.core.MultivaluedMap;
import org.keycloak.Config;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.browser.AbstractUsernameFormAuthenticator;
import org.keycloak.authentication.authenticators.resetcred.ResetCredentialChooseUser;
import org.keycloak.services.managers.AuthenticationManager;

/**
 * Replacement for the reset-credentials flow's "Choose User" step that also checks a Cloudflare
 * Turnstile token before accepting the username, so bots can't use the form to send reset emails.
 * See {@link Turnstile} for when requests are blocked.
 */
public class TurnstileResetCredentialChooseUser extends ResetCredentialChooseUser {

  static final String PROVIDER_ID = "reset-credentials-turnstile";

  private Turnstile turnstile;

  public TurnstileResetCredentialChooseUser() {}

  TurnstileResetCredentialChooseUser(Turnstile turnstile) {
    this.turnstile = turnstile;
  }

  @Override
  public void init(Config.Scope config) {
    turnstile = Turnstile.fromEnvironment(System.getenv());
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    addSiteKey(context);
    super.authenticate(context);
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    // The parent re-renders the form on its own errors, so the widget needs to be there too
    addSiteKey(context);

    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
    if (turnstile.allows(
        context.getSession(),
        formData.getFirst(Turnstile.RESPONSE_FIELD),
        context.getConnection().getRemoteAddr())) {
      super.action(context);
      return;
    }

    // Lets the template refill the username field
    String username = formData.getFirst(AuthenticationManager.FORM_USERNAME);
    if (username != null) {
      context
          .getAuthenticationSession()
          .setAuthNote(AbstractUsernameFormAuthenticator.ATTEMPTED_USERNAME, username.trim());
    }
    // challenge() rather than failureChallenge(): the latter feeds brute-force detection for the
    // attempted username, which would let anyone lock out an account by failing this check
    context.challenge(context.form().setError(Turnstile.FAILED_MESSAGE).createPasswordReset());
  }

  private void addSiteKey(AuthenticationFlowContext context) {
    String siteKey = turnstile.siteKey();
    if (siteKey != null) {
      context.form().setAttribute(Turnstile.SITE_KEY_ATTRIBUTE, siteKey);
    }
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "Choose User with Cloudflare Turnstile";
  }

  @Override
  public String getReferenceCategory() {
    return "turnstile";
  }

  @Override
  public String getHelpText() {
    return "Choose a user to reset credentials for, after passing a Cloudflare Turnstile challenge.";
  }
}
