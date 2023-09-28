FROM alpine:3.14

ARG LDAP_BIND_PASSWORD
ARG SMTP_SERVER
ARG SMTP_PORT
ARG SMTP_USERNAME
ARG SMTP_PASSWORD

RUN apk update && apk add postfix postfix-ldap

COPY config/* /etc/postfix/

RUN echo "bind_pw = $LDAP_BIND_PASSWORD" >> /etc/postfix/ldap-aliases.cf

# Create the postfix/sasl_passwd file with SMTP credentials
RUN echo "[$SMTP_SERVER]:$SMTP_PORT $SMTP_USERNAME:$SMTP_PASSWORD" > /etc/postfix/sasl_passwd
RUN chmod 600 /etc/postfix/sasl_passwd
RUN postmap /etc/postfix/sasl_passwd

EXPOSE 25

CMD ["postfix", "start-fg"]
