/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.bouncycastle.openssl.PEMWriter;

import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.photon.controller.model.security.util.KeyUtil;
import com.vmware.xenon.common.ServiceHost;

@SuppressWarnings("deprecation")
public class CertificateUtilExtended {

    public static final String CUSTOM_PROPERTY_TRUST_CERT_LINK = "__trustCertLink";
    public static final String CUSTOM_PROPERTY_PKS_UAA_TRUST_CERT_LINK = "__pksUaaTrustCertLink";
    public static final String CUSTOM_PROPERTY_PKS_API_TRUST_CERT_LINK = "__pksApiTrustCertLink";

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

    /**
     * Serialize the content of a .crt file to X509 certificate chain
     */
    public static X509Certificate[] fromFile(File certFile) {
        try {
            String content = new String(Files.readAllBytes(certFile.toPath()));
            return CertificateUtil.createCertificateChain(content);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * PSC 6.5 SAML requirement due to Bouncycastle library conflicts.
     */
    public static String toPEMformat(X509Certificate certificate, ServiceHost host) {
        if (useAuthConfig(host)) {
            return certToPEMformat(certificate);
        } else {
            return CertificateUtil.toPEMformat(certificate);
        }
    }

    /**
     * PSC 6.5 SAML requirement due to Bouncycastle library conflicts.
     */
    public static String toPEMformat(X509Certificate[] certificateChain, ServiceHost host) {
        StringWriter sw = new StringWriter();
        for (X509Certificate certificate : certificateChain) {
            sw.append(toPEMformat(certificate, host));
        }
        return sw.toString();
    }

    /**
     * PSC 6.5 SAML requirement due to Bouncycastle library conflicts.
     */
    public static String toPEMFormat(Key key, ServiceHost host) {
        if (useAuthConfig(host)) {
            return keyToPEMFormat(key);
        } else {
            return KeyUtil.toPEMFormat(key);
        }
    }

    /**
     * Serialize Key in PEM format, compatible version for bcprov 1.50
     */
    private static String keyToPEMFormat(Key key) {
        StringWriter sw = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(sw);
        try {
            pemWriter.writeObject(key);
            pemWriter.close();

            return sw.toString();

        } catch (IOException x) {
            throw new RuntimeException("Failed to serialize key", x);
        }
    }

    /**
     * Serialize Certificate in PEM format, compatible version for bcprov 1.50
     */
    private static String certToPEMformat(X509Certificate certificate) {
        StringWriter sw = new StringWriter();
        PEMWriter pemWriter = new PEMWriter(sw);
        try {
            pemWriter.writeObject(certificate);
            pemWriter.close();

            return sw.toString();

        } catch (IOException x) {
            throw new RuntimeException("Failed to serialize certificate", x);
        }
    }

    private static boolean useAuthConfig(ServiceHost host) {
        String field = getAuthConfigFile(host);
        return (field != null) && (!field.isEmpty());
    }

    private static final String AUTH_CONFIG_FILE = "authConfig";

    private static String getAuthConfigFile(ServiceHost host) {
        return PropertyUtils.getValue(host, AUTH_CONFIG_FILE);
    }

}
