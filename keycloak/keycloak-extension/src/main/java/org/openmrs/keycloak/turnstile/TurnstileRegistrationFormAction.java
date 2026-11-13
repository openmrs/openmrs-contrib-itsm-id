package org.openmrs.keycloak.turnstile;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.FormAction;
import org.keycloak.authentication.FormActionFactory;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.ValidationContext;
import org.keycloak.events.Errors;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;

/**
 * Registration form step that checks a Cloudflare Turnstile token, a drop-in replacement for the
 * built-in reCAPTCHA step. See {@link Turnstile} for when requests are blocked.
 */
public class TurnstileRegistrationFormAction implements FormAction, FormActionFactory {

  static final String PROVIDER_ID = "registration-turnstile-action";

  private static final AuthenticationExecutionModel.Requirement[] REQUIREMENT_CHOICES = {
    AuthenticationExecutionModel.Requirement.REQUIRED,
    AuthenticationExecutionModel.Requirement.DISABLED
  };

  private Turnstile turnstile;

  public TurnstileRegistrationFormAction() {}

  TurnstileRegistrationFormAction(Turnstile turnstile) {
    this.turnstile = turnstile;
  }

  @Override
  public void buildPage(FormContext context, LoginFormsProvider form) {
    String siteKey = turnstile.siteKey();
    if (siteKey != null) {
      form.setAttribute(Turnstile.SITE_KEY_ATTRIBUTE, siteKey);
    }
  }

  @Override
  public void validate(ValidationContext context) {
    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
    if (turnstile.allows(
        context.getSession(),
        formData.getFirst(Turnstile.RESPONSE_FIELD),
        context.getConnection().getRemoteAddr())) {
      context.success();
      return;
    }

    // Tokens are single-use, so don't echo this one back into the re-rendered form
    formData.remove(Turnstile.RESPONSE_FIELD);
    context.error(Errors.INVALID_REGISTRATION);
    context.validationError(formData, List.of(new FormMessage(null, Turnstile.FAILED_MESSAGE)));
    context.excludeOtherErrors();
  }

  @Override
  public void success(FormContext context) {}

  @Override
  public boolean requiresUser() {
    return false;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "Cloudflare Turnstile";
  }

  @Override
  public String getReferenceCategory() {
    return "turnstile";
  }

  @Override
  public String getHelpText() {
    return "Adds a Cloudflare Turnstile challenge to the registration form.";
  }

  @Override
  public boolean isConfigurable() {
    return false;
  }

  @Override
  public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
    return REQUIREMENT_CHOICES;
  }

  @Override
  public boolean isUserSetupAllowed() {
    return false;
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return List.of();
  }

  @Override
  public FormAction create(KeycloakSession session) {
    return this;
  }

  @Override
  public void init(Config.Scope config) {
    turnstile = Turnstile.fromEnvironment(System.getenv());
  }

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}
