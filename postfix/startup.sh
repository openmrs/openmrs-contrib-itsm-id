#!/bin/sh

echo "bind_pw = $LDAP_BIND_PASSWORD
server_host = $LDAP_HOST
server_port = $LDAP_PORT
" >> /etc/postfix/ldap-aliases.cf
echo "[$SMTP_SERVER]:$SMTP_PORT $SMTP_USERNAME:$SMTP_PASSWORD" > /etc/postfix/sasl_passwd
chmod 600 /etc/postfix/sasl_passwd
postmap /etc/postfix/sasl_passwd

postfix start-fg
