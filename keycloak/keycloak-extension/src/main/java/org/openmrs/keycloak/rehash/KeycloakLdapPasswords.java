package org.openmrs.keycloak.rehash;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.LDAPConstants;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.ldap.LDAPStorageProvider;
import org.keycloak.storage.ldap.LDAPUtils;
import org.keycloak.storage.ldap.idm.model.LDAPObject;
import org.keycloak.storage.ldap.idm.query.internal.LDAPQuery;
import org.keycloak.storage.ldap.idm.query.internal.LDAPQueryConditionsBuilder;

/** {@link LdapPasswords} backed by Keycloak's LDAP user federation. */
class KeycloakLdapPasswords implements LdapPasswords {

  static final String USER_PASSWORD = "userPassword";

  private static final String LDAP_PROVIDER_ID = "ldap";

  @Override
  public LDAPStorageProvider providerFor(
      KeycloakSession session, RealmModel realm, UserModel user) {
    String componentId = user.getFederationLink();
    if (componentId == null) {
      return null;
    }
    // Keycloak caches the provider instance it used to validate the password under the
    // component's id for the rest of the request
    if (session.getAttribute(componentId) instanceof LDAPStorageProvider provider) {
      return provider;
    }
    // Only the built-in "ldap" provider produces LDAPStorageProvider instances
    ComponentModel component = realm.getComponent(componentId);
    if (component == null || !LDAP_PROVIDER_ID.equals(component.getProviderId())) {
      return null;
    }
    return session.getComponentProvider(UserStorageProvider.class, componentId)
            instanceof LDAPStorageProvider provider
        ? provider
        : null;
  }

  @Override
  public List<String> storedPasswords(
      LDAPStorageProvider provider, RealmModel realm, UserModel user) {
    String ldapId = user.getFirstAttribute(LDAPConstants.LDAP_ID);
    if (ldapId == null) {
      return List.of();
    }
    try (LDAPQuery query = LDAPUtils.createQueryForUserSearch(provider, realm)) {
      String uuidAttribute = provider.getLdapIdentityStore().getConfig().getUuidLDAPAttributeName();
      query.addWhereCondition(new LDAPQueryConditionsBuilder().equal(uuidAttribute, ldapId));
      query.addReturningLdapAttribute(USER_PASSWORD);
      LDAPObject ldapUser = query.getFirstResult();
      if (ldapUser == null) {
        return List.of();
      }
      Set<String> values = ldapUser.getAttributeAsSet(USER_PASSWORD);
      return values == null
          ? List.of()
          : values.stream().map(KeycloakLdapPasswords::decode).toList();
    }
  }

  /**
   * Keycloak hands binary attribute values (which {@code userPassword} is, for JNDI) back
   * base64-encoded. Values that already look like a stored password are returned as is.
   */
  static String decode(String value) {
    if (value == null || value.startsWith("{")) {
      return value;
    }
    try {
      return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return value;
    }
  }
}
