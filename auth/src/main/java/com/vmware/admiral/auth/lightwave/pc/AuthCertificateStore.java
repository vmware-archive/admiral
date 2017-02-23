/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.auth.lightwave.pc;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Static utility functions for OAuth clients.
 */
public class AuthCertificateStore {
    private final KeyStore keyStore;

    /**
     * Constructor.
     */
    AuthCertificateStore() throws AuthException {
        try {
            keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
        } catch (Exception e) {
            throw new AuthException("Failed to get JKS key store.", e);
        }
    }

    /**
     * Retrieve key store.
     *
     * @return
     */
    public KeyStore getKeyStore() {
        return keyStore;
    }

    /**
     * Add a base64 certificate to key store.
     *
     * @param name
     * @param base64Certificate
     * @throws AuthException
     * @throws KeyStoreException
     */
    public void setCertificateEntry(String name, String base64Certificate) throws AuthException {
        X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
        X509Certificate x509Certificate = null;

        try {
            x509Certificate = x509CertificateHelper.getX509CertificateFromBase64(base64Certificate);
        } catch (CertificateException e) {
            throw new AuthException(
                    "Failed to create X509Certificate for the passed base64 certificate", e);
        }

        setCertificateEntry(name, x509Certificate);
    }

    /**
     * Add a X509 certificate to key store.
     *
     * @param name
     * @param x509Certificate
     * @throws AuthException
     * @throws KeyStoreException
     */
    public void setCertificateEntry(String name, X509Certificate x509Certificate)
            throws AuthException {
        try {
            keyStore.setCertificateEntry(name, x509Certificate);
        } catch (KeyStoreException e) {
            throw new AuthException("Failed to add cerificate to the store.", e);
        }
    }
}
