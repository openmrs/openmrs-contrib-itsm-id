# OpenMRS ID

## Setup

The Keycloak instance is pre-filled with the required configuration under the `OpenMRS` realm. However, you need to add the following to the Keycloak instance:

### Connecting to Atlassian

This setup will use SAML to connect to Atlassian. You need to add the following to the Keycloak instance:

1. Follow this guide to create a new SAML client in Atlassian: https://support.atlassian.com/security-and-access-policies/docs/configure-saml-single-sign-on-with-an-identity-provider/
2. Copy the following values from Keycloak to Atlassian:
   - Identity provider Entity ID: `https://id.openmrs.org/realms/openmrs`
   - Identity provider SSO URL: `https://id.openmrs.org/realms/openmrs/protocol/saml/clients/atlassian`
   - SAML X.509 Certificate: This can be found in Realm Settings > Keys > RS256 > Certificate
3. Copy the following values from Atlassian to Keycloak:
   - Service provider assertion consumer service URL: Copy this value to the "Assertion Consumer Service POST Binding URL" field in the advanced setting tab of the Keycloak client.

## Components

This repository contains the following components that are used to run OpenMRS ID:

1. Keycloak extension
2. Keycloak Theme
3. Postfix Docker configuration
4. Atlassian IP updater for postfix

### Keycloak Extension

OpenMRS needs users to be login to Atlassian cloud through OpenMRS ID to make it easier for people to be recognized
across community services and to reduce the burden of supporting accounts across multiple services.

However, Currently Atlassian cloud let use orgs their own SSOs if only the users have an account with a verified domain.
But not every user has an openmrs email, and it’s expensive for OpenMRS to provide all users a mailbox, and it’s hard
for users to use multiple mailboxes.

To facilitate the requirement, we had to introduce a virtual email address based on the username. However, Keycloak does
not support to map such a dynamic field to the NameID of the SAML response. This Keycloak extension helps to add the
said virtual email address to the NameID field.

In order to use the extension you have to follow this:

1. Navigate to Client Scopes > Create Client Scope
   Give it a name, set type to default and set protocol to SAML

   ![Screenshot 2023-09-28 at 18.32.58.png](docs/Screenshot_2023-09-28_at_18.32.58.png)

2. Go to the created Client Scope and Click on the Mappers tab. and then click on Configure a new mapper.

   ![Screenshot 2023-09-28 at 18.37.33.png](docs/Screenshot_2023-09-28_at_18.37.33.png)

   pick “OpenMRS Mapper For NameID”

   ![Screenshot 2023-09-28 at 18.38.19.png](docs/Screenshot_2023-09-28_at_18.38.19.png)

3. Pick NameID format and add the email domain.

   ![Screenshot 2023-09-28 at 18.40.37.png](docs/Screenshot_2023-09-28_at_18.40.37.png)

4. Go to Clients > select your client > Client Scopes > Add Client Scope

pick the created client scope and add as a default.

The extension is building as a jar and it will be copied to the keycloak image when building the keyclaok image. Learn
more about keycloak extensions
here: [Service Provider Interfaces (SPI)](https://www.keycloak.org/docs/latest/server_development/#_providers)

### Keycloak Extension

OpenMRS requires users to log in to Atlassian cloud through OpenMRS ID. This makes it easier for people to be recognized
across community services and reduces the burden of supporting multiple accounts. However, currently Atlassian cloud
only allows organizations to use their own SSOs if users have an account with a verified domain. Since not every user
has an OpenMRS email, it is expensive for OpenMRS to provide all users with a mailbox, and it is difficult for users to
manage multiple mailboxes.

To meet this requirement, a virtual email address was implemented based on the username format: <username>
@id.openmrs.org. However, Keycloak does not support mapping this dynamic field to the NameID in the SAML response. This
Keycloak extension solves this issue by adding the virtual email address to the NameID field.

To use the extension, follow these steps:

1. Navigate to Client Scopes and create a new Client Scope. Give it a name, set the type to default, and set the
   protocol to SAML.

![docs/Screenshot_2023-09-28_at_18.32.58.png](docs/Screenshot_2023-09-28_at_18.32.58.png)

1. Go to the created Client Scope and click on the Mappers tab. Then, click on "Configure a new mapper."

![docs/Screenshot_2023-09-28_at_18.37.33.png](docs/Screenshot_2023-09-28_at_18.37.33.png)

Choose "OpenMRS Mapper For NameID."

![docs/Screenshot_2023-09-28_at_18.38.19.png](docs/Screenshot_2023-09-28_at_18.38.19.png)

1. Select the NameID format and add the email domain.

![docs/Screenshot_2023-09-28_at_18.40.37.png](docs/Screenshot_2023-09-28_at_18.40.37.png)

1. Go to Clients, select your client, go to Client Scopes, and add the created client scope as a default.

The extension is built as a `jar` and will be copied to the Keycloak image during the Keycloak image build. Learn more
about Keycloak extensions [here](a link).

### Keycloak Theme

This theme is to enhace the look and feel of the login and registration process.

This is a custom theme for OpenMRS ID based on the [Keywind](https://github.com/lukin/keywind) theme for Keycloak. Learn
more about Keycloak themes here: [Keycloak Themes](https://www.keycloak.org/docs/latest/server_development/#_themes).
![Preview](docs/preview.jpeg)

### Postfix

The user's email is saved by Atlassian as the virtual email address mentioned above. As a result, all notifications and
other emails are sent to the virtual email address. A mechanism was needed to forward these emails to the user's actual
email address. Postfix was chosen to fulfill this requirement. Postfix is a free and open-source mail transfer agent (
MTA) that routes and delivers email messages.

Whenever Atlassian receives an email, postfix queries for a user in the LDAP where the username is the username part of
the received email address. Then, it retrieves the email address of the returned user and forwards the received email.

`IP udpater` script will check Atlassian, and update postfix configuration based on Atlassian's new IPs. In datadog, you can see metrics `atlassian.ip_updater.ip_count` and `atlassian.ip_updater.postfix_reload_success` related to it. HTTP endpoints `/postfix` and `/status` give a bit of insight about it as well

![Untitled](docs/Untitled.png)



#### **Add a custom announcement**

To add a custom announcement to the login page, you can add a value to the `HTML Display name` field of the Realm settings. (`Realm Settings > General > HTML Display name`)

![Preview](docs/announcement.jpeg)
