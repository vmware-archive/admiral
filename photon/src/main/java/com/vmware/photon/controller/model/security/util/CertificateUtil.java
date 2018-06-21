/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.photon.controller.model.security.util;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.apache.commons.net.util.Base64;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import com.vmware.photon.controller.model.security.service.SslTrustCertificateService;
import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.ssl.ServerX509TrustManager;
import com.vmware.photon.controller.model.security.ssl.X509TrustManagerResolver;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceRequestSender;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Utility class that provides useful functions for certificate's data retrieval and manipulation.
 */
public class CertificateUtil {
    private static final Logger logger = Logger.getLogger(CertificateUtil.class.getName());

    public static final String COMMON_NAME_KEY = "commonName";
    public static final String ISSUER_NAME_KEY = "issuerName";
    public static final String SERIAL_KEY = "serial";
    public static final String FINGERPRINT_KEY = "fingerprint";
    public static final String VALID_SINCE_KEY = "validSince";
    public static final String VALID_TO_KEY = "validTo";

    // Server Name Indication (SNI) Extension
    public static final boolean SSL_CONNECT_USE_SNI = Boolean.parseBoolean(System.getProperty(
            "ssl.resolver.use_sni", "true"));

    private static final int DEFAULT_SECURE_CONNECTION_PORT = 443;
    public static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = Long.getLong(
            "ssl.resolver.import.timeout.millis", TimeUnit.SECONDS.toMillis(30));

    private static final char[] EMPTY = new char[0];
    private static final String SIGNING_ALGORITHM = "SHA1withRSA";
    private static final long CERTIFICATE_TOLERANCE_OFFSET = TimeUnit.DAYS.toMillis(1L);
    private static final Provider PROVIDER = new BouncyCastleProvider();
    private static final long DEFAULT_VALIDITY = TimeUnit.DAYS.toMillis(Long.getLong(
            "default.selfSignedCertificate.validity.day", 365 * 2));

    private static final String HEX = "0123456789ABCDEF";
    public static final String REQUEST_HEADER_PROXY_AUTHORIZATION = "Proxy-Authorization";

    /**
     * Utility method to decode a certificate chain PEM encoded string value to an array of
     * X509Certificate certificate instances.
     *
     * @param certChainPEM
     *            - a certificate chain (one or more certificates) PEM encoded string value.
     * @return - decoded array of X509Certificate certificate instances.
     * @throws RuntimeException
     *             if a certificate can't be decoded to X509Certificate type certificate.
     */
    public static X509Certificate[] createCertificateChain(String certChainPEM) {
        AssertUtil.assertNotNull(certChainPEM, "certChainPEM should not be null.");

        List<X509Certificate> chain = new ArrayList<>();
        try (PEMParser parser = new PEMParser(new StringReader(certChainPEM))) {

            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            X509CertificateHolder certificateHolder;
            while ((certificateHolder = (X509CertificateHolder) parser.readObject()) != null) {
                chain.add(converter.getCertificate(certificateHolder));
            }
        } catch (IOException | CertificateException e) {
            throw new RuntimeException("Failed to create certificate: " + certChainPEM, e);
        }

        if (chain.isEmpty()) {
            throw new RuntimeException("A valid certificate was not found: " + certChainPEM);
        }

        return chain.toArray(new X509Certificate[chain.size()]);
    }

    /**
     * Utility method to decode a certificate PEM encoded string value to X509Certificate type
     * certificate instance.
     * <p>
     * The difference between this method and {@link #createCertificateChain(String)} is that this
     * expects exactly one PEM encoded certificate. Use when the PEM represents a distinguished
     * private or public key. For general purpose, where the expectation is to have one or more PEM
     * encoded certificates, certificate chain, use {@link #createCertificateChain(String)}.
     *
     * @param certPEM
     *            - a certificate PEM encoded string value.
     * @return - decoded X509Certificate type certificate instance.
     * @throws RuntimeException
     *             if the certificate can't be decoded to X509Certificate type certificate.
     */
    public static X509Certificate createCertificate(String certPEM) {
        X509Certificate[] createCertificateChain = createCertificateChain(certPEM);
        AssertUtil.assertTrue(createCertificateChain.length == 1,
                "Expected exactly one certificate in PEM: " + certPEM);

        return createCertificateChain[0];
    }

