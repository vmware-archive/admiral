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

package com.vmware.photon.controller.model.security.ssl;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.net.ssl.X509TrustManager;

import com.vmware.photon.controller.model.security.service.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.util.AssertUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Server Trust Manager that can verify public certificates as well as self-signed stored in
 * SslTrustCertificateService. The TrustManager is synchronized with any changes in
 * SslTrustCertificateService and have up to date ssl trust certificate at any point of time with
 * some delays when the deployment requires polling for updates instead of subscription based model.
 */
public class ServerX509TrustManager implements X509TrustManager, Closeable {

    protected long maintenanceIntervalInitial = Long.getLong(
            "dcp.management.config.certificates.reload.period.initial.micros",
            TimeUnit.MINUTES.toMicros(1));

    protected long maintenanceInterval = Long.getLong(
            "dcp.management.config.certificates.reload.period.micros",
            TimeUnit.MINUTES.toMicros(5));

    protected volatile int reloadCounterThreshold = 10;
    private volatile AtomicInteger reloadCounter = new AtomicInteger(0);

    public static final String JAVAX_NET_SSL_TRUST_STORE = "dcp.net.ssl.trustStore";
    public static final String JAVAX_NET_SSL_TRUST_STORE_PASSWORD = "dcp.net.ssl.trustStorePassword";
    private static final String DEFAULT_JAVA_CACERTS_PASSWORD = "changeit";

    private static ServerX509TrustManager INSTANCE;

    private final DelegatingX509TrustManager delegatingTrustManager;
    private final ServiceHost host;

    /**
     * This method initializes if not already initialized and start the instance (load certificates
     * and subscribes for certificate changes)
     *
     * @param host
     *            cannot be null
     * @return the instance
     */
    public static synchronized ServerX509TrustManager create(ServiceHost host) {
        if (INSTANCE == null) {
            INSTANCE = new ServerX509TrustManager(host);
            INSTANCE.start();
        }
        return INSTANCE;
    }

    /**
     * This method only initialize the ServerX509TrustManager, does not start it. The method is
     * intended to be use only on server start.
     */
    public static synchronized ServerX509TrustManager init(ServiceHost host) {
        if (INSTANCE == null) {
            INSTANCE = new ServerX509TrustManager(host);
        }
        return INSTANCE;
    }

    /**
     * @return instance if created/initialized beforehand or {@code null}
     */
    public static ServerX509TrustManager getInstance() {
        return INSTANCE;
    }

    /**
     * Invalidate the instance, if created/initialized
     */
    public static synchronized void invalidate() {
        INSTANCE = null;
    }

    protected ServerX509TrustManager(ServiceHost host) {
        AssertUtil.assertNotNull(host, "serviceHost");
        this.host = host;

        this.delegatingTrustManager = new DelegatingX509TrustManager();

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

    }

    private void addTrustManager(String alias, String trustStore, String trustStorePassword) {
        Utils.log(getClass(), getClass().getSimpleName(), Level.INFO,
                "Adding trust store '%s' (path: '%s') to the trust manager...", alias, trustStore);
        try (InputStream is = new FileInputStream(trustStore)) {
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(is, trustStorePassword.toCharArray());

            this.delegatingTrustManager.putDelegate(alias,
                    (X509TrustManager) CertificateUtil.getTrustManagers(store)[0]);
        } catch (Exception e) {
            Utils.logWarning(
                    "Exception during trust manager configuration. Trust Store '%s' (path: '%s'). Error: %s",
                    alias, trustStore, Utils.toString(e));
        }
    }

    /**
     * Start the active subscription notifications of this trust manager and load the initial state
     * of ssl trust certificates.
     */
    public void start() {
        try {
            loadSslTrustCertServices();
            subscribeForSslTrustCertNotifications();
        } catch (Exception e) {
            this.host.log(Level.SEVERE,
                    "Failure while subscribing for ssl certificate notifications: "
                            + Utils.toString(e));
        } finally {
            schedulePeriodicCertificatesReload();
        }
    }

