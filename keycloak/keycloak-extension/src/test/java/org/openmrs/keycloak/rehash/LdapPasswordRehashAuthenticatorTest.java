package org.openmrs.keycloak.rehash;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.credential.CredentialInput;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider.EditMode;
import org.keycloak.storage.ldap.LDAPStorageProvider;
import org.mockito.ArgumentCaptor;

class LdapPasswordRehashAuthenticatorTest {

  private static final String SSHA = "{SSHA}I0g9bT3P/t4SjFvpmoozQ8CN9pDWVTX5";
  private static final String ARGON2 =
      "{ARGON2}$argon2i$v=19$m=65536,t=3,p=1$Qs6xTdqWWo/sV7EU5HZ0LA$YIKEe9sn+1subKyUnfBfR3Erkrs4LDEZ5atyX16tMAc";

  private final KeycloakSession session = mock(KeycloakSession.class);
  private final RealmModel realm = mock(RealmModel.class);
  private final UserModel user = mock(UserModel.class);
  private final HttpRequest httpRequest = mock(HttpRequest.class);
  private final AuthenticationFlowContext context = mock(AuthenticationFlowContext.class);
  private final LDAPStorageProvider provider = mock(LDAPStorageProvider.class);
  private final LdapPasswords ldap = mock(LdapPasswords.class);
  private final MultivaluedMap<String, String> form = new MultivaluedHashMap<>();
  private final LdapPasswordRehashAuthenticator authenticator =
      new LdapPasswordRehashAuthenticator(ldap);

  @BeforeEach
  void setUp() {
    when(context.getSession()).thenReturn(session);
    when(context.getRealm()).thenReturn(realm);
    when(context.getUser()).thenReturn(user);
    when(context.getHttpRequest()).thenReturn(httpRequest);
    when(httpRequest.getDecodedFormParameters()).thenReturn(form);
    when(user.getUsername()).thenReturn("alice");
    when(user.getFederationLink()).thenReturn("ldap-component");
    when(ldap.providerFor(session, realm, user)).thenReturn(provider);
    when(provider.getEditMode()).thenReturn(EditMode.WRITABLE);
    when(provider.isValid(eq(realm), eq(user), any())).thenReturn(true);
    when(provider.updateCredential(eq(realm), eq(user), any())).thenReturn(true);
    form.putSingle("username", "alice");
    form.putSingle("password", "s3cret-password");
  }

  private void stored(String... values) {
    when(ldap.storedPasswords(provider, realm, user)).thenReturn(List.of(values));
  }

  /** The stored values before the rewrite, then what LDAP stores after it. */
  private void storedThenRewritten(List<String> before, List<String> after) {
    when(ldap.storedPasswords(provider, realm, user)).thenReturn(before, after);
  }

  private void assertNotRewritten() {
    verify(provider, never()).updateCredential(any(), any(), any());
    verify(context).success();
  }

  @Test
  @DisplayName("writes a legacy-hashed password back to LDAP, after LDAP accepts it again")
  void rehashesLegacyHash() {
    storedThenRewritten(List.of(SSHA), List.of(ARGON2));

    authenticator.authenticate(context);

    ArgumentCaptor<CredentialInput> written = ArgumentCaptor.forClass(CredentialInput.class);
    var order = inOrder(provider);
    order.verify(provider).isValid(eq(realm), eq(user), any());
    order.verify(provider).updateCredential(eq(realm), eq(user), written.capture());
    assertEquals("password", written.getValue().getType());
    assertEquals("s3cret-password", written.getValue().getChallengeResponse());
    verify(context).success();
  }

  @Test
  @DisplayName("carries on if LDAP still stores the rewritten password with an old scheme")
  void rewriteStillLegacy() {
    stored(SSHA);
    authenticator.authenticate(context);
    verify(provider).updateCredential(eq(realm), eq(user), any());
    verify(ldap, times(2)).storedPasswords(provider, realm, user);
    verify(context).success();
  }

  @Test
  @DisplayName("doesn't re-read LDAP when the provider declines the update")
  void updateDeclined() {
    stored(SSHA);
    when(provider.updateCredential(eq(realm), eq(user), any())).thenReturn(false);
    authenticator.authenticate(context);
    verify(ldap, times(1)).storedPasswords(provider, realm, user);
    verify(context).success();
  }

  @Test
  @DisplayName("rewrites passwords stored in cleartext")
  void rehashesCleartext() {
    stored("s3cret-password");
    authenticator.authenticate(context);
    verify(provider).updateCredential(eq(realm), eq(user), any());
  }

  @Test
  @DisplayName("leaves up-to-date hashes alone, whatever the case of the scheme")
  void skipsCurrentHash() {
    stored(ARGON2.replace("{ARGON2}", "{argon2}"));
    authenticator.authenticate(context);
    assertNotRewritten();
    verify(provider, never()).isValid(any(), any(), any());
  }

  @Test
  @DisplayName("rewrites when any one of several stored values is outdated")
  void rehashesMixedValues() {
    stored(ARGON2, SSHA);
    authenticator.authenticate(context);
    verify(provider).updateCredential(eq(realm), eq(user), any());
  }

  @Test
  @DisplayName("doesn't write a password LDAP no longer accepts")
  void skipsUnverifiedPassword() {
    stored(SSHA);
    when(provider.isValid(eq(realm), eq(user), any())).thenReturn(false);
    authenticator.authenticate(context);
    assertNotRewritten();
  }

