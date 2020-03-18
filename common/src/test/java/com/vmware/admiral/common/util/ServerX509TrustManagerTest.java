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

import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE;
import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE_PASSWORD;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.xenon.common.ServiceHost;

public class ServerX509TrustManagerTest {

    private static ServerX509TrustManager trustManager;
    private static String originalTrustStoreProp;
    private static String originalTrustStorePasswordProp;

    @BeforeClass
    public static void setUp() throws Throwable {
        // Force a custom trust store... that shouldn't override the Java default cacerts.
        URI customStore = ServerX509TrustManagerTest.class
                .getResource("/certs/trusted_certificates.jks").toURI();
        File f = new File(customStore.getPath());

        originalTrustStoreProp = System.setProperty(JAVAX_NET_SSL_TRUST_STORE, f.getPath());
        originalTrustStorePasswordProp = System.setProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD,
                "changeit");

        // Fake host, not really needed for the purpose of the trust manager test.
        ServiceHost host = new ServiceHost() {
        };

        ServerX509TrustManager.invalidate();
        trustManager = ServerX509TrustManager.init(host);
    }

    @AfterClass
    public static void tearDown() throws Throwable {
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE,
                originalTrustStoreProp == null ? "" : originalTrustStoreProp);
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD,
                originalTrustStorePasswordProp == null ? "" : originalTrustStorePasswordProp);
    }

    @Test
    public void testTrustedCertificates() throws Exception {

        // Validate a public certificate chain, e.g. the Docker registry one.
        // Is should work because the default cacerts from the JRE is always included and trusted

        trustManager.checkServerTrusted(getCertificates("/certs/vmware.com.chain.crt"), "RSA");

        // Validate a custom certificate.
        // It should work because a truststore which contains the cert is passed as argument.

        trustManager.checkServerTrusted(getCertificates("/certs/trusted_server.crt"), "RSA");

        // Validate a custom certificate signed by a custom CA which is trusted.
        trustManager.checkServerTrusted(getCertificates("/certs/signed-server.crt"), "RSA");
    }

    @Test
    public void testUntrustedCertificates() throws Exception {

        // Validate a custom certificate.
        // Is should fail as it is signed by untrusted CA
        try {
            trustManager.checkServerTrusted(
                    getCertificates("/certs/untrusted-server.crt"), "RSA");
            fail("Should not trust untrusted certificate");
        } catch (CertificateException ignored) {
        }
    }

    private static X509Certificate[] getCertificates(String filename) throws Exception {
        URI customCertificate = ServerX509TrustManagerTest.class.getResource(filename).toURI();
        try (InputStream is = new FileInputStream(customCertificate.getPath())) {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certificate = factory.generateCertificates(is);
            return certificate.toArray(new X509Certificate[] {});
        }
    }

}