    /**
     * Periodically reload all certificates in case we missed something.. e.g. replicated
     * certificates from other xenon nodes
     */
    private void schedulePeriodicCertificatesReload() {
        long nextDelay = (this.reloadCounter.get() > this.reloadCounterThreshold)
                ? this.maintenanceInterval
                : this.maintenanceIntervalInitial;

        if (this.host.isStopping()) {
            return;
        }
        this.host.schedule(() -> {
            try {
                this.host.log(Level.INFO, "Host " + this.host.getPublicUri()
                        + ": reloading all certificates");
                loadSslTrustCertServices();

                this.reloadCounter.updateAndGet((r) -> (r > this.reloadCounterThreshold) ? r
                        : r +
                                1);

                schedulePeriodicCertificatesReload();
            } catch (Exception e) {
                this.host.log(Level.WARNING, "%s", e.getMessage());
                this.host.log(Level.FINE, "%s", Utils.toString(e));

                schedulePeriodicCertificatesReload();
            }
        }, nextDelay, TimeUnit.MICROSECONDS);
    }

    @Override
    public void close() {
        // unsubscribe
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        this.delegatingTrustManager.checkServerTrusted(chain, authType);
    }

    private void loadSslTrustCertServices() {
        SslTrustCertificateServiceUtils.loadCertificates(this.host, this::registerCertificate);
    }

    private void subscribeForSslTrustCertNotifications() {
        SslTrustCertificateServiceUtils.subscribe(this.host, this::certificateChanged);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        this.delegatingTrustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return this.delegatingTrustManager.getAcceptedIssuers();
    }

    protected DelegatingX509TrustManager getDelegatingTrustManager() {
        return this.delegatingTrustManager;
    }

    public void putDelegate(String alias, String certificate) {
        X509TrustManager delegateTrustManager = (X509TrustManager) CertificateUtil
                .getTrustManagers(alias, certificate)[0];

        this.delegatingTrustManager.putDelegate(alias, delegateTrustManager);
    }

    public X509TrustManager getDelegate(Object key) {
        return this.delegatingTrustManager.getDelegate(key);
    }

    private void certificateChanged(Operation operation) {
        Utils.log(getClass(), getClass().getName(), Level.WARNING,
                "process certificate changed for operation %s", operation.toLogString());
        QueryTask queryTask = operation.getBody(QueryTask.class);
        if (queryTask.results != null && queryTask.results.documentLinks != null
                && !queryTask.results.documentLinks.isEmpty()) {

            queryTask.results.documents.values().forEach(doc -> {
                SslTrustCertificateState cert = Utils.fromJson(doc, SslTrustCertificateState.class);
                if (Action.DELETE.toString().equals(cert.documentUpdateAction)) {
                    deleteCertificate(cert.getAlias());
                } else {
                    registerCertificate(cert);
                }
            });
        } else {
            Utils.log(getClass(), getClass().getName(), Level.WARNING,
                    "No document links for operation %s", operation.toLogString());
        }
        operation.complete();
    }

    public void registerCertificate(SslTrustCertificateState sslTrustCert) {
        String alias = sslTrustCert.getAlias();
        try {
            this.putDelegate(alias, sslTrustCert.certificate);
            Utils.log(getClass(), "Self Signed Trust Store", Level.FINE,
                    "Certificate with alias %s updated", alias);
        } catch (Throwable e) {
            Utils.logWarning(
                    "Exception during certificate reload with alias: %s. Error: %s",
                    alias, Utils.toString(e));
        }
    }

    private void deleteCertificate(String alias) {
        this.delegatingTrustManager.removeDelegate(alias);
        Utils.log(getClass(), "Self Signed Trust Store", Level.FINE,
                "Certificate with alias %s removed", alias);

    }

}
