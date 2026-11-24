package org.openmrs.keycloak.rehash;

import jakarta.ws.rs.core.MultivaluedMap;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * ever written back if the directory already accepts it. If LDAP stores a rewritten password
 * without the configured scheme, the directory isn't set up for it, so rewrites stop for {@link
 * #PAUSE_AFTER_MISMATCH} rather than happening on every login.
 */
public class LdapPasswordRehashAuthenticator implements Authenticator {

  private static final Logger LOG = Logger.getLogger(LdapPasswordRehashAuthenticator.class);

  /** Authenticator config key for the prefix that up-to-date {@code userPassword} values have. */
  static final String SCHEME_CONFIG = "scheme";

  static final String DEFAULT_SCHEME = "{ARGON2}";

  static final Duration PAUSE_AFTER_MISMATCH = Duration.ofHours(1);

  private final LdapPasswords ldap;
  private final Clock clock;

  /**
   * When rewrites may resume after LDAP stored one without the configured scheme, by LDAP provider
   * component id and scheme. The authenticator is shared by every realm, provider and flow, so one
   * misconfigured directory mustn't pause the others.
   */
  private final Map<String, Instant> pausedUntil = new ConcurrentHashMap<>();

  LdapPasswordRehashAuthenticator(LdapPasswords ldap) {
    this(ldap, Clock.systemUTC());
  }

  LdapPasswordRehashAuthenticator(LdapPasswords ldap, Clock clock) {
    this.ldap = ldap;
    this.clock = clock;
  }

  @Override
  public void authenticate(AuthenticationFlowContext context) {
    try {
      rehashIfNeeded(context);
    } catch (RuntimeException | LinkageError e) {
      // LinkageError too: this relies on Keycloak's LDAP internals, which can change on upgrade
      logFailure(context, e);
    }
    context.success();
  }

  private static void logFailure(AuthenticationFlowContext context, Throwable e) {
    try {
      UserModel user = context.getUser();
      LOG.warnf(
          e,
          "Could not upgrade the LDAP password hash for %s",
          user == null ? null : user.getUsername());
    } catch (RuntimeException | LinkageError loggingFailure) {
      e.addSuppressed(loggingFailure);
      LOG.warn("Could not upgrade the LDAP password hash", e);
    }
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
    String pauseKey = user.getFederationLink() + " " + scheme;
    if (isCurrent(stored, scheme)
        || clock.instant().isBefore(pausedUntil.getOrDefault(pauseKey, Instant.MIN))) {
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
      // Otherwise every login would rewrite a password again. Nothing from the stored value is
      // logged: it can't be told apart from a cleartext password, which could look like a hash
      Instant resume = clock.instant().plus(PAUSE_AFTER_MISMATCH);
      pausedUntil.put(pauseKey, resume);
      LOG.warnf(
          "Rewrote the LDAP password for %s but LDAP did not store it with %s; pausing rewrites"
              + " from this LDAP provider until %s. Check the directory's olcPasswordHash and olcPPolicyHashCleartext"
              + " settings",
          user.getUsername(),
          scheme,
          resume);
    }
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
