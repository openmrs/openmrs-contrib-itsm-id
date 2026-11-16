# Local-development stand-in for ldap.openmrs.org, seeded with fictional users and the groups that are
# provisioned to Atlassian. Not for staging or production.
FROM alpine:3.24

RUN apk add --no-cache openldap openldap-back-mdb openldap-clients \
    && mkdir -p /run/openldap /var/lib/openldap/openldap-data

COPY slapd.conf /etc/openldap/slapd.conf
COPY seed.ldif /seed/seed.ldif
COPY entrypoint.sh /entrypoint.sh

EXPOSE 389

ENTRYPOINT ["/bin/sh", "/entrypoint.sh"]
