/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.OperationContext;
import com.vmware.xenon.common.Utils;

/**
 * Helper class, responsible for resolving remote SSL certificates.
 */
public class SslCertificateResolver {

    private static final Logger logger = Logger.getLogger(SslCertificateResolver.class.getName());

    private static X509TrustManager trustManager;
    private static final int DEFAULT_SECURE_CONNECTION_PORT = 443;
    private static final long DEFAULT_CONNECTION_TIMEOUT_MILLIS = Long.getLong(
            "ssl.resolver.import.timeout.millis", TimeUnit.SECONDS.toMillis(30));
    private final String uri;
    private final String hostAddress;
    private final int port;

    private List<X509Certificate> connectionCertificates;
    private boolean certsTrusted;
    private final long timeout;
    private SSLContext sslContext;

    private static final int THREAD_POOL_QUEUE_SIZE = 1000;
    private static final int THREAD_POOL_KEEPALIVE_SECONDS = 30;

    private static ExecutorService executor = new ThreadPoolExecutor(0,
            Utils.DEFAULT_THREAD_COUNT, THREAD_POOL_KEEPALIVE_SECONDS,
            TimeUnit.SECONDS, new LinkedBlockingQueue<>(THREAD_POOL_QUEUE_SIZE),
            new ThreadPoolExecutor.AbortPolicy());

    /**
     * Connects to uri in separate thread and then executes the callback. First parameter for the
     * callback function is the SSLCertificateResolver object itself or null in case there's an
     * error. The second one is the error itself or null if no such is thrown.
     *
     * @param uri remote host uri
     * @param callback BiConsumer callback function accepting SslCertificateResolver and Throwable
     */
    public static void execute(URI uri,
            BiConsumer<SslCertificateResolver, Throwable> callback) {
        final OperationContext parentContext = OperationContext.getOperationContext();

        executor.execute(() -> {
            OperationContext childContext = OperationContext.getOperationContext();
            try {
                // set the operation context of the parent thread in the current thread
                OperationContext.restoreOperationContext(parentContext);

                SslCertificateResolver resolver = new SslCertificateResolver(uri);
                Throwable t = null;
                try {
                    resolver = resolver.connect();
                } catch (Throwable e) {
                    String msg = String.format("Error connecting to %s : %s",
                            uri.toString(), e.getMessage());
                    logger.warning(msg);
                    t = e;
                }
                if (t != null) {
                    callback.accept(null, t);
                } else {
                    callback.accept(resolver, null);
                }
            } finally {
                OperationContext.restoreOperationContext(childContext);
            }
        });
    }

    public static void invalidateTrustManager() {
        trustManager = null;
    }

    private SslCertificateResolver(URI uri) {
        this(uri, DEFAULT_CONNECTION_TIMEOUT_MILLIS);
    }

    private SslCertificateResolver(URI uri, long timeout) {
        this.hostAddress = uri.getHost();
        this.port = uri.getPort() == -1 ? DEFAULT_SECURE_CONNECTION_PORT : uri.getPort();
        this.timeout = timeout;
        this.uri = String.format("%s://%s:%d", uri.getScheme(), hostAddress, port);
    }

    private SslCertificateResolver connect() {
        logger.entering(logger.getName(), "connect");
        connectionCertificates = new ArrayList<>();
        // create a SocketFactory without TrustManager (well with one that accepts anything)
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] certs,
                    String authType) {
                throw new UnsupportedOperationException("Client authentication is not supported.");
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] certs,
                    String authType) throws CertificateException {
                certs[0].checkValidity();
                certsTrusted = validateIfTrusted(certs, authType);

                for (X509Certificate certificate : certs) {
                    connectionCertificates.add(certificate);
                }
            }
        } };

        try {
            if (sslContext == null) {
                sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new SecureRandom());
            }
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            logger.throwing(logger.getName(), "connect", e);
            throw new LocalizableValidationException(e, "Failed to initialize SSL context.",
                    "common.ssh.context.init");
        }

        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        try (SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket()) {
            sslSocket.connect(new InetSocketAddress(this.hostAddress, this.port), (int) timeout);
            SSLSession session = sslSocket.getSession();
            session.invalidate();
        } catch (IOException e) {
            if (certsTrusted || !connectionCertificates.isEmpty()) {
                Utils.logWarning(
                        "Exception while resolving certificate for host: [%s]. Error: %s ",
                        uri, e.getMessage());
            } else {
                logger.throwing(logger.getName(), "connect", e);
                if (e instanceof SocketTimeoutException) {
                    throw new LocalizableValidationException(e, "Connection timeout",
                            "common.connection.timeout");
                } else if (e instanceof ConnectException) {
                    throw new LocalizableValidationException(e, "Connection refused",
                            "common.connection.refused");
                } else {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            }
        }

        if (connectionCertificates.size() == 0) {
            LocalizableValidationException e = new LocalizableValidationException(
                    "Importing ssl certificate failed for server: " + uri,
                    "common.certificate.import.failed", uri);

            logger.throwing(logger.getName(), "connect", e);
            throw e;
        }
        logger.exiting(logger.getName(), "connect");
        return this;
    }

    private boolean validateIfTrusted(X509Certificate[] certificates, String authType) {
        if (trustManager == null) {
            trustManager = ServerX509TrustManager.init(null);
        }

        try {
            trustManager.checkServerTrusted(certificates, authType);
            return true;
        } catch (CertificateException e) {
            Utils.log(getClass(), CertificateException.class.getSimpleName(), Level.FINE,
                    "%s", Utils.toString(e));
            return false;
        }
    }

    public boolean isCertsTrusted() {
        return certsTrusted;
    }

    public X509Certificate[] getCertificateChain() {
        if (connectionCertificates.isEmpty()) {
            throw new IllegalStateException("connect() was not called or was not successful.");
        }

        X509Certificate[] certificateChain = new X509Certificate[connectionCertificates.size()];
        certificateChain = connectionCertificates.toArray(certificateChain);
        return certificateChain;
    }

    /**
     * Get the Server Trust Certificate from the chain.
     */
    public X509Certificate getCertificate() {
        if (connectionCertificates.isEmpty()) {
            throw new IllegalStateException("connect() was not called or was not successful.");
        }
        return connectionCertificates.get(0);
    }

}
