package org.openmrs.keycloak.rehash;

import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.storage.UserStorageProvider.EditMode;
import org.keycloak.storage.ldap.LDAPStorageProvider;

/**
 * Upgrades LDAP password hashes to the directory's current scheme as users log in.
 *
 * <p>Placed directly after the username/password form, it runs in the same request that submitted
 * the password. If the user is federated from a writable LDAP provider and any of their stored
 * {@code userPassword} values doesn't start with the configured prefix ({@value
 * #DEFAULT_SCHEME} by default), it writes the submitted password back to LDAP, which hashes it
 * with its default scheme ({@code olcPasswordHash}).
 *
 * <p>It never affects whether the login succeeds: every problem is logged and the flow continues.
 * The submitted password is checked against LDAP again before it is written, so a password is only
 * ever written back if the directory already accepts it.
 */
public class LdapPasswordRehashAuthenticator implements Authenticator {

  private static final Logger LOG = Logger.getLogger(LdapPasswordRehashAuthenticator.class);

  /** Authenticator config key for the prefix that up-to-date {@code userPassword} values have. */
  static final String SCHEME_CONFIG = "scheme";

  static final String DEFAULT_SCHEME = "{ARGON2}";

  /** Password schemes built into OpenLDAP or provided by its password modules. */
  private static final Set<String> KNOWN_SCHEMES =
      Set.of(
          "{ARGON2}", "{CRYPT}", "{MD5}", "{SMD5}", "{SHA}", "{SSHA}", "{SHA256}", "{SSHA256}",
          "{SHA384}", "{SSHA384}", "{SHA512}", "{SSHA512}", "{PBKDF2}", "{PBKDF2-SHA1}",
          "{PBKDF2-SHA256}", "{PBKDF2-SHA512}", "{SASL}", "{CLEARTEXT}", "{TOTP1}", "{TOTP256}",
          "{TOTP512}");

  private final LdapPasswords ldap;

  LdapPasswordRehashAuthenticator(LdapPasswords ldap) {
    this.ldap = ldap;
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    try {
      rehashIfNeeded(context);
    } catch (RuntimeException e) {
      UserModel user = context.getUser();
      LOG.warnf(
          e,
          "Could not upgrade the LDAP password hash for %s",
          user == null ? null : user.getUsername());
    }
    context.success();
  }

  private void rehashIfNeeded(AuthenticationFlowContext context) {
    UserModel user = context.getUser();
    if (user == null || user.getFederationLink() == null) {
      return;
    }
    String password = submittedPassword(context);
    if (password == null || password.isEmpty()) {
      return;
    }

    KeycloakSession session = context.getSession();
    RealmModel realm = context.getRealm();
    LDAPStorageProvider provider = ldap.providerFor(session, realm, user);
    if (provider == null || provider.getEditMode() != EditMode.WRITABLE) {
      return;
    }

    List<String> stored = ldap.storedPasswords(provider, realm, user);
    if (stored.isEmpty()) {
      // Either there's no password, or the bind user isn't allowed to read it
      LOG.debugf("No readable userPassword in LDAP for %s; not rehashing", user.getUsername());
      return;
    }
    String scheme = scheme(context.getAuthenticatorConfig());
    if (isCurrent(stored, scheme)) {
      return;
    }

    UserCredentialModel credential = UserCredentialModel.password(password, false);
    if (!provider.isValid(realm, user, credential)) {
      LOG.debugf("LDAP did not accept the submitted password for %s; not rehashing", user.getUsername());
      return;
    }
    if (!provider.updateCredential(realm, user, credential)) {
      return;
    }
    List<String> rewritten = ldap.storedPasswords(provider, realm, user);
    if (isCurrent(rewritten, scheme)) {
      LOG.infof("Upgraded the LDAP password hash for %s", user.getUsername());
    } else {
      // Otherwise every login would quietly rewrite the password again
      LOG.warnf(
          "Rewrote the LDAP password for %s but LDAP stored it as %s rather than %s; check the"
              + " directory's olcPasswordHash and olcPPolicyHashCleartext settings",
          user.getUsername(),
          rewritten.stream().map(LdapPasswordRehashAuthenticator::schemeOf).toList(),
          scheme);
    }
  }

  /**
   * Describes how a stored password is kept, for logging: its {SCHEME} prefix if that's a scheme
   * OpenLDAP knows, otherwise "cleartext". Only known names are logged because a cleartext password
   * can itself start with something like {Secret}.
   */
  static String schemeOf(String value) {
    int end = value == null || !value.startsWith("{") ? -1 : value.indexOf('}');
    if (end < 0) {
      return "cleartext";
    }
    String scheme = value.substring(0, end + 1).toUpperCase(Locale.ROOT);
    return KNOWN_SCHEMES.contains(scheme) ? scheme : "cleartext";
  }

  private static String submittedPassword(AuthenticationFlowContext context) {
    MultivaluedMap<String, String> form = context.getHttpRequest().getDecodedFormParameters();
    return form == null ? null : form.getFirst(CredentialRepresentation.PASSWORD);
  }

  static String scheme(AuthenticatorConfigModel config) {
    Map<String, String> values = config == null ? null : config.getConfig();
    String scheme = values == null ? null : values.get(SCHEME_CONFIG);
    return scheme == null || scheme.isBlank() ? DEFAULT_SCHEME : scheme.trim();
  }

  /** True if there are stored values and every one starts with the scheme prefix, ignoring case. */
  static boolean isCurrent(List<String> stored, String scheme) {
    String prefix = scheme.toUpperCase(Locale.ROOT);
    return !stored.isEmpty()
        && stored.stream()
            .allMatch(value -> value != null && value.toUpperCase(Locale.ROOT).startsWith(prefix));
  }

  @Override
  public void action(AuthenticationFlowContext context) {
    // Never challenges, so there is nothing to submit to this step
    context.success();
  }

  @Override
  public boolean requiresUser() {
    return true;
  }

  @Override
  public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
    return true;
  }

  @Override
  public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

  @Override
  public void close() {}
}
