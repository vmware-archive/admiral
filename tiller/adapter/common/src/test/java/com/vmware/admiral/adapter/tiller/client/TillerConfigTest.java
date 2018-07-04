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

package com.vmware.admiral.adapter.tiller.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Random;

import org.junit.Test;

import com.vmware.admiral.adapter.tiller.client.TillerConfig.TillerConnectionType;

public class TillerConfigTest {

    @Test
    public void testBuildTillerConfig() {
        final String k8sApiUrl = "k8s-api-url";
        final String k8sCertificateAuthority = "k8s-ca-cert";
        final String k8sClientCertificate = "k8s-client-cert";
        final String k8sClientKey = "k8s-client-key";
        final String k8sClientKeyPassphrase = "k8s-client-key-passphrase";
        final String tillerNamespace = "tiller-namespace";
        final Integer tillerPort = new Random().nextInt(65536);
        final Map<String, String> tillerLabels = Collections.singletonMap("some-key", "some-value");
        final String tillerCertificateAuthority = "tiller-ca-cert";
        final String tillerClientCertificate = "tiller-client-cert";
        final String tillerClientKey = "tiller-client-key";
        final String tillerClientKeyPassphrase = "tiller-client-key-passphrase";
        final TillerConnectionType tillerConnectionType = TillerConnectionType.PLAIN_TEXT;

        TillerConfig.Builder builder = TillerConfig.builder()

                .setK8sApiUrl(k8sApiUrl)
                .setK8sCertificateAuthority(k8sCertificateAuthority)
                .setK8sClientCertificate(k8sClientCertificate)
                .setK8sClientKey(k8sClientKey)
                .setK8sClientKeyPassphrase(k8sClientKeyPassphrase)

                .setTillerNamespace(tillerNamespace)
                .setTillerPort(tillerPort)
                .setTillerLabels(tillerLabels)
                .setTillerCertificateAuthority(tillerCertificateAuthority)
                .setTillerClientCertificate(tillerClientCertificate)
                .setTillerClientKey(tillerClientKey)
                .setTillerClientKeyPassphrase(tillerClientKeyPassphrase);

        TillerConfig config = builder.build();
        assertNotNull("config must not be null", config);

        try {
            builder.build();
        } catch (IllegalStateException ex) {
            assertEquals(TillerConfig.Builder.CONFIG_IS_ALREADY_BUILT_ERROR_MESSAGE,
                    ex.getMessage());
        }

        try {
            builder.setK8sApiUrl("no-such-url");
        } catch (IllegalStateException ex) {
            assertEquals(TillerConfig.Builder.CONFIG_IS_ALREADY_BUILT_ERROR_MESSAGE,
                    ex.getMessage());
        }

        assertEquals("k8sApiUrl",
                k8sApiUrl,
                config.getK8sApiUrl());

        assertEquals("k8sCertificateAuthority",
                k8sCertificateAuthority,
                config.getK8sCertificateAuthority());

        assertEquals("k8sClientCertificate",
                k8sClientCertificate,
                config.getK8sClientCertificate());

        assertEquals("k8sClientKey",
                k8sClientKey,
                config.getK8sClientKey());

        assertEquals("k8sClientKeyPassphrase",
                k8sClientKeyPassphrase,
                config.getK8sClientKeyPassphrase());

        assertEquals("tillerNamespace",
                tillerNamespace,
                config.getTillerNamespace());

        assertEquals("tillerPort",
                tillerPort,
                config.getTillerPort());

        assertEquals("tillerCertificateAuthority",
                tillerCertificateAuthority,
                config.getTillerCertificateAuthority());

        assertEquals("tillerClientCertificate",
                tillerClientCertificate,
                config.getTillerClientCertificate());

        assertEquals("tillerClientKey",
                tillerClientKey,
                config.getTillerClientKey());

        assertEquals("tillerClientKeyPassphrase",
                tillerClientKeyPassphrase,
                config.getTillerClientKeyPassphrase());

        assertEquals("tillerConnectionType",
                tillerConnectionType,
                config.getTillerConnectionType());

        assertNotNull("tillerLabels", config.getTillerLabels());
        assertEquals("tillerLabels.size", tillerLabels.size(), config.getTillerLabels().size());
        tillerLabels.forEach((key, value) -> {
            assertEquals("tillerLabels[" + key + "]", value, config.getTillerLabels().get(key));
        });
    }
}
