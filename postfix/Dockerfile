FROM alpine:3.18

RUN apk update && apk add postfix postfix-ldap envsubst cyrus-sasl && apk cache clean

COPY ./config /etc/postfix

COPY ./startup.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/startup.sh
EXPOSE 25

ENTRYPOINT ["/usr/local/bin/startup.sh"]
