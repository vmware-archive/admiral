FROM alpine:3.3

RUN apk add --update curl

COPY /entrypoint.sh /admiral_logo.txt /

ENTRYPOINT ["/entrypoint.sh"]