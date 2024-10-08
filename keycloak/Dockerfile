# Stage 1: Build the Extension
FROM maven:3.8.4 AS extension-builder

WORKDIR /app

COPY keycloak-extension/pom.xml .

COPY keycloak-extension/src ./src

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

# Enable health support
ENV KC_HEALTH_ENABLED=true

# Configure the database vendor
ENV KC_DB=postgres

# Copy the extension and the theme into the Keycloak distribution
COPY --from=extension-builder /app/target/*.jar /opt/keycloak/providers
COPY --from=theme-builder /theme/out/*.jar /opt/keycloak/providers

WORKDIR /opt/keycloak

# Generate a self-signed certificate for local development
RUN keytool -genkeypair -storepass password -storetype PKCS12 -keyalg RSA -keysize 2048 -dname "CN=server" -alias server -ext "SAN:c=DNS:localhost,IP:127.0.0.1" -keystore conf/server.keystore

RUN /opt/keycloak/bin/kc.sh build

FROM registry.access.redhat.com/ubi9 AS ubi-micro-build
RUN mkdir -p /mnt/rootfs
RUN dnf install --installroot /mnt/rootfs curl --releasever 9 --setopt install_weak_deps=false --nodocs -y && \
    dnf --installroot /mnt/rootfs clean all && \
    rpm --root /mnt/rootfs -e --nodeps setup

FROM quay.io/keycloak/keycloak:22.0
COPY --from=builder /opt/keycloak/ /opt/keycloak/

COPY --from=ubi-micro-build /mnt/rootfs /

USER 1001

CMD ["start", "--optimized", "--import-realm", "--http-enabled true", "--spi-theme-welcome-theme=openmrs"]