    /**
     * Utility method to decode a PEM encoded private key string to a PrivateKey instance
     *
     * @param key
     *            - a PEM encoded private key string
     * @return - decoded PrivateKey instance
     */
    public static KeyPair createKeyPair(String key) {
        AssertUtil.assertNotNull(key, "key");
        String decryptedKey = EncryptionUtils.decrypt(key);
        try (PEMParser parser = new PEMParser(new StringReader(decryptedKey))) {

            JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
            PEMKeyPair keyPair = (PEMKeyPair) parser.readObject();
            if (keyPair == null) {
                throw new RuntimeException("A valid key pair was not found");
            }
            return converter.getKeyPair(keyPair);

        } catch (IOException e) {
            throw new RuntimeException("Failed to create key pair", e);
        }
    }

    /**
     * Create an empty key store
     */
    public static KeyStore createEmptyKeyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null); // initialize empty keystore
            return keyStore;

        } catch (KeyStoreException | NoSuchAlgorithmException
                | CertificateException | IOException e) {
            throw new RuntimeException("Failed to create empty keystore", e);
        }
    }

    /**
     * Set a certificate entry in a trust store
     */
    public static void setCertificateEntry(KeyStore trustStore, String alias, String trustedCert) {

        X509Certificate[] certificates = CertificateUtil.createCertificateChain(trustedCert);

        for (X509Certificate certificate : certificates) {
            String certAlias = alias + "_"
                    + CertificateUtil.getCommonName(certificate.getSubjectDN());
            try {
                trustStore.setCertificateEntry(certAlias, certificate);
            } catch (KeyStoreException e) {
                throw new RuntimeException("Failed to set certificate entry", e);
            }
        }
    }

    /**
     * Set a key entry in a key store
     */
    private static void setKeyEntry(KeyStore keyStore, String alias,
            String clientKey, String clientCert) {

        X509Certificate[] clientCertificates = CertificateUtil.createCertificateChain(clientCert);
        PrivateKey key = CertificateUtil.createKeyPair(clientKey).getPrivate();

        try {
            keyStore.setKeyEntry(alias, key, EMPTY, clientCertificates);

        } catch (KeyStoreException e) {
            throw new RuntimeException("Failed to set key entry", e);
        }
    }

    /**
     * Create a TrustManager for the given PEM encoded certificate
     */
    public static TrustManager[] getTrustManagers(String alias,
            String trustedCert) {
        if (trustedCert == null) {
            return null;
        }

        KeyStore trustStore = createEmptyKeyStore();

        setCertificateEntry(trustStore, alias, trustedCert);

        return getTrustManagers(trustStore);
    }

    /**
     * Create a TrustManager using the given trust store
     */
    public static TrustManager[] getTrustManagers(KeyStore trustStore) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());

            tmf.init(trustStore);
            return tmf.getTrustManagers();

        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(
                    "Failed to create a TrustManager from a trust store", e);
        }
    }

    /**
     * Get a KeyManager configured with the given private key and certificate
     */
    public static KeyManager[] getKeyManagers(String alias, String clientKey,
            String clientCert) {

        if (clientCert == null) {
            return null;
        }

        KeyStore keyStore = createEmptyKeyStore();
        setKeyEntry(keyStore, alias, clientKey, clientCert);

        return getKeyManagers(keyStore);
    }

    /**
     * Get a KeyManager based on the given key store
     */
    public static KeyManager[] getKeyManagers(KeyStore keyStore) {
        try {
            KeyManagerFactory kmf = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());

            kmf.init(keyStore, EMPTY);
            return kmf.getKeyManagers();

        } catch (KeyStoreException | NoSuchAlgorithmException
                | UnrecoverableKeyException e) {
            throw new RuntimeException(
                    "Failed to create a KeyManager from a key store", e);
        }
    }

    /**
     * Create a SSLContext for a given trusted certificate and client key and certificate
     */
    public static SSLContext createSSLContext(String trustedCert,
            String clientKey, String clientCert) {

        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(
                    CertificateUtil.getKeyManagers("client", clientKey, clientCert),
                    CertificateUtil.getTrustManagers("server", trustedCert), null);

            return ctx;

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }

    /**
     * Create an SSLContext for the given TrustManager and KeyManager
     */
    public static SSLContext createSSLContext(TrustManager trustManager,
            KeyManager keyManager) {

        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(new KeyManager[] { keyManager },
                    new TrustManager[] { trustManager }, null);

            return ctx;

        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create SSLContext", e);
        }
    }

    /**
     * Extracts the Certificate Principal Common Name (CN).
     */
    public static String getCommonName(Principal certPrincipal) {
        if (certPrincipal == null || certPrincipal.getName() == null
                || certPrincipal.getName().isEmpty()) {
            return null;
        }
        String attr = getAttributeFromDN(certPrincipal.getName(), "CN");
        if (attr == null) {
            Utils.logWarning("DN %s doesn't contain attribute 'CN'", certPrincipal.getName());
            attr = certPrincipal.getName();
        }
        return attr;

    }

    /**
     * Extracts from DN a given attribute.
     *
     * @param dn
     *            The entire DN
     * @param attribute
     *            The attribute to extract
     * @return the attribute value or null if not found an attribute with the given dn.
     */
    public static String getAttributeFromDN(String dn, String attribute) {
        try {
            LdapName subjectDn = new LdapName(dn);

            for (Rdn rdn : subjectDn.getRdns()) {
                if (rdn.getType().equals(attribute)) {
                    return rdn.getValue().toString();
                }
            }
        } catch (InvalidNameException e) {
            throw new IllegalArgumentException(e);
        }

        return null;
    }

    /**
     * Serialize Certificate in PEM format
     */
    public static String toPEMformat(X509Certificate certificate) {
        StringWriter sw = new StringWriter();
        JcaPEMWriter pemWriter = new JcaPEMWriter(sw);
        try {
            pemWriter.writeObject(certificate);
            pemWriter.close();

            return sw.toString();

        } catch (IOException x) {
            throw new RuntimeException("Failed to serialize certificate", x);
        }
    }

    /**
     * Serialize Certificate chain in PEM format
     */
    public static String toPEMformat(X509Certificate[] certificateChain) {
        StringWriter sw = new StringWriter();
        for (X509Certificate certificate : certificateChain) {
            sw.append(toPEMformat(certificate));
        }
        return sw.toString();
    }

    public static X509TrustManagerResolver resolveCertificate(URI uri,
            String httpProxyHost, int httpProxyPort,
            String httpProxyUser, String httpProxyPass,
            long timeout) {

        InetSocketAddress proxyInet = new InetSocketAddress(httpProxyHost, httpProxyPort);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, proxyInet);
        return resolveCertificate(uri, proxy, httpProxyUser, httpProxyPass, timeout);
    }

    public static X509TrustManagerResolver resolveCertificate(URI uri,
            Proxy proxy, String proxyUsername, String proxyPassword, long timeoutMillis) {
        logger.entering(logger.getName(), "resolveCertificate");

        X509TrustManagerResolver trustManagerResolver = new X509TrustManagerResolver();

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { trustManagerResolver }, null);
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            logger.throwing(logger.getName(), "connect", e);
            throw new LocalizableValidationException(e, "Failed to initialize SSL context.",
                    "security.certificate.context.init.error");
        }

        String hostAddress = uri.getHost();
        int port = uri.getPort() == -1 ? DEFAULT_SECURE_CONNECTION_PORT : uri.getPort();
        String uriScheme = uri.getScheme();
        String host = String.format("%s://%s:%d", uriScheme, hostAddress, port);

        try {
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            if (proxy != null && proxy.type() == Type.HTTP && proxyUsername != null
                    && UriUtils.HTTPS_SCHEME.equalsIgnoreCase(uriScheme)) {
                URL url = uri.toURL();
                handleCertForHttpsThroughHttpProxyWithAuth(url,
                        proxy, proxyUsername, proxyPassword,
                        timeoutMillis, sslSocketFactory);
            } else {
                SSLSocket sslSocket;
                if (proxy != null) {
                    if (proxyUsername != null) {
                        throw new LocalizableValidationException("Proxy authentication supported "
                                + "for HTTPS URI through HTTP Proxy only."
                                + " URI: " + uri.toASCIIString()
                                + ", Proxy: " + proxy.toString(),
                                "security.certificate.proxy.authentication.not.supported.error",
                                uri.toASCIIString(), proxy.toString());
                    }
                    Socket tunnel = new Socket(proxy);
                    tunnel.connect(new InetSocketAddress(hostAddress, port), (int) timeoutMillis);
                    sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                            tunnel,
                            hostAddress,
                            port,
                            true);
                } else {
                    sslSocket = (SSLSocket) sslSocketFactory.createSocket();

                    if (SSL_CONNECT_USE_SNI) {
                        SNIHostName serverName = new SNIHostName(hostAddress);
                        List<SNIServerName> serverNames = new ArrayList<>(1);
                        serverNames.add(serverName);

                        SSLParameters params = sslSocket.getSSLParameters();
                        params.setServerNames(serverNames);
                        sslSocket.setSSLParameters(params);
                    }

                    sslSocket
                            .connect(new InetSocketAddress(hostAddress, port), (int) timeoutMillis);
                }
                SSLSession session = sslSocket.getSession();
                session.invalidate();
            }
        } catch (IOException e) {
            try {
                if (trustManagerResolver.isCertsTrusted()
                        || trustManagerResolver.getCertificateChain().length == 0) {
                    Utils.logWarning(
                            "Exception while resolving certificate for host: [%s]. Error: %s ",
                            host, e.getMessage());
                } else {
                    logger.throwing(logger.getName(), "connect", e);
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            } catch (IllegalStateException ise) {
                throw new LocalizableValidationException(e,
                        String.format("Cannot connect to host: [%s]. Error: %s",
                                host, e.getMessage()),
                        "security.certificate.connection.error", host, e.getMessage());
            }
        }

        if (trustManagerResolver.getCertificateChain().length == 0) {
            LocalizableValidationException e = new LocalizableValidationException(
                    "Check ssl certificate failed for server: " + host,
                    "security.certificate.check.error", host);

            logger.throwing(logger.getName(), "connect", e);
            throw e;
        }
        logger.exiting(logger.getName(), "resolveCertificate");
        return trustManagerResolver;
    }

    private static void handleCertForHttpsThroughHttpProxyWithAuth(URL url, Proxy proxy,
            String proxyUsername, String proxyPassword, long timeout,
            SSLSocketFactory socketFactory) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection(proxy);

        connection.setSSLSocketFactory(socketFactory);
        connection.setConnectTimeout((int) (timeout < 0 ? 0 : timeout));
        if (proxyUsername != null && proxyPassword != null) {
            byte[] token = (proxyUsername + ":" + proxyPassword)
                    .getBytes(StandardCharsets.UTF_8);

            connection.setRequestProperty(REQUEST_HEADER_PROXY_AUTHORIZATION,
                    "Basic " + Base64.encodeBase64StringUnChunked(token));
        }

        connection.connect();
        connection.disconnect();
    }

    public static X509TrustManagerResolver resolveCertificate(URI uri, long timeoutMillis) {
        return resolveCertificate(uri, null, null, null, timeoutMillis);
    }

    public static void storeCertificate(X509Certificate endCertificate, List<String> tenantLinks,
            ServiceHost host, ServiceRequestSender sender, CompletionHandler ch) {
        SslTrustCertificateState certState = new SslTrustCertificateState();
        if (tenantLinks != null) {
            certState.tenantLinks = tenantLinks;
        }

        certState.certificate = CertificateUtil.toPEMformat(endCertificate);
        SslTrustCertificateState.populateCertificateProperties(
                certState,
                endCertificate);

        logger.info(String.format("Register certificate with common name: %s "
                + "and fingerprint: %s in trust store",
                certState.commonName, certState.fingerprint));
        // save untrusted certificate to the trust store
        Operation.createPost(host, SslTrustCertificateService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(certState)
                .setCompletion(ch)
                .sendWith(sender);
        ServerX509TrustManager trustManager = ServerX509TrustManager.getInstance();
        if (trustManager != null) {
            logger.fine("Register Certificate " + certState);
            trustManager.registerCertificate(certState);
        }
    }

    public static void validateCertificateChain(X509Certificate[] certificateChain)
            throws Exception {
        List<X509Certificate> certificates = Arrays.asList(certificateChain);

        for (X509Certificate certificate : certificates) {
            checkIfCertificateExistsMoreThanOneTimeInChain(certificates, certificate);
        }

        Iterator<X509Certificate> it = certificates.iterator();

        X509Certificate current = it.next();
        current.checkValidity();

        while (it.hasNext()) {
            X509Certificate next = it.next();
            next.checkValidity();

            try {
                current.verify(next.getPublicKey());
            } catch (InvalidKeyException | CertificateException | NoSuchAlgorithmException
                    | NoSuchProviderException e) {
                throw new IllegalArgumentException(e);
            } catch (SignatureException e) {
                throw new LocalizableValidationException("Certificate chain is not valid.",
                        "security.certificate.invalid");
            }
            current = next;
        }
    }

    private static void checkIfCertificateExistsMoreThanOneTimeInChain(
            List<X509Certificate> certificates, X509Certificate certificate) {

        int exists = 0;

        for (X509Certificate currentcert : certificates) {
            if (certificate.equals(currentcert) && exists >= 1) {
                String errorMsg = String.format(
                        "Certificate with subject: %s exists in more than one place in chain.",
                        certificate.getSubjectX500Principal().getName());
                throw new LocalizableValidationException(errorMsg,
                        "security.certificate.exists.in.chain",
                        certificate.getSubjectX500Principal().getName());
            } else if (certificate.equals(currentcert)) {
                exists++;
            }
        }
    }

    /**
     * Retrieve the {@link ThumbprintAlgorithm#DEFAULT} thumbprint of a X.509 certificate.
     *
     * @param cert
     *            certificate
     * @return the thumbprint corresponding to the certificate; {@code not-null} value
     * @throws IllegalStateException
     *             if an error occur while getting the encoded form of the certificates
     * @throws IllegalArgumentException
     *             if an error occur while getting the encoded form of the certificates
     */
    public static String computeCertificateThumbprint(X509Certificate cert) {
        return computeCertificateThumbprint(cert, ThumbprintAlgorithm.DEFAULT);
    }

    /**
     * Retrieve thumbprint of a X.509 certificate as specified in {@link ThumbprintAlgorithm}
     * parameter.
     *
     * @param cert
     *            certificate
     * @param thumbprintAlgorithm
     *            the type of {@link ThumbprintAlgorithm}
     * @return the thumbprint corresponding to the certificate; {@code not-null} value
     * @throws IllegalStateException
     *             if an error occur while getting the encoded form of the certificates
     * @throws IllegalArgumentException
     *             if an error occur while getting the encoded form of the certificates
     */
    public static String computeCertificateThumbprint(X509Certificate cert,
            ThumbprintAlgorithm thumbprintAlgorithm) {
        if (cert == null) {
            throw new LocalizableValidationException("certificate must not be null.",
                    "security.certificate.null.error");
        }
        byte[] digest;
        try {
            MessageDigest md = MessageDigest.getInstance(thumbprintAlgorithm.getAlgorithmName());
            digest = md.digest(cert.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } catch (CertificateEncodingException e) {
            throw new IllegalArgumentException(e);
        }

        StringBuilder thumbprint = new StringBuilder();
        for (int i = 0, len = digest.length; i < len; ++i) {
            if (i > 0) {
                thumbprint.append(':');
            }
            byte b = digest[i];
            thumbprint.append(HEX.charAt((b & 0xF0) >> 4));
            thumbprint.append(HEX.charAt(b & 0x0F));
        }
        return thumbprint.toString();
    }

    /**
     * Extracts {@link X509Certificate} properties from the given {@code cert}
     *
     * @param cert
     *            the x509 certificate
     * @return x509 certificate related properties as map
     */
    public static Map<String, String> getCertificateInfoProperties(X509Certificate cert) {
        Map<String, String> certificateInfo = new HashMap<>();
        certificateInfo.put(COMMON_NAME_KEY, CertificateUtil.getCommonName(cert.getSubjectDN()));
        certificateInfo.put(ISSUER_NAME_KEY, CertificateUtil.getCommonName(cert.getIssuerDN()));
        certificateInfo.put(SERIAL_KEY, getSerialNumber(cert));
        certificateInfo.put(FINGERPRINT_KEY, CertificateUtil.computeCertificateThumbprint(cert));
        certificateInfo.put(VALID_SINCE_KEY, Long.toString(cert.getNotBefore().getTime()));
        certificateInfo.put(VALID_TO_KEY, Long.toString(cert.getNotAfter().getTime()));
        return certificateInfo;
    }

    /**
     * Generates a signed certificate and a private key for client auth.
     *
     * @param fqdn
     *            the fqdn name of the subject
     * @param issuerCertificate
     *            the certificate that will be used as a signer. Cannot be null.
     * @param issuerPrivateKey
     *            the private key to sign the certificate. Cannot be null.
     * @return holder object for the issued certificate and the private key.
     */
    public static CertChainKeyPair generateSignedForClient(
            String fqdn, X509Certificate issuerCertificate,
            PrivateKey issuerPrivateKey) {
        try {
            return generateCertificateAndSign(fqdn, issuerCertificate,
                    issuerPrivateKey,
                    getClientExtensions());
        } catch (CertIOException | CertificateException | OperatorCreationException e) {
            throw new RuntimeException(String.format(
                    "Failed to generate client certificate, reason: %s",
                    e.getMessage()), e);
        }
    }

    /**
     * Generates a signed certificate and a private key.
     *
     * @param fqdn
     *            the fqdn name of the subject
     * @param issuerCertificate
     *            the certificate that will be used as a signer. Cannot be null.
     * @param issuerPrivateKey
     *            the private key to sign the certificate. Cannot be null.
     * @return holder object for the issued certificate and the private key.
     */
    public static CertChainKeyPair generateSigned(
            String fqdn, X509Certificate issuerCertificate,
            PrivateKey issuerPrivateKey) {
        try {
            return generateCertificateAndSign(fqdn, issuerCertificate,
                    issuerPrivateKey,
                    getServerExtensions(issuerCertificate));
        } catch (CertificateException | OperatorCreationException | NoSuchAlgorithmException
                | IOException e) {
            throw new RuntimeException(String.format(
                    "Failed to generate server certificate, reason: %s",
                    e.getMessage()), e);
        }
    }

    /**
     * Returns certificate thumbprint with no colon characters and lower-cased for the first
     * certificate in the chain.
     */
    public static String generatePureFingerPrint(X509Certificate[] certificateChain) {
        return generatePureFingerPrint(certificateChain[0]);
    }

    /**
     * Returns certificate thumbprint with no colon characters and lower-cased.
     */
    public static String generatePureFingerPrint(X509Certificate certificate) {
        String thumbprint = computeCertificateThumbprint(certificate);
        return thumbprint.replaceAll(":", "").toLowerCase();
    }

    private static CertChainKeyPair generateCertificateAndSign(String fqdn,
            X509Certificate issuerCertificate,
            PrivateKey issuerPrivateKey, List<ExtensionHolder> extensions)
            throws CertificateException, CertIOException, OperatorCreationException {
        AssertUtil.assertNotNull(issuerCertificate, "issuerCertificate");
        AssertUtil.assertNotNull(issuerPrivateKey, "issuerPrivateKey");

        // private key that we are creating certificate for
        KeyPair pair = KeyUtil.generateRSAKeyPair();

        PublicKey publicKey = pair.getPublic();
        PrivateKey privateKey = convertToSunImpl(pair.getPrivate());

        ContentSigner signer = new JcaContentSignerBuilder(
                SIGNING_ALGORITHM).setProvider(PROVIDER).build(
                        issuerPrivateKey);

        X500Name subjectName = new X500Name("CN=" + fqdn);

        // serial number of certificate
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());

        // valid from
        Date notBefore = generateNotBeforeDate();

        // valid to
        Date notAfter = generateNotAfterDate(notBefore, DEFAULT_VALIDITY);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(issuerCertificate,
                serial, notBefore, notAfter, subjectName,
                publicKey);

        for (ExtensionHolder extension : extensions) {
            certBuilder.addExtension(extension.getOID(), extension.isCritical(),
                    extension.getValue());
        }

        X509CertificateHolder certificateHolder = certBuilder.build(signer);

        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(PROVIDER)
                .getCertificate(certificateHolder);

        List<X509Certificate> certificateChain = new ArrayList<>(2);
        certificateChain.add(certificate);
        certificateChain.add(issuerCertificate);

        return new CertChainKeyPair(certificateChain, certificate, privateKey);
    }

    /**
     * Helper method for converting the bouncycastle implementation of a private key to a sun
     * implementation.
     *
     * @param key
     *            the private key to be converted.
     * @return a sun implementation of the private key.
     */
    private static PrivateKey convertToSunImpl(PrivateKey key) {
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(key.getEncoded());
        KeyFactory kf;
        try {
            kf = KeyFactory.getInstance(KeyUtil.RSA_ALGORITHM);
            return kf.generatePrivate(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Date generateNotBeforeDate() {
        return new Date(System.currentTimeMillis() - CERTIFICATE_TOLERANCE_OFFSET);
    }

    private static Date generateNotAfterDate(Date notBefore, long validityPeriod) {
        return new Date(notBefore.getTime() + validityPeriod);
    }

    private static List<ExtensionHolder> getClientExtensions() {
        List<ExtensionHolder> extensions = new ArrayList<>();

        extensions.add(new ExtensionHolder(Extension.basicConstraints, true,
                new BasicConstraints(false)));
        extensions.add(new ExtensionHolder(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature)));
        extensions.add(new ExtensionHolder(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth)));

        return extensions;
    }

    private static List<ExtensionHolder> getServerExtensions(X509Certificate issuerCertificate)
            throws CertificateEncodingException, NoSuchAlgorithmException, IOException {
        List<ExtensionHolder> extensions = new ArrayList<>();

        // SSO forces us to allow data encipherment
        extensions.add(new ExtensionHolder(Extension.keyUsage, true, new KeyUsage(
                KeyUsage.digitalSignature
                        | KeyUsage.keyEncipherment
                        | KeyUsage.dataEncipherment)));

        extensions.add(new ExtensionHolder(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)));

        Extension authorityKeyExtension = new Extension(Extension.authorityKeyIdentifier, false,
                new DEROctetString(new JcaX509ExtensionUtils()
                        .createAuthorityKeyIdentifier(issuerCertificate)));
        extensions.add(new ExtensionHolder(authorityKeyExtension.getExtnId(),
                authorityKeyExtension.isCritical(), authorityKeyExtension.getParsedValue()));

        return extensions;
    }

    private static String getSerialNumber(X509Certificate cert) {
        return cert.getSerialNumber() == null ? null : cert.getSerialNumber().toString();
    }

    public enum ThumbprintAlgorithm {
        SHA_1("SHA-1", "[a-fA-F0-9:]{59}"), SHA_256("SHA-256", "[a-fA-F0-9:]{95}");

        public static final ThumbprintAlgorithm DEFAULT = SHA_1;

        ThumbprintAlgorithm(String algorithmName, String thumbprintRegex) {
            this.algorithmName = algorithmName;
            this.thumbprintRegex = thumbprintRegex;
        }

        private final String algorithmName;
        private final String thumbprintRegex;

        public String getAlgorithmName() {
            return this.algorithmName;
        }

        public boolean isValid(String thumbPrint) {
            if (thumbPrint == null) {
                return false;
            }
            return thumbPrint.matches(this.thumbprintRegex);
        }

        public static boolean isValidThumbprint(String thumbPrint) {
            if (thumbPrint == null) {
                return false;
            }
            return SHA_256.isValid(thumbPrint) || SHA_1.isValid(thumbPrint);
        }
    }

    /**
     * Wrapper class for certificate chain and a private key.
     */
    public static class CertChainKeyPair {
        private final List<X509Certificate> certificateChain;
        private final PrivateKey privateKey;
        private final X509Certificate certificate;

        public CertChainKeyPair(List<X509Certificate> certificateChain,
                X509Certificate certificate, PrivateKey privateKey) {
            this.certificateChain = certificateChain;
            this.certificate = certificate;
            this.privateKey = privateKey;
        }

        public List<X509Certificate> getCertificateChain() {
            return this.certificateChain;
        }

        public PrivateKey getPrivateKey() {
            return this.privateKey;
        }

        public X509Certificate getCertificate() {
            return this.certificate;
        }
    }

    private static class ExtensionHolder {

        private final ASN1ObjectIdentifier oid;
        private final boolean isCritical;
        private final ASN1Encodable value;

        public ExtensionHolder(ASN1ObjectIdentifier oid, boolean isCritical, ASN1Encodable value) {
            this.oid = oid;
            this.isCritical = isCritical;
            this.value = value;
        }

        public ASN1ObjectIdentifier getOID() {
            return this.oid;
        }

        public boolean isCritical() {
            return this.isCritical;
        }

        public ASN1Encodable getValue() {
            return this.value;
        }
    }
}
