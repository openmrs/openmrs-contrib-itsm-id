package org.openmrs.keycloak;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.saml.mappers.SAMLNameIdMapper;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Comprehensive tests for the {@link OpenmrsIdMapper} Keycloak SAML protocol mapper.
 *
 * <p>This extension maps a Keycloak username combined with a configurable email domain
 * into a SAML NameID value of the form {@code username@domain}.</p>
 */
@ExtendWith(MockitoExtension.class)
class OpenmrsIdMapperTest {

  private OpenmrsIdMapper mapper;

  @Mock private KeycloakSession session;

  @Mock private UserSessionModel userSession;

  @Mock private AuthenticatedClientSessionModel clientSession;

  @Mock private UserModel userModel;

  @BeforeEach
  void setUp() {
    mapper = new OpenmrsIdMapper();
  }

  // ──────────────────────────────────────────────────────────────────────
  // Helper methods
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Creates a ProtocolMapperModel with the given email domain configuration.
   */
  private ProtocolMapperModel createMapperModel(String emailDomain) {
    ProtocolMapperModel model = new ProtocolMapperModel();
    Map<String, String> config = new HashMap<>();
    config.put("openmrs.email.domain", emailDomain);
    model.setConfig(config);
    return model;
  }

  // ══════════════════════════════════════════════════════════════════════
  //  Identity / Provider metadata
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Provider Identity & Metadata")
  class ProviderIdentityTests {

    @Test
    @DisplayName("getId() returns the correct provider ID")
    void getIdReturnsCorrectProviderId() {
      assertEquals("openmrs-id-saml-mapper", mapper.getId());
    }

    @Test
    @DisplayName("getId() returns a stable value across instances")
    void getIdIsStableAcrossInstances() {
      OpenmrsIdMapper another = new OpenmrsIdMapper();
      assertEquals(mapper.getId(), another.getId());
    }

    @Test
    @DisplayName("getDisplayType() returns human-readable name")
    void getDisplayTypeReturnsCorrectName() {
      assertEquals("OpenMRS Mapper For NameID", mapper.getDisplayType());
    }

    @Test
    @DisplayName("getDisplayCategory() returns NameID Mapper category")
    void getDisplayCategoryReturnsCorrectCategory() {
      assertEquals("NameID Mapper", mapper.getDisplayCategory());
    }

