/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import static com.vmware.admiral.service.common.SslTrustCertificateService.SSL_TRUST_LAST_UPDATED_DOCUMENT_KEY;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.net.ssl.X509TrustManager;

import com.vmware.admiral.common.util.ServiceDocumentQuery.ServiceDocumentQueryElementResult;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Server Trust Manager that can verify public certificates as well as self-signed stored in
 * SslTrustCertificateService. The TrustManager is synchronized with any changes in
 * SslTrustCertificateService and have up to date ssl trust certificate at any point of time with
 * some delays when the deployment requires polling for updates instead of subscription based model.
 */
public class ServerX509TrustManager implements X509TrustManager, Closeable {
    private static final String SSL_TRUST_CONFIG_SUBSCRIBE_FOR_LINK = UriUtils.buildUriPath(
            ConfigurationFactoryService.SELF_LINK, SSL_TRUST_LAST_UPDATED_DOCUMENT_KEY);

    protected long maintenanceIntervalInitial = Long.getLong(
            "dcp.management.config.certificates.reload.period.initial.micros",
            TimeUnit.MINUTES.toMicros(1));

    protected long maintenanceInterval = Long.getLong(
            "dcp.management.config.certificates.reload.period.micros",
            TimeUnit.MINUTES.toMicros(5));

    protected volatile int reloadCounterThreshold = 10;
    private volatile AtomicInteger reloadCounter = new AtomicInteger(0);


    public static final String JAVAX_NET_SSL_TRUST_STORE = "dcp.net.ssl.trustStore";
    public static final String JAVAX_NET_SSL_TRUST_STORE_PASSWORD =
            "dcp.net.ssl.trustStorePassword";
    private static final String DEFAULT_JAVA_CACERTS_PASSWORD = "changeit";

    private static volatile ServerX509TrustManager INSTANCE;

    private final DelegatingX509TrustManager delegatingTrustManager;
    private final ServiceHost host;
    private final ServiceDocumentQuery<SslTrustCertificateState> sslTrustQuery;
    private final SslTrustQueryCompletionHandler queryHandler;
    private final SubscriptionManager<ConfigurationState> subscriptionManager;

    /* Last time the document was update in microseconds since UNIX epoch */
    private volatile long documentUpdateTimeMicros;

    private AtomicBoolean started = new AtomicBoolean();

    public static ServerX509TrustManager create(ServiceHost host) {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (ServerX509TrustManager.class) {
            if (INSTANCE == null) {
                ServerX509TrustManager tm = new ServerX509TrustManager(host);
                tm.start();
                INSTANCE = tm;
            }
        }
        return INSTANCE;
    }

    /**
     * This method only initialize the ServerX509TrustManager, does not start it. The method is
     * intended to be use only on server start.
     */
    public static ServerX509TrustManager init(ServiceHost host) {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (ServerX509TrustManager.class) {
            if (INSTANCE == null) {
                INSTANCE = new ServerX509TrustManager(host);
            }
        }
        return INSTANCE;
    }

    /**
     * Invalidate the instance, if created/initialized and the copy in SslCertificateResolver
     */
    public static synchronized void invalidate() {
        INSTANCE = null;
        SslCertificateResolver.invalidateTrustManager();
    }

    protected ServerX509TrustManager(ServiceHost host) {
        AssertUtil.assertNotNull(host, "serviceHost");
        this.host = host;

        delegatingTrustManager = new DelegatingX509TrustManager();

        String cacerts = System.getProperty("java.home") + File.separator + "lib"
                + File.separator + "security" + File.separator + "cacerts";

        String customTrustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        String customTrustStorePassword = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD,
                DEFAULT_JAVA_CACERTS_PASSWORD);

        if (customTrustStore != null) {
            if (Paths.get(cacerts).equals(Paths.get(customTrustStore))) {
                // the custom trust store points to the default one,
                // probably because its password has been changed
                addTrustManager("default-CA", cacerts, customTrustStorePassword);
            } else {
                // different trust stores, adding both
                addTrustManager("default-CA", cacerts, DEFAULT_JAVA_CACERTS_PASSWORD);
                addTrustManager("custom-CA", customTrustStore, customTrustStorePassword);
            }
        } else {
            // no custom trust store provided,
            // but the password of the default one could still have been changed
            addTrustManager("default-CA", cacerts, customTrustStorePassword);
        }

        this.sslTrustQuery = new ServiceDocumentQuery<SslTrustCertificateState>(host,
                SslTrustCertificateState.class);

        this.queryHandler = new SslTrustQueryCompletionHandler(this);

