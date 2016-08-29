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

package com.vmware.admiral.host;

import static java.lang.Boolean.TRUE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.host.HostInitAdapterServiceConfig.FIELD_NAME_START_MOCK_HOST_ADAPTER_INSTANCE;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.doGet;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.doRestrictedOperation;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.login;
import static com.vmware.admiral.service.common.AuthBootstrapService.waitForInitConfig;
import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_ASSIGNMENT;
import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_PREFIX;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import javax.net.ssl.SSLSocketFactory;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.service.common.AuthBootstrapService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceRequestListener;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Base class for cluster tests.
 */
public abstract class ManagementHostClusterBaseTestCase extends ManagementHostBaseTest {

    private static final String LOCAL_USERS_FILE = getResourceFilePath(
            "/local-users-encrypted.json");
    private static final String TRUST_STORE_FILE = getResourceFilePath(
            "/certs/trusted_certificates.jks");
    private static final String KEY_FILE = getResourceFilePath("/certs/server_private.pem");
    private static final String CERTIFICATE_FILE = getResourceFilePath("/certs/server_public.crt");
    private static final String ENCRYPTION_KEY_FILE = getResourceFilePath("/encryption.key");

    protected static final String USERNAME = "administrator@admiral.com";
    protected static final String PASSWORD = "secret";

    private static final String NODE_GROUPS = ServiceUriPaths.DEFAULT_NODE_GROUP;

    private static final long TIMEOUT_FOR_WAIT_CONDITION = 60 * 1000;
    private static final long DELAY_BETWEEN_RETRIES_IN_MILISEC = 3000;

    @Rule
    public TemporaryFolder test = new TemporaryFolder();

    protected static final String LOCALHOST = "https://127.0.0.1:";

    static {
        System.setProperty("encryption.key.file", ENCRYPTION_KEY_FILE);
        System.setProperty("javax.net.ssl.trustStore", TRUST_STORE_FILE);
        System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
    }

    protected ManagementHost setUpHost(int port, URI sandboxUri, List<String> peers)
            throws Throwable {

        String sandboxPath;
        if (sandboxUri != null) {
            sandboxPath = sandboxUri.toString().replace("file:", "");
            sandboxPath = sandboxPath.substring(0, sandboxPath.lastIndexOf("/"));
        } else {
            TemporaryFolder sandbox = new TemporaryFolder(test.getRoot());
            sandbox.create();
            sandboxPath = sandbox.getRoot().toPath().toString();
        }

        String peerNodes = String.join(",", peers);

        ManagementHost host = ManagementHostBaseTest.createManagementHost(new String[] {
                ARGUMENT_PREFIX
                        + FIELD_NAME_START_MOCK_HOST_ADAPTER_INSTANCE
                        + ARGUMENT_ASSIGNMENT
                        + TRUE.toString(),
                ARGUMENT_PREFIX
                        + AuthBootstrapService.LOCAL_USERS_FILE
                        + ARGUMENT_ASSIGNMENT
                        + LOCAL_USERS_FILE,
                ARGUMENT_PREFIX
                        + "bindAddress"
                        + ARGUMENT_ASSIGNMENT
                        + "0.0.0.0",
                ARGUMENT_PREFIX
                        + "peerNodes"
                        + ARGUMENT_ASSIGNMENT
                        + peerNodes,
                ARGUMENT_PREFIX
                        + "sandbox"
                        + ARGUMENT_ASSIGNMENT
                        + sandboxPath,
                ARGUMENT_PREFIX
                        + "keyFile"
                        + ARGUMENT_ASSIGNMENT
                        + KEY_FILE,
                ARGUMENT_PREFIX
                        + "certificateFile"
                        + ARGUMENT_ASSIGNMENT
                        + CERTIFICATE_FILE,
                ARGUMENT_PREFIX
                        + "securePort"
                        + ARGUMENT_ASSIGNMENT
                        + port,
                ARGUMENT_PREFIX
                        + "port"
                        + ARGUMENT_ASSIGNMENT
                        + "-1" },
                true);

        assertTrue(host.isAuthorizationEnabled());
        assertNotNull(host.getAuthorizationServiceUri());

        return host;
    }

    protected void tearDownHost(ManagementHost... hosts) {
        Arrays.stream(hosts).forEach(host -> stopHost(host));
    }

    protected void stopHost(ManagementHost host) {

        if (host == null) {
            return;
        }

        String hostname = host.getUri().toString();
        System.out.println("Stopping host '" + hostname + "'...");

        try {
            ServiceRequestListener secureListener = host.getSecureListener();
            host.stop();
            secureListener.stop();

            waitWhilePortIsListening(secureListener.getPort());

            System.out.println("Host '" + hostname + "' stopped.");
        } catch (Exception e) {
            throw new RuntimeException("Exception stopping host!", e);
        }
    }

    protected static void waitWhilePortIsListening(int port) throws InterruptedException {
        SSLSocketFactory factory = ManagementHostAuthUsersTest.getUnsecuredSSLSocketFactory();
        boolean portListening = true;
        while (portListening) {
            try (Socket s = factory.createSocket((String) null, port)) {
                System.out.println("Wait while port '" + port + "' is listening...");
            } catch (Exception e) {
                portListening = false;
            } finally {
                Thread.sleep(2000);
            }
        }
    }

