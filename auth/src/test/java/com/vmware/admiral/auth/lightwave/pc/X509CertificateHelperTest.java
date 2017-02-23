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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.junit.Assert;
import org.junit.Test;

/**
 * Test for {@link X509CertificateHelper}.
 */
public class X509CertificateHelperTest {
    @Test
    public void generateX509CertificateTestSuccess() throws Throwable {
        X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
        X509Certificate cert = x509CertificateHelper.generateX509Certificate();

        assertNotNull(cert);
        assertEquals("SHA1withRSA", cert.getSigAlgName());
    }

    @Test(expected = NoSuchAlgorithmException.class)
    public void generateX509CertificateTestFailure() throws Throwable {
        X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
        x509CertificateHelper.generateX509Certificate("InvalidAlg", "SHA1withRSA");
    }

    @Test
    public void x509CertificateToBase64Test() throws Throwable {
        X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
        X509Certificate cert = x509CertificateHelper.generateX509Certificate();
        String base64Cert = x509CertificateHelper.x509CertificateToBase64(cert);

        assertNotNull(base64Cert);
        Assert.assertTrue(base64Cert.length() > 0);
    }

    @Test
    public void getX509CertificateFromBase64TestSuccess() throws Throwable {
        X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
        X509Certificate cert = x509CertificateHelper.generateX509Certificate();
        String base64Cert = x509CertificateHelper.x509CertificateToBase64(cert);

        X509Certificate convertedCert = x509CertificateHelper
                .getX509CertificateFromBase64(base64Cert);

        assertEquals(cert, convertedCert);
    }

    @Test(expected = CertificateException.class)
    public void getX509CertificateFromBase64TestFailure() throws Throwable {
        X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
        x509CertificateHelper.getX509CertificateFromBase64("invalid cert");
    }
}
