#!/bin/sh
# Seeds the directory on first start, then runs slapd in the foreground. The data directory is not a
# volume, so recreating the container resets the directory to the seed.
set -eu

: "${LDAP_BIND_PASSWORD:?LDAP_BIND_PASSWORD must be set}"
# Password for every seeded (fictional) user, so they can be used to test logins
: "${LDAP_SEED_USER_PASSWORD:?LDAP_SEED_USER_PASSWORD must be set}"

data_dir=/var/lib/openldap/openldap-data

if [ ! -f "$data_dir/data.mdb" ]; then
    bind_hash=$(slappasswd -s "$LDAP_BIND_PASSWORD")
    user_hash=$(slappasswd -s "$LDAP_SEED_USER_PASSWORD")
    sed -e "s|__BIND_PASSWORD__|$bind_hash|" -e "s|__USER_PASSWORD__|$user_hash|" /seed/seed.ldif \
        | slapadd -f /etc/openldap/slapd.conf
fi

# Log level 256 logs connections and operations, which makes it easy to see what clients are querying
exec slapd -f /etc/openldap/slapd.conf -h "ldap:///" -d 256
