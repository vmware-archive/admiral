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
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

/**
 * KeyManager that delegates to one of multiple KeyManagers based on alias. Each delegate KeyManager
 * should have one and only one unique alias it handles.
 *
 * The list of delegates can be changed in runtime without reloading or replacing the main
 * KeyManager.
 */
public class DelegatingX509KeyManager extends X509ExtendedKeyManager {
    private final Map<String, X509ExtendedKeyManager> delegates = new ConcurrentHashMap<String, X509ExtendedKeyManager>();

    @Override
    public String chooseEngineClientAlias(String[] keyType,
            Principal[] issuers, SSLEngine engine) {

        // try each delegate and see if has a match for the issuers
        for (X509KeyManager delegate : delegates.values()) {
            String alias = ((X509ExtendedKeyManager) delegate)
                    .chooseEngineClientAlias(keyType, issuers, engine);

            if (alias != null) {
                return alias;
            }
        }

        // no matching alias found
        return null;
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers,
            SSLEngine engine) {

        throw new UnsupportedOperationException();
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
            Socket socket) {

        throw new UnsupportedOperationException();
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers,
            Socket socket) {
        throw new UnsupportedOperationException();
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
        X509Certificate[] certificateChain = delegates.get(alias)
                .getCertificateChain(alias);
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
     * Add a delegate identified by the given unique key (can be used to remove it later)
     *
     * @param alias
     * @param newDelegate
     */
    public void putDelegate(String alias, X509ExtendedKeyManager newDelegate) {
        if (!alias.equals(alias.toLowerCase())) {
            // aliases given by the SSL engine are case insensitive so make sure
            // they are normalized
            throw new IllegalArgumentException("Aliases must be all lowercase");
        }
        delegates.put(alias, newDelegate);
    }

    /**
     * Remove a previously added delegate
     *
     * @param alias
     * @return
     */
    public X509KeyManager removeDelegate(String alias) {
        return delegates.remove(alias);
    }

}
