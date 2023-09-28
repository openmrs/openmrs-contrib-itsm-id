# Stage 1: Build the Extension
FROM maven:3.8.4 AS extension-builder

WORKDIR /app

COPY keycloak-extension/pom.xml .

COPY keycloak-extension/src ./src

# Build the Maven project and create a JAR file
RUN mvn clean install -DskipTests

# Stage 2: Build the TypeScript application
FROM node:14 AS theme-builder

WORKDIR /theme

COPY theme/package.json theme/package-lock.json ./
RUN npm install

COPY theme ./

RUN npm run build
RUN npm run build:jar

# Stage 3: Build the Keycloak including the theme and the extension
FROM quay.io/keycloak/keycloak:22.0 as builder

# Enable health and metrics support
ENV KC_HEALTH_ENABLED=true
ENV KC_METRICS_ENABLED=true

# Configure a database vendor
ENV KC_DB=postgres

COPY --from=extension-builder /app/target/*.jar /opt/keycloak/providers
COPY --from=theme-builder /theme/out/*.jar /opt/keycloak/providers

WORKDIR /opt/keycloak

# for demonstration purposes only, please make sure to use proper certificates in production instead
RUN keytool -genkeypair -storepass password -storetype PKCS12 -keyalg RSA -keysize 2048 -dname "CN=server" -alias server -ext "SAN:c=DNS:localhost,IP:127.0.0.1" -keystore conf/server.keystore
RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:22.0
COPY --from=builder /opt/keycloak/ /opt/keycloak/

USER 1001

CMD ["start", "--optimized"]