  @Test
  @DisplayName("does nothing when the stored password can't be read")
  void skipsUnreadablePassword() {
    stored();
    authenticator.authenticate(context);
    assertNotRewritten();
    verify(provider, never()).isValid(any(), any(), any());
  }

  @Test
  @DisplayName("does nothing when no password was submitted in this request")
  void skipsWithoutPassword() {
    form.remove("password");
    stored(SSHA);
    authenticator.authenticate(context);
    assertNotRewritten();
    verifyNoInteractions(ldap);
  }

  @Test
  @DisplayName("does nothing for users that aren't federated")
  void skipsLocalUsers() {
    when(user.getFederationLink()).thenReturn(null);
    authenticator.authenticate(context);
    assertNotRewritten();
    verifyNoInteractions(ldap);
  }

  @Test
  @DisplayName("does nothing for users from a provider that isn't LDAP")
  void skipsOtherProviders() {
    when(ldap.providerFor(session, realm, user)).thenReturn(null);
    authenticator.authenticate(context);
    assertNotRewritten();
  }

  @Test
  @DisplayName("does nothing unless the LDAP provider is writable")
  void skipsReadOnlyProvider() {
    stored(SSHA);
    for (EditMode mode : List.of(EditMode.READ_ONLY, EditMode.UNSYNCED)) {
      when(provider.getEditMode()).thenReturn(mode);
      authenticator.authenticate(context);
    }
    verify(provider, never()).updateCredential(any(), any(), any());
    verify(context, times(2)).success();
  }

  @Test
  @DisplayName("lets the login continue when LDAP fails")
  void continuesOnFailure() {
    stored(SSHA);
    when(provider.updateCredential(eq(realm), eq(user), any()))
        .thenThrow(new RuntimeException("LDAP is down"));
    authenticator.authenticate(context);
    verify(context).success();
    verify(context, never()).failure(any());
  }

  @Test
  @DisplayName("uses the configured scheme prefix, including hash parameters")
  void configuredScheme() {
    AuthenticatorConfigModel config = new AuthenticatorConfigModel();
    config.setConfig(
        Map.of(LdapPasswordRehashAuthenticator.SCHEME_CONFIG, "{ARGON2}$argon2i$v=19$m=262144,"));
    when(context.getAuthenticatorConfig()).thenReturn(config);
    stored(ARGON2);

    authenticator.authenticate(context);

    verify(provider).updateCredential(eq(realm), eq(user), any());
  }

  @Test
  @DisplayName("action just continues, since the step never shows a form")
  void action() {
    authenticator.action(context);
    verify(context).success();
    verifyNoInteractions(ldap);
  }

  @Nested
  @DisplayName("Helpers")
  class Helpers {

    @Test
    @DisplayName("falls back to {ARGON2} when no scheme is configured")
    void defaultScheme() {
      assertEquals("{ARGON2}", LdapPasswordRehashAuthenticator.scheme(null));
      assertEquals("{ARGON2}", LdapPasswordRehashAuthenticator.scheme(new AuthenticatorConfigModel()));
      AuthenticatorConfigModel blank = new AuthenticatorConfigModel();
      blank.setConfig(Map.of(LdapPasswordRehashAuthenticator.SCHEME_CONFIG, "  "));
      assertEquals("{ARGON2}", LdapPasswordRehashAuthenticator.scheme(blank));
    }

    @Test
    @DisplayName("logs only the scheme of a stored password, never the hash")
    void schemeOf() {
      assertEquals("{SSHA}", LdapPasswordRehashAuthenticator.schemeOf(SSHA));
      assertEquals("{ARGON2}", LdapPasswordRehashAuthenticator.schemeOf(ARGON2));
      assertEquals("cleartext", LdapPasswordRehashAuthenticator.schemeOf("s3cret-password"));
      assertEquals("cleartext", LdapPasswordRehashAuthenticator.schemeOf("{unterminated"));
    }

    @Test
    @DisplayName("treats no stored values as not current")
    void emptyIsNotCurrent() {
      assertFalse(LdapPasswordRehashAuthenticator.isCurrent(List.of(), "{ARGON2}"));
    }

    @Test
    @DisplayName("decodes base64 userPassword values and keeps stored-password strings as they are")
    void decode() {
      String encoded = Base64.getEncoder().encodeToString(SSHA.getBytes(StandardCharsets.UTF_8));
      assertEquals(SSHA, KeycloakLdapPasswords.decode(encoded));
      assertEquals(SSHA, KeycloakLdapPasswords.decode(SSHA));
      assertEquals("not base64!", KeycloakLdapPasswords.decode("not base64!"));
    }
  }

  @Nested
  @DisplayName("Factory")
  class FactoryTests {

    private final LdapPasswordRehashAuthenticatorFactory factory =
        new LdapPasswordRehashAuthenticatorFactory();

    @Test
    @DisplayName("is a configurable, required-or-disabled step that can't set users up")
    void identity() {
      assertEquals("ldap-password-rehash", factory.getId());
      assertTrue(factory.isConfigurable());
      assertFalse(factory.isUserSetupAllowed());
      assertArrayEquals(
          new Requirement[] {Requirement.REQUIRED, Requirement.DISABLED},
          factory.getRequirementChoices());
      assertEquals(
          LdapPasswordRehashAuthenticator.SCHEME_CONFIG,
          factory.getConfigProperties().get(0).getName());
      assertSame(factory.create(session), factory.create(session));
    }
  }
}
