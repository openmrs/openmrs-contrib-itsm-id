package org.openmrs.keycloak.rehash;

import java.util.List;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.ldap.LDAPStorageProvider;

/**
 * The LDAP lookups {@link LdapPasswordRehashAuthenticator} needs, kept behind an interface so the
 * decision logic can be tested without a directory.
 */
interface LdapPasswords {

  /** Returns the LDAP provider the user is federated from, or null if it isn't an LDAP provider. */
  LDAPStorageProvider providerFor(KeycloakSession session, RealmModel realm, UserModel user);

  /**
   * Returns the user's {@code userPassword} values as stored in LDAP (for example {@code
   * {SSHA}...}), or an empty list if there are none or they can't be read.
   */
  List<String> storedPasswords(LDAPStorageProvider provider, RealmModel realm, UserModel user);
}
