FROM alpine:3.14

ARG LDAP_BIND_PASSWORD
ARG SMTP_SERVER
ARG SMTP_PORT
ARG SMTP_USERNAME
ARG SMTP_PASSWORD

RUN apk update && apk add postfix postfix-ldap

COPY config/* /etc/postfix/

COPY ./startup.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/startup.sh
EXPOSE 25

ENTRYPOINT ["/usr/local/bin/startup.sh"]
