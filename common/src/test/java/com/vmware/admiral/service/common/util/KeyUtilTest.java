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

package com.vmware.admiral.service.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import static com.vmware.admiral.common.util.FileUtil.switchToUnixLineEnds;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.common.util.KeyUtil;

public class KeyUtilTest {
    private static final String PROPERTIES_FILE_NAME = "KeyUtilTest.properties";
    private static final String KEY_PAIR_PROP_NAME = "key.pair.pem";
    private static final String OPENSSH_KEY_PROP_NAME = "public.key.format.openssh";

    private static Properties testProperties = new Properties();

    @BeforeClass
    public static void loadProperties() {
        testProperties = new Properties();

        try (InputStream in = CertificateUtilTest.class.getClassLoader().getResourceAsStream(
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
    public void testToPEMFormat() {
        String keyPairPEMFormat = testProperties.getProperty(KEY_PAIR_PROP_NAME);
        KeyPair keyPair = CertificateUtil.createKeyPair(keyPairPEMFormat);

        assertEquals(
                switchToUnixLineEnds(keyPairPEMFormat),
                switchToUnixLineEnds(KeyUtil.toPEMFormat(keyPair.getPrivate())));
    }

    @Test
    public void testToPublicOpenSSHFormat() {
        String keyPairPEMFormat = testProperties.getProperty(KEY_PAIR_PROP_NAME);
        String publicKeyOpenSshFormat = testProperties.getProperty(OPENSSH_KEY_PROP_NAME);
        KeyPair keyPair = CertificateUtil.createKeyPair(keyPairPEMFormat);

        assertEquals(publicKeyOpenSshFormat,
                KeyUtil.toPublicOpenSSHFormat((RSAPublicKey) keyPair.getPublic()));
    }

    @Test
    public void testGenerateRSAKeyPair() {
        KeyPair keyPair = KeyUtil.generateRSAKeyPair();
        assertNotNull(keyPair);
        assertEquals("RSA", keyPair.getPrivate().getAlgorithm());
    }

    @Test
    public void testX509Decoder() throws CertificateException {
        @SuppressWarnings("resource")
        String pemCertificate = new Scanner(
                KeyUtilTest.class.getResourceAsStream("/certs/wildcard-docker-cert"),
                "UTF-8").useDelimiter("\\A").next();
        X509Certificate certificate = KeyUtil.decodeCertificate(pemCertificate);
        assertNotNull(certificate);
        Assert.assertTrue(certificate.getSubjectDN().getName().contains("CN=*.docker.com"));
        for (List<?> entry : certificate.getSubjectAlternativeNames()) {
            entry.contains("docker.com");
        }
        assertEquals("SHA256withRSA", certificate.getSigAlgName());
        assertEquals("X.509", certificate.getType());

    }

}