    protected ManagementHost startHost(ManagementHost host, URI sandboxUri,
            List<String> peers) throws Throwable {

        String hostname = host.getUri().toString() + ":" + host.getSecurePort();
        System.out.println("Starting host '" + hostname + "'...");

        host = setUpHost(host.getSecurePort(), sandboxUri, peers);

        waitForInitConfig(host, host.localUsers);

        System.out.println("Sleep for a while, until the host starts...");
        Thread.sleep(4000);
        System.out.println("Host '" + hostname + "' started.");

        return host;
    }

    protected static void assertClusterWithToken(String token, ManagementHost... hosts)
            throws Exception {
        assertNotNull(token);
        Map<String, String> headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, token);
        assertNodes(headers, hosts);
    }

    protected static void assertClusterFromNodes(ManagementHost... hosts) throws Exception {
        for (ManagementHost host : hosts) {
            String token = login(host, USERNAME, PASSWORD);
            assertNotNull(token);
            Map<String, String> headers = new HashMap<>();
            headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, token);
            assertNodes(headers, hosts);
        }
    }

    private static void assertNodes(Map<String, String> headers, ManagementHost... hosts)
            throws Exception {
        for (ManagementHost host : hosts) {

            // Assert restricted operation access

            waitFor(new Condition() {

                @Override
                public boolean isReady() {
                    try {
                        assertEquals(HttpURLConnection.HTTP_OK,
                                doRestrictedOperation(host, headers));
                    } catch (Throwable e) {
                        return false;
                    }
                    return true;
                }

                @Override
                public String getDescription() {
                    return String.format("Restricted operation to host: [%s] and headers: [%s] ",
                            host, headers);
                }
            });

            // Assert node groups info

            URI uri = UriUtils.buildUri(host, NODE_GROUPS);
            // SimpleEntry<Integer, String> result = doGet(uri, headers);
            waitFor(new Condition() {

                @Override
                public boolean isReady() {
                    try {
                        SimpleEntry<Integer, String> response = doGet(uri, headers);
                        assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());
                        String body = switchToUnixLineEnds(response.getValue());

                        for (ManagementHost host2 : hosts) {
                            assertTrue("Host " + host2.getUri() + " should be present!",
                                    body.contains("\"groupReference\": \"" + host2.getUri()
                                            + "/core/node-groups/default\",\n      \"status\": \"AVAILABLE\""));
                        }
                    } catch (Throwable e) {
                        host.log(Level.WARNING, "Request to [%s] failed with: [%s].", NODE_GROUPS,
                                e.getMessage());
                        return false;
                    }

                    return true;
                }

                @Override
                public String getDescription() {
                    return String.format("Sending GET Request to: [%s] with headers: [%s]", uri,
                            headers);
                }
            });

        }
    }

    protected interface Condition {

        public boolean isReady();

        public String getDescription();

    }

    private static void waitFor(Condition condition) throws InterruptedException, TimeoutException {
        long start = System.currentTimeMillis();
        long end = start + TIMEOUT_FOR_WAIT_CONDITION; //Wait 1 minute.

        while (!condition.isReady()) {
            Thread.sleep(DELAY_BETWEEN_RETRIES_IN_MILISEC);
            if (System.currentTimeMillis() > end) {
                throw new TimeoutException(
                        String.format("Timeout waiting for: [%s]", condition.getDescription()));
            }
        }
    }

    protected void assertContainerDescription(ManagementHost host, Map<String, String> headers)
            throws IOException {

        URI uri = UriUtils.buildUri(host, ContainerDescriptionService.FACTORY_LINK);

        SimpleEntry<Integer, String> result = doGet(uri, headers);

        assertEquals("Operation should be OK!", HttpURLConnection.HTTP_OK, (int) result.getKey());

        String body = result.getValue();

        assertNotNull(body);

        DocumentDescriptionResponse response = Utils.fromJson(Utils.toJson(body),
                DocumentDescriptionResponse.class);

        assertNotNull(response);
        assertNotNull(response.getDocumentLinks());

        assertTrue("Document with selfLink 'test-container-desc' must exists in response!",
                response.getDocumentLinks().contains(UriUtilsExtended
                        .buildUriPath(ContainerDescriptionService.FACTORY_LINK,
                                TestAuthServiceDocumentHelper.TEST_CONTAINER_DESC_SELF_LINK)));

    }

    private class DocumentDescriptionResponse {

        private List<String> documentLinks;

        public List<String> getDocumentLinks() {
            return documentLinks;
        }
    }

    private static String switchToUnixLineEnds(String s) {
        return s == null ? null : s.replaceAll("\r\n", "\n");
    }

    protected static String getResourceFilePath(String file) {
        try {
            URI uri = ManagementHostTest.class.getResource(file).toURI();
            /*
             * URI.getPath() returns slash prefixed path and causes the test to fail on Windows
             * machines (/C:/... is not a valid path)
             */
            String path = new File(uri).getPath();
            return path;
        } catch (URISyntaxException e) {
            fail(e.getMessage());
            return null;
        }
    }

}
