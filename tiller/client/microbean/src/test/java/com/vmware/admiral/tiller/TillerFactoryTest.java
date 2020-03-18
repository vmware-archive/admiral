/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.tiller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.microbean.helm.Tiller;

import com.vmware.admiral.tiller.client.TillerConfig;
import com.vmware.admiral.tiller.client.TillerConfig.Builder;
import com.vmware.admiral.tiller.client.TillerConfig.TillerConnectionType;

public class TillerFactoryTest {

    private static final String TEST_FILE_CA_CERT = "certs-and-keys/test.ca.cert.pem";
    private static final String TEST_FILE_CLIENT_CERT = "certs-and-keys/test.client.cert.pem";
    private static final String TEST_FILE_CLIENT_KEY = "certs-and-keys/test.client.key.pkcs8.pem";

    private static final int TILLER_PORT = Tiller.DEFAULT_PORT;
    private static final String TILLER_NAMESPACE = "tiller-namespace";
    private static final String TILLER_LABELS_KEY = "test-app";
    private static final String TILLER_LABELS_VALUE = "test-tiller";
    private static final Map<String, String> TILLER_LABELS = Collections
            .singletonMap(TILLER_LABELS_KEY, TILLER_LABELS_VALUE);
    private static final String TILLER_POD_NAME = "almighty-tiller";

    @Rule
    public Timeout timeout = new Timeout(10, TimeUnit.SECONDS);

    /** See https://github.com/fabric8io/mockwebserver. */
    @Rule
    public KubernetesServer kubernetesServer = new KubernetesServer(true);

    private Logger logger = Logger.getLogger(TillerFactoryTest.class.getName());

    @Before
    public void setUp() {
        setupTillerPodOnMockServer(kubernetesServer);
    }

    @Test
    public void testBuildK8sConfig() throws Throwable {
        assertNull(TillerFactory.buildK8sConfig(null));

        TillerConfig mockConfig = TillerConfig.builder()
                .setK8sApiUrl("https://some-api-url:8443/")
                .setK8sCertificateAuthority(
                        stripCertHeaderAndFooter(readTestFile(TEST_FILE_CA_CERT)))
                .setK8sClientCertificate(
                        stripCertHeaderAndFooter(readTestFile(TEST_FILE_CLIENT_CERT)))
                .setK8sClientKey(
                        readTestFile(TEST_FILE_CLIENT_KEY))
                .build();

        Config builtConfig = TillerFactory.buildK8sConfig(mockConfig);
        assertEquals(mockConfig.getK8sApiUrl(), builtConfig.getMasterUrl());
        assertEquals(mockConfig.getK8sCertificateAuthority(), builtConfig.getCaCertData());
        assertEquals(mockConfig.getK8sClientCertificate(), builtConfig.getClientCertData());
        assertEquals(mockConfig.getK8sClientKey(), builtConfig.getClientKeyData());
        assertEquals(mockConfig.getK8sClientKeyPassphrase(), builtConfig.getClientKeyPassphrase());
    }

    @Test
    public void testStringToStream() {
        assertNull(TillerFactory.stringToStream(null));

        final String testString = "test-string";
        InputStream stream = TillerFactory.stringToStream(testString);
        try (Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            // \A matches String beginning, i.e. this will match the whole stream
            String readString = scanner.useDelimiter("\\A").next();
            assertEquals(testString, readString);
        }
    }

    @Test
    public void testNewTiller() throws Throwable {
        for (TillerConnectionType connectionType : TillerConnectionType.values()) {
            Tiller tiller = TillerFactory.newTiller(buildTillerConfig(connectionType));
            assertNotNull(tiller);
            assertNotNull(tiller.getHealthStub());
            tiller.close();
        }
    }

    private void setupTillerPodOnMockServer(KubernetesServer kubernetesServer) {
        Pod pod = new PodBuilder()
                // add pod metadata to pod
                .withNewMetadata()
                .withName(TILLER_POD_NAME)
                .withLabels(TILLER_LABELS)
                .endMetadata()
                // add pod status to pod
                .withNewStatus()
                // add ready condition to pod status
                .addNewCondition()
                .withType("Ready")
                .withStatus("True")
                .endCondition()
                .endStatus()
                .build();

        // In CRUD mocking mode it is not possible to create the pod with labels (the ones in the
        // Pod itself are ignored), so we need to set the KubernetesServer in expectations mode and
        // return the pod manually
        String tillerSearchPath = String.format("/api/v1/namespaces/%s/pods?labelSelector=%s%%3D%s",
                TILLER_NAMESPACE, TILLER_LABELS_KEY, TILLER_LABELS_VALUE);
        logger.log(Level.INFO, String.format("registering expected path %s", tillerSearchPath));
        kubernetesServer
                .expect().withPath(tillerSearchPath)
                .andReturn(HttpResponseStatus.OK.code(),
                        new PodListBuilder().withItems(pod).build())
                .always();

        NamespacedKubernetesClient client = kubernetesServer.getClient();

        PodList list = client.pods().inNamespace(TILLER_NAMESPACE).withLabels(TILLER_LABELS).list();
        assertNotNull(list);
        assertEquals(1, list.getItems().size());
    }

    private TillerConfig buildTillerConfig(TillerConnectionType connectionType) throws Throwable {
        final String testCa = readTestFile(TEST_FILE_CA_CERT);
        final String testCert = readTestFile(TEST_FILE_CLIENT_CERT);
        final String testKey = readTestFile(TEST_FILE_CLIENT_KEY);

        Builder configBuilder = TillerConfig.builder()
                .setK8sApiUrl(kubernetesServer.getMockServer().url("/").toString())
                .setK8sTrustCertificateAuthority(true)

                // There is no actual pod that will answer us so
                // any valid certificates here will do.
                .setTillerNamespace(TILLER_NAMESPACE)
                .setTillerLabels(TILLER_LABELS)
                .setTillerPort(TILLER_PORT)
                .setTillerConnectionType(connectionType);

        switch (connectionType) {
        case TLS_VERIFY:
            configBuilder.setTillerCertificateAuthority(testCa)
                    .setTillerClientCertificate(testCert)
                    .setTillerClientKey(testKey);
            break;
        case TLS:
            configBuilder.setTillerClientCertificate(testCert)
                    .setTillerClientKey(testKey);
            break;
        default:
            break;
        }

        return configBuilder.build();
    }

    private String readTestFile(String filename) throws Throwable {
        URI uri = getClass().getClassLoader().getResource(filename).toURI();
        Path path = Paths.get(uri);
        return new String(Files.readAllBytes(path));
    }

    private static String stripCertHeaderAndFooter(String certData) {
        return certData.replaceAll("-+[A-Z ]+-+\\s", "");
    }

}
