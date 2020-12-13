/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import static com.vmware.admiral.common.util.SecurityUtils.SECURITY_PROPERTIES;
import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE;
import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE_PASSWORD;
import static com.vmware.photon.controller.model.security.util.EncryptionUtils.ENCRYPTION_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URISyntaxException;
import java.security.Security;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.photon.controller.model.security.util.EncryptionUtils;

public class SecurityUtilsTest {

    private static String originalTrustStoreProp;
    private static String originalTrustStorePasswordProp;

    @BeforeClass
    public static void setUp() throws Throwable {
        originalTrustStoreProp = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        originalTrustStorePasswordProp = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
    }

    @AfterClass
    public static void tearDown() throws Throwable {
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE,
                originalTrustStoreProp == null ? "" : originalTrustStoreProp);
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD,
                originalTrustStorePasswordProp == null ? "" : originalTrustStorePasswordProp);
    }

    @Test
    public void testEnsureTlsDisabledAlgorithms() {

        // default settings

        Security.setProperty("jdk.tls.disabledAlgorithms", "");

        SecurityUtils.ensureTlsDisabledAlgorithms();

        String disabledAlgorithms = Security.getProperty("jdk.tls.disabledAlgorithms");
        assertNotNull(disabledAlgorithms);
        assertTrue(disabledAlgorithms.contains("TLSv1,"));
        assertTrue(disabledAlgorithms.contains("TLSv1.1,"));

        // force TLSv1.0

        Security.setProperty("jdk.tls.disabledAlgorithms", "");
        System.setProperty("com.vmware.admiral.enable.tlsv1", Boolean.TRUE.toString());
        System.setProperty("com.vmware.admiral.enable.tlsv1.1", Boolean.TRUE.toString());

        SecurityUtils.ensureTlsDisabledAlgorithms();

        disabledAlgorithms = Security.getProperty("jdk.tls.disabledAlgorithms");
        assertNotNull(disabledAlgorithms);
        assertFalse(disabledAlgorithms.contains("TLSv1,"));
        assertFalse(disabledAlgorithms.contains("TLSv1.1,"));

        System.setProperty("com.vmware.admiral.enable.tlsv1", Boolean.FALSE.toString());
        System.setProperty("com.vmware.admiral.enable.tlsv1.1", Boolean.FALSE.toString());
    }

    @Test
    public void testEnsureTrustStoreSettings() throws URISyntaxException {

        // default settings

        SecurityUtils.ensureTrustStoreSettings();

        String trustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        assertTrue(trustStore == null || "".equals(trustStore));
        String trustStorePassword = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
        assertTrue(trustStorePassword == null || "".equals(trustStorePassword));
        String securityProperties = System.getProperty(SECURITY_PROPERTIES);
        assertNull(securityProperties);

        // set JAVAX_NET_SSL_TRUST_STORE & JAVAX_NET_SSL_TRUST_STORE_PASSWORD

        System.setProperty(JAVAX_NET_SSL_TRUST_STORE, "/foo/bar/trustStore");
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD, "secret");
        System.clearProperty(SECURITY_PROPERTIES);

        SecurityUtils.ensureTrustStoreSettings();

        trustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        assertNotNull(trustStore);
        assertEquals("/foo/bar/trustStore", trustStore);
        trustStorePassword = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
        assertNotNull(trustStorePassword);
        assertEquals("secret", trustStorePassword);
        securityProperties = System.getProperty(SECURITY_PROPERTIES);
        assertNull(securityProperties);

        // set JAVAX_NET_SSL_TRUST_STORE & JAVAX_NET_SSL_TRUST_STORE_PASSWORD
        // and SECURITY_PROPERTIES... which is ignored

        System.setProperty(JAVAX_NET_SSL_TRUST_STORE, "/foo/bar/trustStore");
        System.setProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD, "secret");
        System.setProperty(SECURITY_PROPERTIES, "/foo/bar/ignored");

        SecurityUtils.ensureTrustStoreSettings();

        trustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        assertNotNull(trustStore);
        assertEquals("/foo/bar/trustStore", trustStore);
        trustStorePassword = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
        assertNotNull(trustStorePassword);
        assertEquals("secret", trustStorePassword);
        securityProperties = System.getProperty(SECURITY_PROPERTIES);
        assertNotNull(securityProperties);
        assertEquals("/foo/bar/ignored", securityProperties);

        // set SECURITY_PROPERTIES with plain-text password

        System.clearProperty(JAVAX_NET_SSL_TRUST_STORE);
        System.clearProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
        String securityPropertiesValue = SecurityUtilsTest.class
                .getResource("/security1.properties").toURI().getPath();
        System.setProperty(SECURITY_PROPERTIES, securityPropertiesValue);

        SecurityUtils.ensureTrustStoreSettings();

        trustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        assertNotNull(trustStore);
        assertEquals("/foo/bar/trustStore1", trustStore);
        trustStorePassword = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
        assertNotNull(trustStorePassword);
        assertEquals("secret1", trustStorePassword);
        securityProperties = System.getProperty(SECURITY_PROPERTIES);
        assertNotNull(securityProperties);
        assertEquals(securityPropertiesValue, securityProperties);

        // set SECURITY_PROPERTIES with encrypted password

        System.clearProperty(JAVAX_NET_SSL_TRUST_STORE);
        System.clearProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
        securityPropertiesValue = SecurityUtilsTest.class
                .getResource("/security2.properties").toURI().getPath();
        System.setProperty(SECURITY_PROPERTIES, securityPropertiesValue);

        String encryptionKey = SecurityUtilsTest.class
                .getResource("/encryption.key").toURI().getPath();
        System.setProperty(ENCRYPTION_KEY, encryptionKey);
        EncryptionUtils.initEncryptionService();

        SecurityUtils.ensureTrustStoreSettings();

        trustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        assertNotNull(trustStore);
        assertEquals("/foo/bar/trustStore2", trustStore);
        trustStorePassword = System.getProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
        assertNotNull(trustStorePassword);
        assertEquals("secret2", trustStorePassword);
        securityProperties = System.getProperty(SECURITY_PROPERTIES);
        assertNotNull(securityProperties);
        assertEquals(securityPropertiesValue, securityProperties);

        System.clearProperty(JAVAX_NET_SSL_TRUST_STORE);
        System.clearProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD);
        System.clearProperty(SECURITY_PROPERTIES);
    }

}
