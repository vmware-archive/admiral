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

import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE;
import static com.vmware.admiral.common.util.ServerX509TrustManager.JAVAX_NET_SSL_TRUST_STORE_PASSWORD;

import java.io.File;
import java.security.Security;
import java.util.Properties;

import com.vmware.photon.controller.model.security.util.EncryptionUtils;

public class SecurityUtils {

    public static final String SECURITY_PROPERTIES = "security.properties";

    public static final String CERTIFICATE_STORE_FILE = "certificate.store.file";
    public static final String CERTIFICATE_STORE_PASSWORD = "certificate.store.password";

    /**
     * Unless explicitly enabled, disable TLS v1.0 and v1.1 by default <br />
     * - TLS v1.0 due to the BEAST vulnerability
     * (see https://en.wikipedia.org/wiki/Transport_Layer_Security#BEAST_attack) <br />
     * - TLS v1.1 Bug 2573012
     */
    public static void ensureTlsDisabledAlgorithms() {
        boolean enableTlsv1 = Boolean.getBoolean("com.vmware.admiral.enable.tlsv1");
        if (!enableTlsv1) {
            String disabledAlgorithms = Security.getProperty("jdk.tls.disabledAlgorithms");
            disabledAlgorithms = "TLSv1, " + disabledAlgorithms;
            Security.setProperty("jdk.tls.disabledAlgorithms", disabledAlgorithms);
        }
        boolean enableTlsv1_1 = Boolean.getBoolean("com.vmware.admiral.enable.tlsv1.1");
        if (!enableTlsv1_1) {
            String disabledAlgorithms = Security.getProperty("jdk.tls.disabledAlgorithms");
            disabledAlgorithms = "TLSv1.1, " + disabledAlgorithms;
            Security.setProperty("jdk.tls.disabledAlgorithms", disabledAlgorithms);
        }
    }

    /**
     * Enable the configuration of the trustStore settings (file and password) via a provided
     * security properties file instead of the standard system properties. The rationale behind that
     * is to provide a mechanism to avoid showing the plain text password value of the trustStore
     * when analyzing the OS running processes (e.g. ps -ax | grep java).
     */
    public static void ensureTrustStoreSettings() {
        String trustStore = System.getProperty(JAVAX_NET_SSL_TRUST_STORE);
        if (trustStore != null) {
            // trustStore initialized via the standard system properties - nothing to do
            return;
        }

        String securityProperties = System.getProperty(SECURITY_PROPERTIES);
        if (securityProperties != null) {
            // try to initialize the trustStore via the security properties file...
            File f = new File(securityProperties);
            if (f.exists()) {
                Properties properties = FileUtil.getProperties(securityProperties, false);

                String trustStoreFile = properties.getProperty(CERTIFICATE_STORE_FILE);
                if (trustStoreFile != null) {
                    System.setProperty(JAVAX_NET_SSL_TRUST_STORE, trustStoreFile);
                }

                String trustStorePassword = properties.getProperty(CERTIFICATE_STORE_PASSWORD);
                if (trustStorePassword != null) {
                    System.setProperty(JAVAX_NET_SSL_TRUST_STORE_PASSWORD,
                            EncryptionUtils.decrypt(trustStorePassword));
                }
            } else {
                throw new IllegalArgumentException(
                        "Unable to load security properties from '" + securityProperties + "'!");
            }
        }
    }

}
