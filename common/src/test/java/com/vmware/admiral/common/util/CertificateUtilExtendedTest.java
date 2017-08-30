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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

public class CertificateUtilExtendedTest {

    private static final String PROPERTIES_FILE_NAME = "CertificateUtilExtendedTest.properties";
    private static final String SELF_SIGNED_CERT_PROP_NAME = "cert.selfsigned";
    private static final String NOT_SELF_SIGNED_CERT_PROP_NAME = "cert.notselfsigned";
    private static final String CERT_CHAIN_PROP_NAME = "cert.chain";

    private static Properties testProperties;

    @BeforeClass
    public static void loadProperties() {
        testProperties = new Properties();

        try (InputStream in = CertificateUtilExtendedTest.class.getClassLoader().getResourceAsStream(
                PROPERTIES_FILE_NAME)) {

            if (in == null) {
                fail("Test input properties file missing: " + PROPERTIES_FILE_NAME);
            }
            testProperties.load(in);

        } catch (IOException e) {
            fail("Failed to read properties file with test input: " + PROPERTIES_FILE_NAME + ", "
                    + e.getMessage());
        }
    }

    @Test
    public void testSelfSignedCertificate() {
        String certPem = testProperties.getProperty(SELF_SIGNED_CERT_PROP_NAME);
        assertTrue(CertificateUtilExtended.isSelfSignedCertificate(certPem));
    }

    @Test
    public void testCertificateNotSelfSigned() {
        String certPem = testProperties.getProperty(NOT_SELF_SIGNED_CERT_PROP_NAME);
        assertFalse(CertificateUtilExtended.isSelfSignedCertificate(certPem));
    }

    @Test
    public void testCertificateChain() {
        String certPem = testProperties.getProperty(CERT_CHAIN_PROP_NAME);
        assertFalse(CertificateUtilExtended.isSelfSignedCertificate(certPem));
    }

    @Test
    public void testInvalidCertificate() {
        assertFalse(CertificateUtilExtended.isSelfSignedCertificate("invalid certificate"));
        assertFalse(CertificateUtilExtended.isSelfSignedCertificate(null));
    }

    @Test
    public void testFromFile() throws URISyntaxException {
        URL resource = CertificateUtilExtendedTest.class.getResource("/certs/docker.com.chain.crt");
        X509Certificate[] certChain = CertificateUtilExtended.fromFile(new File(resource.toURI()));
        assertEquals(3, certChain.length);
        assertEquals(BigInteger.valueOf(26021L), certChain[0].getSerialNumber());
    }
}
