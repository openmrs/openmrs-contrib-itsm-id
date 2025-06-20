FROM alpine:3.18

# Install Postfix, Python and required packages
RUN apk update && \
    apk add curl cyrus-sasl envsubst postfix postfix-ldap py3-pip python3 && \
    pip3 install requests && \
    apk cache clean

COPY ./config /etc/postfix
RUN chmod 644 /etc/postfix/clients.cidr /etc/postfix/ldap-aliases.cf.tmpl /etc/postfix/main.cf.tmpl

COPY ./startup.sh /usr/local/bin/
COPY ./ip_updater.py /usr/local/bin/
COPY ./health_check.py /usr/local/bin/
RUN chmod +x /usr/local/bin/startup.sh /usr/local/bin/ip_updater.py /usr/local/bin/health_check.py

EXPOSE 25

ENTRYPOINT ["/usr/local/bin/startup.sh"]
