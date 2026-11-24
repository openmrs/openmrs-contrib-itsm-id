package org.openmrs.keycloak.rehash;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

/** Registers {@link LdapPasswordRehashAuthenticator}. */
public class LdapPasswordRehashAuthenticatorFactory implements AuthenticatorFactory {

  static final String PROVIDER_ID = "ldap-password-rehash";

  private static final Requirement[] REQUIREMENTS = {Requirement.REQUIRED, Requirement.DISABLED};

  private static final List<ProviderConfigProperty> CONFIG =
      ProviderConfigurationBuilder.create()
          .property()
          .name(LdapPasswordRehashAuthenticator.SCHEME_CONFIG)
          .label("Current scheme")
          .helpText(
              "Prefix of userPassword values that are already up to date. Passwords stored any other"
                  + " way are written back to LDAP at login so the directory rehashes them. Include"
                  + " the parameters (e.g. {ARGON2}$argon2i$v=19$m=65536,t=3,p=1$) to also upgrade"
                  + " hashes made with weaker settings.")
          .type(ProviderConfigProperty.STRING_TYPE)
          .defaultValue(LdapPasswordRehashAuthenticator.DEFAULT_SCHEME)
          .add()
          .build();

  private static final LdapPasswordRehashAuthenticator SINGLETON =
      new LdapPasswordRehashAuthenticator(new KeycloakLdapPasswords());

  @Override
  public Authenticator create(KeycloakSession session) {
    return SINGLETON;
  }

  @Override
  public String getId() {
    return PROVIDER_ID;
  }

  @Override
  public String getDisplayType() {
    return "Upgrade LDAP Password Hash";
  }

  @Override
  public String getReferenceCategory() {
    return "password";
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public Requirement[] getRequirementChoices() {
    return REQUIREMENTS;
  }

  @Override
  public boolean isUserSetupAllowed() {
    return false;
  }

  @Override
  public String getHelpText() {
    return "Place directly after the username/password form. Writes the submitted password back to"
        + " LDAP when the stored hash uses an outdated scheme, so the directory rehashes it. Never"
        + " blocks a login.";
  }

  @Override
  public List<ProviderConfigProperty> getConfigProperties() {
    return CONFIG;
  }

  @Override
  public void init(Config.Scope config) {}

  @Override
  public void postInit(KeycloakSessionFactory factory) {}

  @Override
  public void close() {}
}
