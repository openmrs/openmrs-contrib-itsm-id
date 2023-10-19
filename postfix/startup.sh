#!/bin/sh

echo "Adjusting postfix configuration"
envsubst '$POSTFIX_HOSTNAME,$SMTP_SERVER,$SMTP_PORT' < /etc/postfix/main.cf > /etc/postfix/main.cf
envsubst '$LDAP_BIND_PASSWORD,$LDAP_HOST,$LDAP_PORT' < /etc/postfix/ldap-aliases.cf > /etc/postfix/ldap-aliases.cf

echo "Adding SASL authentication configuration"
echo "[${SMTP_SERVER}]:${SMTP_PORT} ${SMTP_USERNAME}:${SMTP_PASSWORD}" > /etc/postfix/sasl_passwd
postmap /etc/postfix/sasl_passwd

chmod 600 /etc/postfix/sasl_passwd /etc/postfix/sasl_passwd.db

postfix start-fg
