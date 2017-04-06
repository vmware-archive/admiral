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

package com.vmware.admiral.common.util;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import com.vmware.photon.controller.model.security.util.CertificateUtil;

public class CertificateUtilExtended {

    public static boolean isSelfSignedCertificate(String certPEM) {
        try {
            X509Certificate[] certs = CertificateUtil.createCertificateChain(certPEM);
            if (certs.length != 1) {
                return false;
            }
            return isSelfSignedCertificate(certs[0]);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isSelfSignedCertificate(X509Certificate cert)
            throws CertificateException, NoSuchAlgorithmException,
            NoSuchProviderException {
        try {
            // Try to verify certificate signature with its own public key
            cert.verify(cert.getPublicKey());
            return true;
        } catch (SignatureException sigEx) {
            // Invalid signature --> not self-signed
            return false;
        } catch (InvalidKeyException keyEx) {
            // Invalid key --> not self-signed
            return false;
        }
    }
}