        this.subscriptionManager = new SubscriptionManager<>(host,
                host.getId(), SSL_TRUST_CONFIG_SUBSCRIBE_FOR_LINK, ConfigurationState.class,
                true);
    }

    private void addTrustManager(String alias, String trustStore, String trustStorePassword) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.INFO,
                "Adding trust store '%s' (path: '%s') to the trust manager...", alias, trustStore);
        try (InputStream is = new FileInputStream(trustStore)) {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(is, trustStorePassword.toCharArray());

            delegatingTrustManager.putDelegate(alias,
                    (X509TrustManager) CertificateUtil.getTrustManagers(store)[0]);
        } catch (Exception e) {
            Utils.logWarning("Exception during trust manager configuration. Trust Store '%s'"
                            + " (path: '%s'). Error: %s", alias, trustStore, Utils.toString(e));
        }
    }

    /**
     * Start the active subscription notifications of this trust manager and load the initial state
     * of ssl trust certificates.
     */
    public void start() {
        // check if it's already started
        if (started.getAndSet(true)) {
            return;
        }

        this.documentUpdateTimeMicros = 0;
        try {
            verifySubscriptionTargetExists(() -> {
                try {
                    subscribeForSslTrustCertNotifications();
                    loadSslTrustCertServices();
                } catch (Exception e) {
                    host.log(Level.SEVERE,
                            "Failure while subscribing for ssl certificate notifications: %s",
                            Utils.toString(e));
                }
            });
        } finally {
            schedulePeriodicCertificatesReload();
        }
    }

    /**
     * Periodically reload all certificates in case we missed something.. e.g. replicated
     * certificates from other xenon nodes
     */
    private void schedulePeriodicCertificatesReload() {
        long nextDelay = (reloadCounter.get() > reloadCounterThreshold) ?
                maintenanceInterval :
                maintenanceIntervalInitial;

        if (host.isStopping()) {
            return;
        }

        Runnable task = () -> {
            try {
                host.log(Level.FINE, "Host %s reloading all certificates", host.getPublicUri());
                documentUpdateTimeMicros = 0;
                loadSslTrustCertServices();

                reloadCounter.updateAndGet((r) -> (r > reloadCounterThreshold) ? r : r + 1);

                schedulePeriodicCertificatesReload();
            } catch (Exception e) {
                host.log(Level.WARNING, e.getMessage());
                host.log(Level.FINE, Utils.toString(e));

                schedulePeriodicCertificatesReload();
            }
        };

        try {
            host.schedule(task, nextDelay, TimeUnit.MICROSECONDS);
        } catch (Exception e) {
            host.log(Level.INFO, "Host is stopping");
        }
    }

    @Override
    public void close() {
        this.subscriptionManager.close();
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        delegatingTrustManager.checkServerTrusted(chain, authType);
    }

    private void loadSslTrustCertServices() {
        // make sure first get the time, then issue the query not to miss an interval with updates
        long currentDocumentUpdateTimeMicros = Utils.getNowMicrosUtc();
        sslTrustQuery.queryUpdatedSince(documentUpdateTimeMicros, queryHandler);
        this.documentUpdateTimeMicros = currentDocumentUpdateTimeMicros;
    }

    private void subscribeForSslTrustCertNotifications() {
        this.subscriptionManager.start((n) -> {
            loadSslTrustCertServices();
        }, null);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        delegatingTrustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegatingTrustManager.getAcceptedIssuers();
    }

    private void verifySubscriptionTargetExists(Runnable handler) {
        new ServiceDocumentQuery<>(host, ConfigurationState.class)
                .queryDocument(SSL_TRUST_CONFIG_SUBSCRIBE_FOR_LINK, (r) -> {
                    if (r.hasException()) {
                        r.throwRunTimeException();
                    } else if (r.hasResult()) {
                        // subscription is possible, proceed.
                        try {
                            handler.run();
                        } catch (Throwable t) {
                            Utils.logWarning("%s", Utils.toString(t));
                        }
                    } else {
                        // create new configuration element in order to subscribe to it
                        ConfigurationState body = new ConfigurationState();
                        body.key = SSL_TRUST_LAST_UPDATED_DOCUMENT_KEY;
                        body.documentSelfLink = body.key;
                        body.value = "__initial-value";
                        host.sendRequest(Operation.createPost(
                                UriUtils.buildUri(host, ConfigurationFactoryService.SELF_LINK))
                                .setReferer(host.getUri())
                                .setBody(body)
                                .setCompletion((o, e) -> {
                                    if (e != null) {
                                        Utils.logWarning("%s", Utils.toString(e));
                                        return;
                                    }
                                    handler.run();
                                }));
                    }
                });
    }

    protected DelegatingX509TrustManager getDelegatingTrustManager() {
        return delegatingTrustManager;
    }

    public void putDelegate(String alias, String certificate) {
        X509TrustManager delegateTrustManager = (X509TrustManager) CertificateUtil
                .getTrustManagers(alias, certificate)[0];

        this.delegatingTrustManager.putDelegate(alias, delegateTrustManager);
    }

    public X509TrustManager getDelegate(Object key) {
        return this.delegatingTrustManager.getDelegate(key);
    }

    private static class SslTrustQueryCompletionHandler implements
            Consumer<ServiceDocumentQueryElementResult<SslTrustCertificateState>> {
        private final ServerX509TrustManager self;

        private SslTrustQueryCompletionHandler(ServerX509TrustManager self) {
            this.self = self;
        }

        @Override
        public void accept(ServiceDocumentQueryElementResult<SslTrustCertificateState> result) {
            if (result.hasException()) {
                Utils.logWarning("Exception during ssl trust cert loading: %s",
                        (result.getException() instanceof CancellationException)
                                ? result.getException().getClass().getName()
                                : Utils.toString(result.getException()));
            } else if (result.hasResult()) {
                SslTrustCertificateState sslTrustCert = result.getResult();
                self.host.log(Level.FINE, "Adding certificate %s", sslTrustCert.fingerprint);

                if (ServiceDocument.isDeleted(sslTrustCert)) {
                    deleteCertificate(sslTrustCert.getAlias());
                } else {
                    loadCertificate(sslTrustCert);
                }
            }
        }

        private void loadCertificate(SslTrustCertificateState sslTrustCert) {
            try {
                self.putDelegate(sslTrustCert.getAlias(), sslTrustCert.certificate);
                Utils.log(getClass(), "Self Signed Trust Store", Level.FINE,
                        "Certificate with alias %s updated", sslTrustCert.getAlias());
            } catch (Throwable e) {
                Utils.logWarning(
                        "Exception during certificate reload with alias: %s. Error: %s",
                        sslTrustCert.getAlias(), Utils.toString(e));
            }
        }

        private void deleteCertificate(String alias) {
            self.delegatingTrustManager.removeDelegate(alias);
            Utils.log(getClass(), "Self Signed Trust Store", Level.FINE,
                    "Certificate with alias %s removed", alias);

        }
    }

}
