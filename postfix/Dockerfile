FROM alpine:3.18

RUN apk update && apk add postfix postfix-ldap envsubst cyrus-sasl && apk cache clean

COPY --chmod=644 ./config /etc/postfix

COPY --chmod=755 ./startup.sh /usr/local/bin/

EXPOSE 25

ENTRYPOINT ["/usr/local/bin/startup.sh"]
