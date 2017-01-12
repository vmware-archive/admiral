/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Utils;

/**
 * KeyManager that delegates to one of multiple KeyManagers based on alias. Each delegate KeyManager
 * should have one and only one unique alias it handles.
 *
 * The list of delegates can be changed in runtime without reloading or replacing the main
 * KeyManager.
 */
public class DelegatingX509KeyManager extends X509ExtendedKeyManager {

    private static final Logger logger = Logger.getLogger(DelegatingX509KeyManager.class.getName());

    private final Map<String, X509ExtendedKeyManager> delegates = new ConcurrentHashMap<>();

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
        String alias = getAliasByRemoteCert(engine);
        if (alias != null) {
            return alias;
        }

        return getAliasByRemoteCA(keyType, issuers, engine);
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
        throw new UnsupportedOperationException();
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        X509Certificate[] certificateChain = delegates.get(alias).getCertificateChain(alias);
        return certificateChain;
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
        PrivateKey privateKey = delegates.get(alias).getPrivateKey(alias);
        return privateKey;
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        throw new UnsupportedOperationException();
    }

    /**
     * Add a delegate identified by the given unique key (can be used to remove it later).
     *
     * @param alias       key with which the specified value is to be associated
     * @param newDelegate value to be associated with the specified key
     */
    public void putDelegate(String alias, X509ExtendedKeyManager newDelegate) {
        if (!alias.equals(alias.toLowerCase())) {
            // aliases given by the SSL engine are case insensitive so make sure
            // they are normalized
            throw new LocalizableValidationException("Aliases must be all lowercase", "common.certificate.aliases.lowercase");
        }
        delegates.put(alias, newDelegate);
    }

    /**
     * Remove a previously added delegate.
     *
     * @param alias key whose mapping is to be removed from the map
     * @return the previous value associated with key, or null if there was no mapping for key.
     */
    public X509KeyManager removeDelegate(String alias) {
        return delegates.remove(alias);
    }

    private String getAliasByRemoteCert(SSLEngine engine) {
        if (engine == null) {
            logger.info("Cannot choose client alias: SSLEngine is null");
            return null;
        }

        SSLSession handshakeSession = engine.getHandshakeSession();
        if (handshakeSession == null) {
            logger.info("Cannot choose client alias: HandshakeSession is null");
            return null;
        }

        Certificate[] peerCertificates = null;
        try {
            peerCertificates = handshakeSession.getPeerCertificates();
        } catch (SSLPeerUnverifiedException e) {
            logger.info("Cannot choose client alias: error getting peer certificate " +
                    Utils.toString(e));
            return null;
        }

        if (peerCertificates == null || peerCertificates.length == 0) {
            logger.info("Cannot choose client alias: peer certificate is empty");
            return null;
        }

        X509Certificate cert;
        try {
            cert = (X509Certificate) peerCertificates[0];
        } catch (ClassCastException e) {
            logger.info("Cannot choose client alias: not a X509 certificate: " + Utils.toString(e));
            return null;
        }

        String alias = CertificateUtil.generatePureFingerPrint(cert);

        if (delegates.containsKey(alias)) {
            return alias;
        }

        logger.info("Cannot choose client alias by cert: no delegate for key " + alias);
        return null;
    }

    private String getAliasByRemoteCA(String[] keyType, Principal[] issuers, SSLEngine engine) {
        // try each delegate and see if has a match for the issuers
        for (X509KeyManager delegate : delegates.values()) {
            String alias = ((X509ExtendedKeyManager) delegate)
                    .chooseEngineClientAlias(keyType, issuers, engine);

            if (alias != null) {
                return alias;
            }
        }

        logger.warning("Cannot choose client alias by CA: no delegate found");
        return null;
    }

}
