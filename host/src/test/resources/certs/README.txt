This directory contains sample certificates and keystores that can be used to configure a (Admiral) host with one-way or two-way authentication.

One-way authentication with server side certificate:
Typical SSL scenario. In this mode Admiral is configured with a certificate and potential clients have to know about that certificate (i.e. accept it) in order to establish the communication.

Two-way authentication with client side certificate:
Like in the previous scenario but moreover Admiral requires to potential clients to identify themselves using their own certificate, certificate that has to be trusted by Admiral and its keystore.


Files
=====
- server.p12
- server_public.crt
- server_private.pem
- client.p12
- client_public.crt
- client_private.pem
- trusted_certificates.jks


How to generate those files
===========================

Using:
Java(TM) SE Runtime Environment (build 1.8.0_65-b17)
OpenSSL 0.9.8zg 14 July 2015

1. Generate the server key pair
keytool -genkey -alias cmp-host -keystore server.p12 -storepass changeit -keyalg RSA -validity 3650 -keysize 2048 -storetype pkcs12 -dname 'CN=cmp-host.vmware.com,OU=CMP,O=VMware,L=Palo Alto,ST=CA,C=US'

2. Extract the public key
keytool -exportcert -alias cmp-host -keystore server.p12 -storepass changeit -rfc -file server_public.crt

3. Extract the private key
openssl pkcs12 -in server.p12 -out server_private.tmp -passin pass:changeit -nodes -nocerts 
openssl pkcs8 -topk8 -inform PEM -outform PEM -in server_private.tmp -out server_private.pem -nocrypt

4. Generate the client key pair
keytool -genkey -alias cmp-agent -keystore client.p12 -storepass changeit -keyalg RSA -validity 3650 -keysize 2048 -storetype pkcs12 -dname 'CN=cmp-agent.vmware.com,OU=CMP,O=VMware,L=Palo Alto,ST=CA,C=US'

5. Extract the public key
keytool -exportcert -alias cmp-agent -keystore client.p12 -storepass changeit -rfc -file client_public.crt

6. Extract the private key
openssl pkcs12 -in client.p12 -out client_private.tmp -passin pass:changeit -nodes -nocerts 
openssl pkcs8 -topk8 -inform PEM -outform PEM -in client_private.tmp -out client_private.pem -nocrypt

7. Add the client public key to the trusted certificates store
keytool -import -keystore trusted_certificates.jks -storepass changeit -file client_public.crt -alias client

8. Add the server public key to the trusted certificates store (because the same JKS used by the Container Service tests)
keytool -import -keystore trusted_certificates.jks -storepass changeit -file server_public.crt -alias server

9. Convert the client key pair store from PKCS#12 to JKS (to be used by the Container Service tests)
keytool -importkeystore -srckeystore client.p12 -srcstoretype pkcs12 -srcstorepass changeit -srcalias cmp-agent -destkeystore client.jks -deststoretype jks -deststorepass changeit -destalias cmp-agent

Host parameters
===============

One-way authentication (on the given securePort!):
--securePort=8283 --keyFile=/path_to/server_private.pem --certificateFile=/path_to/server_public.crt

Two-way authentication (on the given securePort!):
--securePort=8283 --keyFile=/path_to/server_private.pem --certificateFile=/path_to/server_public.crt --sslClientAuthMode=WANT -Djavax.net.ssl.trustStore=/path_to/trusted_certificates.jks -Djavax.net.ssl.trustStorePassword=changeit

Note: With two-way authentication, setting --sslClientAuthMode=NEED forces the client to provide a trusted certificate or the server won't accept any connection.