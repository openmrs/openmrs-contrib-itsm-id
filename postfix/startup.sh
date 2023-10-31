#!/bin/sh

echo "Adjusting postfix configuration"
envsubst '$POSTFIX_MYHOSTNAME,$POSTFIX_MYDOMAIN,$POSTFIX_MYNETWORKS' < /etc/postfix/main.cf.tmpl > /etc/postfix/main.cf
envsubst '$LDAP_USER,$LDAP_BIND_PASSWORD,$LDAP_HOST,$LDAP_PORT' < /etc/postfix/ldap-aliases.cf.tmpl > /etc/postfix/ldap-aliases.cf

newaliases

postfix start-fg
