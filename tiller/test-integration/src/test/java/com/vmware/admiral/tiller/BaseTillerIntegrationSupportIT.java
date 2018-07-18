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

package com.vmware.admiral.tiller;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.Before;
import org.microbean.helm.Tiller;

import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.admiral.test.integration.TestPropertiesUtil;
import com.vmware.admiral.tiller.client.TillerConfig;
import com.vmware.admiral.tiller.client.TillerConfig.Builder;
import com.vmware.admiral.tiller.client.TillerConfig.TillerConnectionType;

public class BaseTillerIntegrationSupportIT extends BaseIntegrationSupportIT {

    protected static final String TEST_PROP_K8S_API_URL = "test.k8s.api.url";
    protected static final String TEST_PROP_K8S_CA_FILE = "test.k8s.ca.file";
    protected static final String TEST_PROP_K8S_CERT_FILE = "test.k8s.cert.file";
    protected static final String TEST_PROP_K8S_KEY_FILE = "test.k8s.key.file";
    protected static final String TEST_PROP_K8S_KEY_PASSPHRASE = "test.k8s.key.passphrase";
    protected static final String TEST_PROP_TILLER_CA_FILE = "test.tiller.ca.file";
    protected static final String TEST_PROP_TILLER_CERT_FILE = "test.tiller.cert.file";
    protected static final String TEST_PROP_TILLER_KEY_FILE = "test.tiller.key.file";
    protected static final String TEST_PROP_TILLER_KEY_PASSPHRASE = "test.tiller.key.passphrase";
    protected static final String TEST_PROP_TILLER_INSECURE_NAMESPACE = "test.tiller.namespace.insecure";
    protected static final String TEST_PROP_TILLER_TLS_NAMESPACE = "test.tiller.namespace.tls";

    protected String k8sApiUrl;
    protected String k8sCertificateAuthority;
    protected String k8sClientCertificate;
    protected String k8sClientKey;
    protected String k8sClientKeyPassphrase;
    protected String tillerCertificateAuthority;
    protected String tillerClientCertificate;
    protected String tillerClientKey;
    protected String tillerClientKeyPassphrase;

    protected String tillerInsecureNamespace = Tiller.DEFAULT_NAMESPACE;
    protected String tillerTlsNamespace = "tiller-world";

    @Before
    public void setupTestProperties() throws Throwable {
        this.k8sApiUrl = TestPropertiesUtil.getTestRequiredProp(TEST_PROP_K8S_API_URL);
        this.k8sClientKeyPassphrase = TestPropertiesUtil
                .getSystemOrTestProp(TEST_PROP_K8S_KEY_PASSPHRASE);
        this.tillerClientKeyPassphrase = TestPropertiesUtil
                .getSystemOrTestProp(TEST_PROP_TILLER_KEY_PASSPHRASE);
        this.tillerInsecureNamespace = TestPropertiesUtil
                .getSystemOrTestProp(TEST_PROP_TILLER_INSECURE_NAMESPACE, "kube-system");
        this.tillerTlsNamespace = TestPropertiesUtil
                .getSystemOrTestProp(TEST_PROP_TILLER_TLS_NAMESPACE, "kube-system");

        this.k8sCertificateAuthority = stripCertHeaderAndFooter(
                getFileContentsFromProperty(TEST_PROP_K8S_CA_FILE));
        this.k8sClientCertificate = stripCertHeaderAndFooter(
                getFileContentsFromProperty(TEST_PROP_K8S_CERT_FILE));
        this.k8sClientKey = getFileContentsFromProperty(TEST_PROP_K8S_KEY_FILE);

        this.tillerCertificateAuthority = getFileContentsFromProperty(TEST_PROP_TILLER_CA_FILE);
        this.tillerClientCertificate = getFileContentsFromProperty(TEST_PROP_TILLER_CERT_FILE);
        this.tillerClientKey = getFileContentsFromProperty(TEST_PROP_TILLER_KEY_FILE);
    }

    private String getFileContentsFromProperty(String propertyName) throws IOException {
        String prop = TestPropertiesUtil.getSystemOrTestProp(propertyName);
        if (prop == null || prop.isEmpty()) {
            return null;
        }
        return readFile(prop);
    }

    protected TillerConfig buildPlaintextTillerConfig() {
        return buildTillerConfig(false);
    }

    protected TillerConfig buildTlsTillerConfig() {
        return buildTillerConfig(true);
    }

    private TillerConfig buildTillerConfig(boolean tlsEnabled) {
        Builder configBuilder = TillerConfig.builder()
                .setK8sApiUrl(k8sApiUrl)
                .setK8sCertificateAuthority(k8sCertificateAuthority)
                .setK8sClientCertificate(k8sClientCertificate)
                .setK8sClientKey(k8sClientKey)
                .setK8sClientKeyPassphrase(k8sClientKeyPassphrase);

        if (tlsEnabled) {
            configBuilder
                    .setTillerNamespace(tillerTlsNamespace)
                    .setTillerConnectionType(TillerConnectionType.TLS)
                    .setTillerCertificateAuthority(tillerCertificateAuthority)
                    .setTillerClientCertificate(tillerClientCertificate)
                    .setTillerClientKey(tillerClientKey)
                    .setTillerClientKeyPassphrase(tillerClientKeyPassphrase);
        } else {
            configBuilder.setTillerNamespace(tillerInsecureNamespace)
                    .setTillerConnectionType(TillerConnectionType.PLAIN_TEXT);
        }

        return configBuilder.build();
    }

    private static String readFile(String filename) throws IOException {
        return new String(Files.readAllBytes(new File(filename).toPath()));
    }

    private static String stripCertHeaderAndFooter(String certData) {
        return certData.replaceAll("-+[A-Z ]+-+\\s", "");
    }
}
