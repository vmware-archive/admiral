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

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.net.ssl.X509TrustManager;

import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.support.CertificateInfo;
import com.vmware.photon.controller.model.support.CertificateInfoServiceErrorResponse;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

public class X509TrustManagerResolver implements X509TrustManager {
    private static X509TrustManager trustManager;
    private List<X509Certificate> connectionCertificates = new ArrayList<>();
    private boolean certsTrusted;
    private CertificateException certificateException;

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certs,
            String authType) {
        throw new UnsupportedOperationException("Client authentication is not supported.");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] certs,
            String authType) throws CertificateException {
        certs[0].checkValidity();
        this.certificateException = validateIfTrusted(certs, authType);
        this.certsTrusted = this.certificateException == null;

        Collections.addAll(this.connectionCertificates, certs);
    }

    public boolean isCertsTrusted() {
        return this.certsTrusted;
    }

    /**
     * Get the Server Trust Certificate from the chain.
     */
    public X509Certificate getCertificate() {
        if (this.connectionCertificates.isEmpty()) {
            throw new IllegalStateException(
                    "checkServerTrusted was not called or was not successful.");
        }
        return this.connectionCertificates.get(0);
    }

    /**
     * @return {@link CertificateException} in case the certificate is not trusted
     */
    public CertificateException getCertificateException() {
        return this.certificateException;
    }

    /**
     * @return {@link CertificateInfoServiceErrorResponse} for the untrusted certificate or {@code
     * null} if the resolver was not called or the certificate is trusted
     */
    public CertificateInfoServiceErrorResponse getCertificateInfoServiceErrorResponse() {
        if (this.connectionCertificates.isEmpty()) {
            return null;
        }
        X509Certificate[] chain = getCertificateChain();
        String certificate = CertificateUtil.toPEMformat(chain);
        Map<String, String> certProps = CertificateUtil.getCertificateInfoProperties(chain[0]);
        CertificateInfo certificateInfo = CertificateInfo.of(certificate, certProps);

        CertificateException certException = getCertificateException();

        return CertificateInfoServiceErrorResponse.create(
                certificateInfo,
                Operation.STATUS_CODE_UNAVAILABLE,
                CertificateInfoServiceErrorResponse.ERROR_CODE_UNTRUSTED_CERTIFICATE,
                certException.getCause());
    }

    public X509Certificate[] getCertificateChain() {
        if (this.connectionCertificates.isEmpty()) {
            throw new IllegalStateException(
                    "checkServerTrusted was not called or was not successful.");
        }

        return this.connectionCertificates.toArray(new X509Certificate[0]);
    }

    private CertificateException validateIfTrusted(X509Certificate[] certificates,
            String authType) {
        if (trustManager == null) {
            trustManager = ServerX509TrustManager.getInstance();
            if (trustManager == null) {
                return new CertificateException(
                        "Cannot validate certificate chain.",
                        new IllegalStateException("ServerX509TrustManager not initialized."));
            }
        }

        try {
            trustManager.checkServerTrusted(certificates, authType);
            return null;
        } catch (CertificateException e) {
            Utils.log(getClass(), CertificateException.class.getSimpleName(), Level.FINE,
                    Utils.toString(e));
            return e;
        }
    }

}