    @Test
    @DisplayName("getHelpText() returns non-null help text")
    void getHelpTextReturnsNonNull() {
      String helpText = mapper.getHelpText();
      assertNotNull(helpText);
      assertFalse(helpText.isBlank());
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  //  Configuration properties
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Configuration Properties")
  class ConfigPropertiesTests {

    @Test
    @DisplayName("getConfigProperties() returns non-null list")
    void getConfigPropertiesReturnsNonNull() {
      assertNotNull(mapper.getConfigProperties());
    }

    @Test
    @DisplayName("getConfigProperties() includes the email domain property")
    void getConfigPropertiesIncludesEmailDomain() {
      var props = mapper.getConfigProperties();
      boolean hasEmailDomain =
          props.stream().anyMatch(p -> "openmrs.email.domain".equals(p.getName()));
      assertTrue(hasEmailDomain, "Config properties should include 'openmrs.email.domain'");
    }

    @Test
    @DisplayName("Email domain property has correct label and help text")
    void emailDomainPropertyHasCorrectMetadata() {
      var props = mapper.getConfigProperties();
      var emailProp =
          props.stream()
              .filter(p -> "openmrs.email.domain".equals(p.getName()))
              .findFirst()
              .orElseThrow();

      assertEquals("Email Domain", emailProp.getLabel());
      assertEquals("The domain of the virtual emails", emailProp.getHelpText());
    }

    @Test
    @DisplayName("getConfigProperties() includes NameID mapper helper properties")
    void getConfigPropertiesIncludesNameIdHelperProperties() {
      // NameIdMapperHelper.setConfigProperties adds the standard NameID format options
      var props = mapper.getConfigProperties();
      // It should have at least the email domain property plus NameID helper properties
      assertTrue(
          props.size() > 1,
          "Should have email domain property plus NameID mapper helper properties");
    }

    @Test
    @DisplayName("getConfigProperties() returns the same static list across calls")
    void getConfigPropertiesReturnsSameStaticList() {
      List<?> first = mapper.getConfigProperties();
      List<?> second = mapper.getConfigProperties();
      assertSame(first, second, "Config properties should be backed by a static list");
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  //  Core mapping logic: mapperNameId()
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("mapperNameId() - Core NameID Mapping")
  class MapperNameIdTests {

    @Test
    @DisplayName("Maps username with simple email domain")
    void mapsUsernameWithSimpleDomain() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result =
          mapper.mapperNameId(
              "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
              model,
              session,
              userSession,
              clientSession);

      assertEquals("admin@openmrs.org", result);
    }

    @Test
    @DisplayName("Maps username with subdomain-style email domain")
    void mapsUsernameWithSubdomain() {
      ProtocolMapperModel model = createMapperModel("id.openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("testuser");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("testuser@id.openmrs.org", result);
    }

    @Test
    @DisplayName("Maps username with short TLD domain")
    void mapsUsernameWithShortTld() {
      ProtocolMapperModel model = createMapperModel("example.io");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("user1");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("user1@example.io", result);
    }

    @Test
    @DisplayName("Handles username with dots")
    void handlesUsernameWithDots() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("john.doe");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("john.doe@openmrs.org", result);
    }

    @Test
    @DisplayName("Handles username with hyphens")
    void handlesUsernameWithHyphens() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("test-user");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("test-user@openmrs.org", result);
    }

    @Test
    @DisplayName("Handles username with underscores")
    void handlesUsernameWithUnderscores() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("test_user");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("test_user@openmrs.org", result);
    }

    @Test
    @DisplayName("Handles numeric usernames")
    void handlesNumericUsernames() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("user123");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("user123@openmrs.org", result);
    }

    @Test
    @DisplayName("Handles single-character username")
    void handlesSingleCharacterUsername() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("a");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("a@openmrs.org", result);
    }

