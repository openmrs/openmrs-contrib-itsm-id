package org.openmrs.keycloak;

import org.keycloak.models.AuthenticatedClientSessionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.saml.mappers.*;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.ArrayList;
import java.util.List;

public class OpenmrsIdMapper extends AbstractSAMLProtocolMapper implements SAMLNameIdMapper {

    private static final String PROVIDER_ID = "openmrs-id-saml-mapper";
    private static final String EMAIL_DOMAIN_PROPERTY_NAME = "openmrs.email.domain";

    private static final List<ProviderConfigProperty> configProperties = new ArrayList<ProviderConfigProperty>();

    static {
        NameIdMapperHelper.setConfigProperties(configProperties);
        ProviderConfigProperty property;
        property = new ProviderConfigProperty();
        property.setName(EMAIL_DOMAIN_PROPERTY_NAME);
        property.setLabel("Email Domain");
        property.setHelpText("The domain of the virtual emails");
        configProperties.add(property);
    }

    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }
    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "OpenMRS Mapper For NameID";
    }

    @Override
    public String getDisplayCategory() {
        return "NameID Mapper";
    }

    @Override
    public String getHelpText() {
        return "Map user attribute to SAML NameID value.";
    }

    @Override
    public String mapperNameId(String nameIdFormat, ProtocolMapperModel mappingModel, KeycloakSession session,
                               UserSessionModel userSession, AuthenticatedClientSessionModel clientSession) {
        return userSession.getUser().getUsername() + "@" + mappingModel.getConfig().get(EMAIL_DOMAIN_PROPERTY_NAME);
    }

}
