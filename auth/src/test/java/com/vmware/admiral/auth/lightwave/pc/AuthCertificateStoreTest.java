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
import static org.junit.Assert.fail;

import java.security.cert.X509Certificate;

import org.junit.Test;

/**
 * Test AuthCertificateStoreTest.
 */
public class AuthCertificateStoreTest {

    @Test
    public void testGetKeyStore() throws AuthException {
        AuthCertificateStore authCertificateStore = new AuthCertificateStore();
        assertNotNull(authCertificateStore.getKeyStore());
    }

    @Test
    public void testSetCertificateEntryBase64() throws Exception {
        AuthCertificateStore authCertificateStore = new AuthCertificateStore();

        try {
            authCertificateStore.setCertificateEntry("invalid", "");
            fail("exception expected");
        } catch (AuthException e) {
            assertEquals("Failed to create X509Certificate for the passed base64 certificate",
                    e.getMessage());
        }

        assertEquals(0, authCertificateStore.getKeyStore().size());

        X509CertificateHelper helper = new X509CertificateHelper();
        X509Certificate x509Certificate = helper.generateX509Certificate();
        String base64Certificate = helper.x509CertificateToBase64(x509Certificate);
        authCertificateStore.setCertificateEntry("new cert one", base64Certificate);
        assertNotNull(authCertificateStore.getKeyStore());
        assertEquals(1, authCertificateStore.getKeyStore().size());
    }

    @Test
    public void testSetCertificateEntryX509() throws Exception {
        AuthCertificateStore authCertificateStore = new AuthCertificateStore();
        assertEquals(0, authCertificateStore.getKeyStore().size());

        X509CertificateHelper helper = new X509CertificateHelper();
        X509Certificate x509Certificate = helper.generateX509Certificate();
        authCertificateStore.setCertificateEntry("new cert two", x509Certificate);
        assertNotNull(authCertificateStore.getKeyStore());
        assertEquals(1, authCertificateStore.getKeyStore().size());
    }

}