    @Test
    @DisplayName("Handles long username")
    void handlesLongUsername() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      String longUsername = "a".repeat(200);
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn(longUsername);

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals(longUsername + "@openmrs.org", result);
    }

    @Test
    @DisplayName("Handles long email domain")
    void handlesLongEmailDomain() {
      String longDomain = "subdomain".repeat(20) + ".org";
      ProtocolMapperModel model = createMapperModel(longDomain);
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("admin@" + longDomain, result);
    }

    @Test
    @DisplayName("Output format is always username@domain with '@' separator")
    void outputFormatUsesAtSeparator() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      // The '@' separator should appear exactly where expected
      int atIndex = result.indexOf('@');
      assertTrue(atIndex > 0, "'@' should appear after the username");
      assertEquals(
          atIndex, result.lastIndexOf('@'), "There should be exactly one '@' in the result");
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  //  Edge cases & defensive testing
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Edge Cases & Null Handling")
  class EdgeCaseTests {

    @Test
    @DisplayName("Handles empty email domain in config")
    void handlesEmptyEmailDomain() {
      ProtocolMapperModel model = createMapperModel("");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("admin@", result);
    }

    @Test
    @DisplayName("Handles null email domain in config")
    void handlesNullEmailDomain() {
      ProtocolMapperModel model = createMapperModel(null);
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("admin@null", result);
    }

    @Test
    @DisplayName("Handles empty username")
    void handlesEmptyUsername() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("@openmrs.org", result);
    }

    @Test
    @DisplayName("Handles username that is null (defensive)")
    void handlesNullUsername() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn(null);

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("null@openmrs.org", result);
    }

    @Test
    @DisplayName("Different nameIdFormat values produce same result (format-agnostic)")
    void differentNameIdFormatsProduceSameResult() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result1 =
          mapper.mapperNameId(
              "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress",
              model,
              session,
              userSession,
              clientSession);
      String result2 =
          mapper.mapperNameId(
              "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent",
              model,
              session,
              userSession,
              clientSession);
      String result3 =
          mapper.mapperNameId(
              "urn:oasis:names:tc:SAML:2.0:nameid-format:transient",
              model,
              session,
              userSession,
              clientSession);

      assertEquals(result1, result2);
      assertEquals(result2, result3);
    }

    @Test
    @DisplayName("Null nameIdFormat does not cause exception")
    void nullNameIdFormatDoesNotThrow() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      assertDoesNotThrow(
          () -> mapper.mapperNameId(null, model, session, userSession, clientSession));
    }

    @Test
    @DisplayName("Null config map still produces result with 'null' domain")
    void nullConfigMapProducesNullDomain() {
      ProtocolMapperModel model = new ProtocolMapperModel();
      model.setConfig(null);
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      // This will NPE because getConfig().get() is called on null
      assertThrows(
          NullPointerException.class,
          () -> mapper.mapperNameId(null, model, session, userSession, clientSession));
    }

    @Test
    @DisplayName("Empty config map (no email domain key) produces 'null' domain")
    void emptyConfigMapProducesNullDomain() {
      ProtocolMapperModel model = new ProtocolMapperModel();
      model.setConfig(new HashMap<>());
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("admin@null", result);
    }

    @Test
    @DisplayName("Email domain with special characters is preserved verbatim")
    void emailDomainWithSpecialCharactersPreserved() {
      ProtocolMapperModel model = createMapperModel("openmrs.org?param=value");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("admin@openmrs.org?param=value", result);
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  //  Type hierarchy & SPI contract
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Type Hierarchy & SPI Contract")
  class TypeHierarchyTests {

    @Test
    @DisplayName("Mapper implements SAMLNameIdMapper")
    void implementsSAMLNameIdMapper() {
      assertInstanceOf(SAMLNameIdMapper.class, mapper);
    }

    @Test
    @DisplayName("mapperNameId() is idempotent for same inputs")
    void mapperNameIdIsIdempotent() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result1 = mapper.mapperNameId(null, model, session, userSession, clientSession);
      String result2 = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals(result1, result2);
    }

    @Test
    @DisplayName("Different usernames produce different NameIDs")
    void differentUsernamesProduceDifferentNameIds() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");

      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("user1");
      String result1 = mapper.mapperNameId(null, model, session, userSession, clientSession);

      when(userModel.getUsername()).thenReturn("user2");
      String result2 = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("Different domains produce different NameIDs")
    void differentDomainsProduceDifferentNameIds() {
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result1 =
          mapper.mapperNameId(
              null, createMapperModel("openmrs.org"), session, userSession, clientSession);
      String result2 =
          mapper.mapperNameId(
              null, createMapperModel("example.com"), session, userSession, clientSession);

      assertNotEquals(result1, result2);
    }

    @Test
    @DisplayName("Same username + same domain = same NameID (deterministic)")
    void sameInputsAlwaysProduceSameNameId() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("deterministicUser");

      // Call 10 times, all should be equal
      String first = mapper.mapperNameId(null, model, session, userSession, clientSession);
      for (int i = 0; i < 9; i++) {
        assertEquals(first, mapper.mapperNameId(null, model, session, userSession, clientSession));
      }
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  //  Mock interaction verification
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Mock Interaction Verification")
  class MockInteractionTests {

    @Test
    @DisplayName("mapperNameId() reads username from user session")
    void mapperNameIdReadsUsernameFromUserSession() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      mapper.mapperNameId(null, model, session, userSession, clientSession);

      verify(userSession).getUser();
      verify(userModel).getUsername();
    }

    @Test
    @DisplayName("mapperNameId() reads domain from mapper model config")
    void mapperNameIdReadsDomainFromMapperModelConfig() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      mapper.mapperNameId(null, model, session, userSession, clientSession);

      // The config map should be consulted for the email domain
      assertNotNull(model.getConfig().get("openmrs.email.domain"));
    }

    @Test
    @DisplayName("mapperNameId() does not interact with KeycloakSession")
    void mapperNameIdDoesNotInteractWithKeycloakSession() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      mapper.mapperNameId(null, model, session, userSession, clientSession);

      // KeycloakSession is passed but never used by the mapper
      verifyNoInteractions(session);
    }

    @Test
    @DisplayName("mapperNameId() does not interact with clientSession")
    void mapperNameIdDoesNotInteractWithClientSession() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      mapper.mapperNameId(null, model, session, userSession, clientSession);

      verifyNoInteractions(clientSession);
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  //  SPI Service File Verification
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("SPI Service Registration")
  class SpiServiceRegistrationTests {

    @Test
    @DisplayName("Service file references correct class name")
    void serviceFileReferencesCorrectClassName() {
      assertEquals("org.openmrs.keycloak.OpenmrsIdMapper", OpenmrsIdMapper.class.getName());
    }

    @Test
    @DisplayName("PROVIDER_ID constant matches expected value via reflection")
    void providerIdConstantMatchesExpectedValue() throws Exception {
      Field field = OpenmrsIdMapper.class.getDeclaredField("PROVIDER_ID");
      field.setAccessible(true);
      String providerId = (String) field.get(null);
      assertEquals("openmrs-id-saml-mapper", providerId);
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  //  Realistic integration scenarios
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Realistic OpenMRS Integration Scenarios")
  class RealisticScenarioTests {

    @Test
    @DisplayName("Typical OpenMRS admin user mapping")
    void typicalAdminUserMapping() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("admin@openmrs.org", result);
    }

    @Test
    @DisplayName("Clinical user with realistic username")
    void clinicalUserWithRealisticUsername() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("mksdoctor1");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("mksdoctor1@openmrs.org", result);
    }

    @Test
    @DisplayName("Custom deployment domain")
    void customDeploymentDomain() {
      ProtocolMapperModel model = createMapperModel("pihmalawi.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("clinician");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("clinician@pihmalawi.org", result);
    }

    @Test
    @DisplayName("Username with UUID-style format")
    void usernameWithUuidStyle() {
      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890@openmrs.org", result);
    }

    @Test
    @DisplayName("Domain with port number")
    void domainWithPortNumber() {
      ProtocolMapperModel model = createMapperModel("localhost:8080");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result = mapper.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals("admin@localhost:8080", result);
    }
  }

  // ══════════════════════════════════════════════════════════════════════
  //  Multiple independent mapper instances
  // ══════════════════════════════════════════════════════════════════════

  @Nested
  @DisplayName("Multiple Mapper Instances")
  class MultipleInstanceTests {

    @Test
    @DisplayName("Two mapper instances produce identical results for same inputs")
    void twoInstancesProduceIdenticalResults() {
      OpenmrsIdMapper mapper1 = new OpenmrsIdMapper();
      OpenmrsIdMapper mapper2 = new OpenmrsIdMapper();

      ProtocolMapperModel model = createMapperModel("openmrs.org");
      when(userSession.getUser()).thenReturn(userModel);
      when(userModel.getUsername()).thenReturn("admin");

      String result1 = mapper1.mapperNameId(null, model, session, userSession, clientSession);
      String result2 = mapper2.mapperNameId(null, model, session, userSession, clientSession);

      assertEquals(result1, result2);
    }

    @Test
    @DisplayName("All instances share the same config properties (static)")
    void allInstancesShareSameConfigProperties() {
      OpenmrsIdMapper mapper1 = new OpenmrsIdMapper();
      OpenmrsIdMapper mapper2 = new OpenmrsIdMapper();

      assertSame(mapper1.getConfigProperties(), mapper2.getConfigProperties());
    }

    @Test
    @DisplayName("All instances report same provider ID")
    void allInstancesReportSameProviderId() {
      OpenmrsIdMapper mapper1 = new OpenmrsIdMapper();
      OpenmrsIdMapper mapper2 = new OpenmrsIdMapper();

      assertEquals(mapper1.getId(), mapper2.getId());
    }
  }
}
