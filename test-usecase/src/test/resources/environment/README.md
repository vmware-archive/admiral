# Generating SSL keys for the mock server

Generate key pair.

```
openssl genrsa -aes128 -out jetty.key
```

Generate certificate from the keys.

```
openssl req -new -x509 -newkey rsa:2048 -sha256 -key jetty.key -out jetty.crt -days 3650
```

Create a PKCS12 keystore with the private key and certificate.

```
openssl pkcs12 -inkey jetty.key -in jetty.crt -export -out jetty.pkcs12
```