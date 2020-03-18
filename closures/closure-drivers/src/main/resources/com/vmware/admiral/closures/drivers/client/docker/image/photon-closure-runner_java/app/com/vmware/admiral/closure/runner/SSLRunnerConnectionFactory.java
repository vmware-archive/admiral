/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.closure.runner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.BrowserCompatHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

/**
 * The class is responsible for SSL support of Java runtime
 *
 */
public class SSLRunnerConnectionFactory {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    private static final int MAX_TOTAL = 20;
    private static final int MAX_PER_ROUTE = 10;

    private KeyStore keyStore = null;
    private String trustStorePath = null;

    public SSLRunnerConnectionFactory(String trustStorePath) throws Exception {
        this.trustStorePath = trustStorePath;

        keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    }

    public HttpClientConnectionManager createConnectionManager() throws Exception {
        RegistryBuilder registryBuilder = RegistryBuilder.<ConnectionSocketFactory>create();

        registryBuilder.register(HTTP_SCHEME, PlainConnectionSocketFactory.getSocketFactory());
        try {
            Path storePath = Paths.get(trustStorePath);
            if (trustStorePath != null && Files.exists(storePath) && new File(trustStorePath)
                    .length() > 0) {
                registryBuilder.register(HTTPS_SCHEME, createSSLConnectionFactory());
            }
        } catch (Exception ex) {
            System.err.println("Unable to register HTTPS scheme! Reason: " + ex.getMessage());
        }

        Registry<ConnectionSocketFactory> registry = registryBuilder.build();
        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(
                registry);

        setConnectionPoolParams(connManager);

        return connManager;
    }

    private SSLConnectionSocketFactory createSSLConnectionFactory() throws Exception {
        return new SSLConnectionSocketFactory(newSslContext("TLS"),
                new RunnerHostnameVerifier(keyStore));
    }

    private static class RunnerHostnameVerifier implements HostnameVerifier {
        private static final BrowserCompatHostnameVerifier BROWSER_COMPATIBLE_HOSTNAME_VERIFIER =
                new BrowserCompatHostnameVerifier();

        private KeyStore keyStore;

        public RunnerHostnameVerifier(KeyStore keyStore) {
            this.keyStore = keyStore;
        }

        public boolean verify(String hostname, SSLSession session) {
            try {
                Certificate[] certs = session.getPeerCertificates();
                X509Certificate x509 = (X509Certificate) certs[0];
                this.verify(hostname, x509);
                return true;
            } catch (SSLException var5) {
                return false;
            }
        }

        public void verify(String host, SSLSocket ssl) throws IOException {
            if (host == null) {
                throw new IllegalArgumentException("host to verify is null");
            } else {
                SSLSession session = ssl.getSession();
                if (session == null) {
                    InputStream in = ssl.getInputStream();
                    in.available();
                    session = ssl.getSession();
                    if (session == null) {
                        ssl.startHandshake();
                        session = ssl.getSession();
                    }
                }

                Certificate[] certs = session.getPeerCertificates();
                X509Certificate x509 = (X509Certificate) certs[0];
                this.verify(host, x509);
            }
        }

        public void verify(String host, X509Certificate cert) throws SSLException {
            try {
                String alias = cert.getSubjectX500Principal().getName();
                if (!keyStore.containsAlias(alias)) {
                    RunnerHostnameVerifier.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER.verify(host, cert);
                }
            } catch (KeyStoreException var4) {
                RunnerHostnameVerifier.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER.verify(host, cert);
            }

        }

        public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
            throw new UnsupportedOperationException(
                    "This verifier doesn't implement this functionality!");
        }
    }

    private void setConnectionPoolParams(PoolingHttpClientConnectionManager connManager) {
        connManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);
        connManager.setMaxTotal(MAX_TOTAL);
    }

    private SSLContext newSslContext(String protocol) throws Exception {
        SSLContext sslContext = SSLContext.getInstance(protocol);

        keyStore.load(null, null);
        TrustManagerFactory tmf = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());

        try (InputStream in = new FileInputStream(trustStorePath)) {
            Collection<? extends Certificate> certCollection = CertificateFactory
                    .getInstance("X509").generateCertificates(in);
            for (Certificate cert : certCollection) {
                String alias = ((X509Certificate) cert).getSubjectX500Principal().getName();

                keyStore.setCertificateEntry(alias, cert);
            }
        }

        tmf.init(keyStore);

        sslContext.init((KeyManager[]) null, tmf.getTrustManagers(), new SecureRandom());

        return sslContext;
    }

}
