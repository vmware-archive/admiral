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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.host.HostInitDockerAdapterServiceConfig.FIELD_NAME_START_MOCK_HOST_ADAPTER_INSTANCE;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.DELAY_BETWEEN_AUTH_TOKEN_RETRIES;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.doRestrictedOperation;
import static com.vmware.admiral.host.ManagementHostAuthUsersTest.login;
import static com.vmware.admiral.service.common.AuthBootstrapService.waitForInitConfig;
import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_ASSIGNMENT;
import static com.vmware.xenon.common.CommandLineArgumentParser.ARGUMENT_PREFIX;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.AbstractMap.SimpleEntry;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.io.FileUtils;
import org.junit.ClassRule;
import org.junit.rules.TemporaryFolder;

import com.vmware.admiral.common.test.BaseTestCase;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionFactoryService;
import com.vmware.admiral.compute.container.CompositeDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionState;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.HostNetworkListDataCollectionState;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.AuthBootstrapService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.test.MockComputeHostInstanceAdapter;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service.ServiceOption;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.Builder;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceRequestListener;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.NodeGroupService.NodeGroupState;
import com.vmware.xenon.services.common.NodeGroupService.UpdateQuorumRequest;
import com.vmware.xenon.services.common.NodeState;
import com.vmware.xenon.services.common.NodeState.NodeStatus;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Base class for cluster tests.
 */
public abstract class BaseManagementHostClusterIT {

    protected List<ManagementHost> hostsToTeardown;

    private final Logger logger = Logger.getLogger(BaseManagementHostClusterIT.class.getName());

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

    protected static final String RESOURCE_POOL_LINK = "resource-pool-for-clustering";
    protected static final String GROUP_RESOURCE_STATEMENT_LINK = "group-resource-statement-for-clustering";
    protected static final String COMPUTE_DESCRIPTION_LINK = "compute-description-for-clustering";

    protected static GroupResourcePlacementState groupResourcePlacementState;

    @ClassRule
    public static final TemporaryFolder test = new TemporaryFolder();

    protected static final String LOCALHOST = "https://127.0.0.1:";

    static {
        System.setProperty("encryption.key.file", ENCRYPTION_KEY_FILE);
        System.setProperty("dcp.net.ssl.trustStore", TRUST_STORE_FILE);
        System.setProperty("dcp.net.ssl.trustStorePassword", "changeit");
    }

    protected BaseManagementHostClusterIT() {
    }

    protected static ManagementHost setUpHost(int port, URI sandboxUri, List<String> peers)
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
        String hostId = LOCALHOST.substring(0, LOCALHOST.length() - 2) + "-" + port;

        String peerNodes = String.join(",", peers);

        ManagementHost host = ManagementHostBaseTest.createManagementHost(new String[] {
                ARGUMENT_PREFIX
                        + "id"
                        + ARGUMENT_ASSIGNMENT
                        + hostId,
                ARGUMENT_PREFIX
                        + FIELD_NAME_START_MOCK_HOST_ADAPTER_INSTANCE
                        + ARGUMENT_ASSIGNMENT
                        + Boolean.TRUE.toString(),
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
        assertTrue(host.isStarted());

        // Sleep to give some time for the host to initialize
        Thread.sleep(3000);

        return host;
    }

    protected void tearDownHost(List<ManagementHost> hosts) {
        hosts.forEach(host -> stopHost(host));
    }

    protected void stopHost(ManagementHost host) {

        if (host == null) {
            return;
        }

        String hostname = host.getUri().toString();
        logger.log(Level.INFO, String.format("Stopping host '%s'", hostname));

        try {
            ServiceRequestListener secureListener = host.getSecureListener();
            host.stop();
            secureListener.stop();
            // waitWhilePortIsListening(host);
            // Surrounded with try catch because on Windows OS, there is probably
            // permissions problem and this util cannot delete the sandbox.
            try {
                FileUtils.deleteDirectory(new File(host.getStorageSandbox().getPath()));
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error when trying to remove sandbox", t);
            }
            logger.log(Level.INFO, String.format("Host '%s' stopped", hostname));

        } catch (Exception e) {
            throw new RuntimeException("Exception stopping host!", e);
        }
    }

    /**
     * Stops the host and removes it from default node group.
     *
     * @param availableHost - available host which will exclude other from group.
     * @param hostToStop    - unavailable host.
     */
    protected void stopHostAndRemoveItFromNodeGroup(ManagementHost availableHost,
            ManagementHost hostToStop) {

        stopHost(hostToStop);

        if (availableHost != null) {
            waitUntilNodeIsRemovedFromGroup(availableHost);
        }

    }

    public void validateDefaultContentAdded(List<ManagementHost> allHostsInstances)
            throws Throwable {
        Map<String, Class<? extends ServiceDocument>> defaultContent = new HashMap<>();

        defaultContent.put(ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK,
                ContainerHostDataCollectionState.class);
        defaultContent.put(
                HostContainerListDataCollection.DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK,
                HostContainerListDataCollectionState.class);
        defaultContent.put(
                HostNetworkListDataCollection.DEFAULT_HOST_NETWORK_LIST_DATA_COLLECTION_LINK,
                HostNetworkListDataCollectionState.class);

        defaultContent.put("/config/props/__build.number", ConfigurationState.class);
        defaultContent.put("/config/props/container.user.resources.path", ConfigurationState.class);
        defaultContent.put("/config/props/register.user.retries.count", ConfigurationState.class);
        defaultContent.put("/config/props/docker.container.min.memory", ConfigurationState.class);
        defaultContent.put("/config/props/register.user.interval.delay", ConfigurationState.class);
        defaultContent.put("/config/props/container.user.resources.path", ConfigurationState.class);

        defaultContent.put(GroupResourcePlacementService.DEFAULT_RESOURCE_PLACEMENT_LINK,
                GroupResourcePlacementState.class);

        for (Entry<String, Class<? extends ServiceDocument>> docEntry : defaultContent.entrySet()) {
            waitForDocumentSync(allHostsInstances, docEntry.getKey(), docEntry.getValue());
        }
    }

    private <T extends ServiceDocument> void validateDefaultContentInSync(
            List<ManagementHost> allHostsInstances, String documentSelfLink, Class<T> type)
            throws Throwable {
        ManagementHost firstHost = allHostsInstances.get(0);
        String firstToken = login(firstHost, USERNAME, PASSWORD);
        T firstState = doGet(firstHost, documentSelfLink, type, firstToken);
        assertNotNull(
                "State with link " + documentSelfLink + " was not found on host "
                        + firstHost.getUri().toString(),
                firstState);

        for (ManagementHost host : allHostsInstances) {
            logger.log(Level.INFO,
                    "Finding state with link " + documentSelfLink + " on host %s", host.getId());
            String hostToken = login(host, USERNAME, PASSWORD);
            T state = doGet(host, documentSelfLink, type, hostToken);
            logger.log(Level.INFO, "State with link " + documentSelfLink + " was not found on host "
                    + host.getId(), state);

            logger.log(Level.INFO,
                    "Found state with link " + documentSelfLink + " on host " + host.getId());

            if (!equals(firstState, state, type)) {
                System.out.println(" - first state");
                System.out.println(Utils.toJsonHtml(firstState));

                System.out.println(" - current state");
                System.out.println(Utils.toJsonHtml(state));

                fail("States with link " + documentSelfLink
                        + " are not the same on different hosts");
            }
        }
    }

    private <T extends ServiceDocument> void waitForDocumentSync(
            List<ManagementHost> allHostsInstances, String documentSelfLink,
            Class<T> type) throws TimeoutException, InterruptedException {
        waitFor(allHostsInstances.get(0), new Condition() {
            @Override
            public boolean isReady() {
                try {
                    validateDefaultContentInSync(allHostsInstances, documentSelfLink,
                            type);
                    return true;
                } catch (AssertionError e) {
                    logger.log(Level.WARNING, "Exception when validating synchronized documents",
                            e);
                } catch (Throwable ex) {
                    logger.log(Level.WARNING, "Exception caught: ", ex);
                }
                return false;
            }

            @Override
            public String getDescription() {
                return "Waiting for documents to get synchronized.";
            }
        });
    }

    private static <T extends ServiceDocument> boolean equals(T documentA, T documentB,
            Class<T> type) {
        EnumSet<ServiceOption> options = EnumSet.noneOf(ServiceOption.class);
        ServiceDocumentDescription buildDescription = Builder.create().buildDescription(type,
                options);

        documentA.documentUpdateTimeMicros = 0;
        documentA.documentExpirationTimeMicros = 0;

        documentB.documentUpdateTimeMicros = 0;
        documentB.documentExpirationTimeMicros = 0;

        return ServiceDocument.equals(buildDescription, documentA, documentB);
    }

    private <T extends ServiceDocument> T doGet(ServiceHost host, String selfLink, Class<T> type,
            String token) {
        AtomicReference<T> result = new AtomicReference<>();

        TestContext ctx = testContext();

        QueryTask q = QueryUtil.buildPropertyQuery(type, ServiceDocument.FIELD_NAME_SELF_LINK,
                selfLink);
        QueryUtil.addExpandOption(q);

        host.sendRequest(Operation
                .createGet(UriUtils.buildUri(host, ServiceUriPaths.CORE_DOCUMENT_INDEX,
                        "documentSelfLink=" + selfLink))
                .addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, token)
                .setBody(q)
                .setReferer(host.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        ctx.failIteration(e);
                    } else {
                        result.set(o.getBody(type));
                        ctx.completeIteration();
                    }
                }));

        ctx.await();

        return result.get();
    }

    protected void waitWhilePortIsListening(ManagementHost host)
            throws TimeoutException, InterruptedException {

        SSLSocketFactory factory = ManagementHostAuthUsersTest.getUnsecuredSSLSocketFactory();
        boolean portListening = true;
        while (portListening) {
            try (Socket s = factory.createSocket((String) null, host.getSecurePort())) {
                logger.log(Level.INFO,
                        "Wait while port '" + host.getSecurePort() + "' is listening...");
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
        logger.log(Level.INFO, "Starting host '" + hostname + "'...");

        host = setUpHost(host.getSecurePort(), sandboxUri, peers);

        waitForInitConfig(host, host.localUsers);

        logger.log(Level.INFO, "Sleep for a while, until the host starts...");
        Thread.sleep(5000);
        logger.log(Level.INFO, "Host '" + hostname + "' started.");

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
            final String[] tokens = new String[1];
            // String token = login(host, USERNAME, PASSWORD);
            waitFor(host, new Condition() {

                @Override
                public boolean isReady() {
                    try {
                        String token = login(host, USERNAME, PASSWORD);
                        if (token != null && !token.isEmpty()) {
                            tokens[0] = token;
                            return true;
                        }
                    } catch (Throwable e) {
                        host.log(Level.WARNING, "Exception while getting token from host: %s",
                                host.getUri());
                    }
                    return false;
                }

                @Override
                public String getDescription() {
                    return String.format("Getting auth token for host: %s", host.getUri());
                }

            });

            assertNotNull(tokens[0]);
            Map<String, String> headers = new HashMap<>();
            headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, tokens[0]);
            assertNodes(headers, hosts);
        }
    }

    private static void assertNodes(Map<String, String> headers, ManagementHost... hosts)
            throws Exception {
        for (ManagementHost host : hosts) {

            // Assert restricted operation access
            waitFor(host, new Condition() {

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

            waitFor(host, new Condition() {

                @Override
                public boolean isReady() {
                    try {
                        SimpleEntry<Integer, String> response = ManagementHostAuthUsersTest
                                .doGet(uri, headers);
                        assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());

                        NodesStatusJSONResponseMapper nodeDocument = Utils
                                .fromJson(response.getValue(), NodesStatusJSONResponseMapper.class);

                        for (ManagementHost currentHost : hosts) {
                            nodeDocument.assertProperty(currentHost.getId(), "status", "AVAILABLE");
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

    protected void initializeProvisioningContext(ManagementHost host) throws Throwable {
        Map<String, String> headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(host, USERNAME, PASSWORD));
        headers.put("Content-Type", "application/json;charset=utf-8");
        startContextCreationWithResourcePool(headers, host);
    }

    /**
     * Starts provisioning context sequentially 1.ResourcePool 2.GroupResourceState
     * 3.ComputeDescription 4.ComputeState
     *
     * @param headers - headers for rest calls
     * @param host    - ServiceHost
     * @throws Exception
     */
    private void startContextCreationWithResourcePool(Map<String, String> headers,
            ManagementHost host)
            throws Exception {

        checkHostAccess(headers, host);

        ResourcePoolState resourcePool = TestRequestStateFactory.createResourcePool();
        resourcePool.documentSelfLink = RESOURCE_POOL_LINK;

        String body = Utils.toJson(resourcePool);
        URI uri = UriUtils.buildUri(host, ResourcePoolService.FACTORY_LINK);

        waitFor(host, new Condition() {

            @Override
            public boolean isReady() {
                try {
                    SimpleEntry<Integer, String> response = ManagementHostAuthUsersTest.doPost(uri,
                            headers, body);
                    assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());

                    createGroupResourcePlacement(headers, host, RESOURCE_POOL_LINK);

                } catch (Throwable e) {
                    host.log(Level.WARNING, "Request to [%s] failed with: [%s].", uri,
                            e.getMessage());
                    return false;
                }

                return true;
            }

            @Override
            public String getDescription() {
                return String.format("Sending POST Request to: [%s] with headers: [%s]", uri,
                        headers);
            }
        });
    }

    private void createGroupResourcePlacement(Map<String, String> headers, ManagementHost host,
            String resourcePoolLink) throws IOException {

        GroupResourcePlacementState groupPlacementState = TestRequestStateFactory
                .createGroupResourcePlacementState(ResourceType.CONTAINER_TYPE);
        groupResourcePlacementState = groupPlacementState;
        groupPlacementState.maxNumberInstances = 100;
        groupPlacementState.resourcePoolLink = UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK,
                resourcePoolLink);
        groupPlacementState.documentSelfLink = GROUP_RESOURCE_STATEMENT_LINK;
        String body = Utils.toJson(groupPlacementState);
        URI uri = UriUtils.buildUri(host, GroupResourcePlacementService.FACTORY_LINK);
        SimpleEntry<Integer, String> response = ManagementHostAuthUsersTest.doPost(uri, headers,
                body);
        assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());
        logger.log(Level.INFO,
                "############### RESOURCE POOL PLACEMENT HAS BEEN CREATED ###################");
        createComputeDescription(headers, host);
    }

    private void createComputeDescription(Map<String, String> headers, ManagementHost host)
            throws IOException {

        ComputeDescription hostDesc = TestRequestStateFactory.createDockerHostDescription();
        hostDesc.documentSelfLink = COMPUTE_DESCRIPTION_LINK;
        hostDesc.instanceAdapterReference = UriUtils.buildUri(host,
                MockComputeHostInstanceAdapter.SELF_LINK);
        String body = Utils.toJson(hostDesc);
        URI uri = UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK);
        SimpleEntry<Integer, String> response = ManagementHostAuthUsersTest.doPost(uri, headers,
                body);
        assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());
        logger.log(Level.INFO,
                "############### COMPUTE DESCRIPTION HAS BEEN CREATED ###################");

        createComputeState(headers, host, RESOURCE_POOL_LINK, GROUP_RESOURCE_STATEMENT_LINK);

    }

    private void createComputeState(Map<String, String> headers, ManagementHost host,
            String resourcePoolLink, String groupResourcePlacementLink) throws IOException {

        ComputeState containerHost = TestRequestStateFactory.createDockerComputeHost();
        containerHost.documentSelfLink = containerHost.id;
        containerHost.resourcePoolLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK,
                resourcePoolLink);
        containerHost.descriptionLink = UriUtils.buildUriPath(
                ComputeDescriptionService.FACTORY_LINK,
                COMPUTE_DESCRIPTION_LINK);

        if (containerHost.customProperties == null) {
            containerHost.customProperties = new HashMap<>();
        }

        containerHost.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        containerHost.customProperties.put(
                ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                String.valueOf(Integer.MAX_VALUE - 100L));

        containerHost.customProperties.put(ComputeConstants.GROUP_RESOURCE_PLACEMENT_LINK_NAME,
                UriUtils.buildUriPath(GroupResourcePlacementService.FACTORY_LINK,
                        groupResourcePlacementLink));

        String body = Utils.toJson(containerHost);
        URI uri = UriUtils.buildUri(host, ComputeService.FACTORY_LINK);
        SimpleEntry<Integer, String> response = ManagementHostAuthUsersTest.doPost(uri, headers,
                body);
        assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());
        logger.log(Level.INFO,
                "############### COMPUTE STATE HAS BEEN CREATED ###################");

    }

    protected void startRequest(Map<String, String> headers, ManagementHost host,
            RequestBrokerState requestState) throws IOException {
        String body = Utils.toJson(requestState);
        URI uri = UriUtils.buildUri(host, RequestBrokerFactoryService.SELF_LINK);
        SimpleEntry<Integer, String> response = ManagementHostAuthUsersTest.doPost(uri, headers,
                body);
        assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());
        logger.log(Level.INFO, "############### REQUEST STARTED ###################");
    }

    protected String getResource(ManagementHost host, Map<String, String> headers, URI uri)
            throws IOException {

        SimpleEntry<Integer, String> result = ManagementHostAuthUsersTest.doGet(uri, headers);

        assertEquals("Operation should be OK!", HttpURLConnection.HTTP_OK, (int) result.getKey());

        String body = result.getValue();

        assertNotNull(body);

        return body;

    }

    protected RequestJSONResponseMapper waitTaskToCompleteAndGetResponse(
            Map<String, String> headers, ManagementHost host, URI uri)
            throws InterruptedException, TimeoutException {

        final RequestJSONResponseMapper[] result = new RequestJSONResponseMapper[1];

        waitFor(host, new Condition() {

            @Override
            public boolean isReady() {
                try {
                    // From response in JSON format we will get provisioned resources.
                    String resourceAsJson = getResource(host, headers, uri);
                    RequestJSONResponseMapper response = Utils.fromJson(resourceAsJson,
                            RequestJSONResponseMapper.class);
                    assertNotNull(response);

                    if (response.taskSubStage == null || !response.taskSubStage
                            .equals(RequestBrokerState.SubStage.COMPLETED.toString())) {
                        return false;
                    }

                    result[0] = response;

                } catch (Throwable e) {
                    host.log(Level.WARNING, "Request to [%s] failed with: [%s].", uri,
                            e.getMessage());
                    return false;
                }

                return true;
            }

            @Override
            public String getDescription() {
                return String.format("Sending POST Request to: [%s] with headers: [%s]", uri,
                        headers);
            }
        });

        return result[0];
    }

    protected Map<String, String> getAuthenticationHeaders(ManagementHost host) throws Throwable {
        Map<String, String> headers = new HashMap<>();
        headers.put(Operation.REQUEST_AUTH_TOKEN_HEADER, login(host, USERNAME, PASSWORD));
        headers.put("Content-Type", "application/json;charset=utf-8");
        return headers;
    }

    private static void checkHostAccess(Map<String, String> headers, ManagementHost host)
            throws InterruptedException, TimeoutException {
        // Assert restricted operation access before provisioning.
        waitFor(host, new Condition() {
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
    }

    protected interface Condition {

        public boolean isReady();

        public String getDescription();

    }

    public static void waitFor(ServiceHost host, Condition condition) throws TimeoutException {
        long start = System.currentTimeMillis();
        long end = start + TIMEOUT_FOR_WAIT_CONDITION; //Wait 1 minute.

        while (!condition.isReady()) {
            if (System.currentTimeMillis() > end) {
                throw new TimeoutException(
                        String.format("Timeout waiting for: [%s]", condition.getDescription()));
            }

            host.schedule(() -> {
                try {
                    waitFor(host, condition);
                } catch (TimeoutException e) {
                    host.log(Level.WARNING, e.getMessage());
                }
            }, DELAY_BETWEEN_AUTH_TOKEN_RETRIES, TimeUnit.SECONDS);
        }
    }

    protected void assertContainerDescription(ManagementHost host, Map<String, String> headers)
            throws IOException {

        URI uri = UriUtils.buildUri(host, ContainerDescriptionService.FACTORY_LINK);

        SimpleEntry<Integer, String> result = ManagementHostAuthUsersTest.doGet(uri, headers);

        assertEquals("Operation should be OK!", HttpURLConnection.HTTP_OK, (int) result.getKey());

        String body = result.getValue();

        assertNotNull(body);

        DocumentDescriptionResponse response = Utils.fromJson(Utils.toJson(body),
                DocumentDescriptionResponse.class);

        assertNotNull(response);
        assertNotNull(response.getDocumentLinks());

        assertTrue("Document with selfLink 'test-container-desc' must exists in response!",
                response.getDocumentLinks().contains(UriUtils
                        .buildUriPath(ContainerDescriptionService.FACTORY_LINK,
                                TestAuthServiceDocumentHelper.TEST_CONTAINER_DESC_SELF_LINK)));

    }

    protected CompositeDescriptionService.CompositeDescription createCompositeDesc(
            Map<String, String> headers, ManagementHost host, ServiceDocument... descs)
            throws Throwable {

        CompositeDescriptionService.CompositeDescription compositeDesc = TestRequestStateFactory
                .createCompositeDescription();
        compositeDesc.documentSelfLink = CompositeDescriptionFactoryService.SELF_LINK + "/"
                + UUID.randomUUID().toString();

        for (ServiceDocument desc : descs) {

            URI uri = null;

            if (desc instanceof ContainerDescriptionService.ContainerDescription) {
                uri = UriUtils.buildUri(host, ContainerDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ContainerNetworkDescriptionService.ContainerNetworkDescription) {
                uri = UriUtils.buildUri(host, ContainerNetworkDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ComputeDescriptionService.ComputeDescription) {
                uri = UriUtils.buildUri(host, ComputeDescriptionService.FACTORY_LINK);
            } else if (desc instanceof ContainerVolumeDescriptionService.ContainerVolumeDescription) {
                uri = UriUtils.buildUri(host, ContainerVolumeDescriptionService.FACTORY_LINK);
            } else {
                throw new IllegalArgumentException(
                        "Unknown description type: " + desc.getClass().getSimpleName());
            }

            SimpleEntry<Integer, String> response = ManagementHostAuthUsersTest.doPost(uri, headers,
                    Utils.toJson(desc));
            assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());

            compositeDesc.descriptionLinks.add(uri.getPath() + "/" + desc.documentSelfLink);
        }

        URI uri = UriUtils.buildUri(host, CompositeDescriptionFactoryService.SELF_LINK);
        SimpleEntry<Integer, String> response = ManagementHostAuthUsersTest.doPost(uri, headers,
                Utils.toJson(compositeDesc));
        assertEquals(HttpURLConnection.HTTP_OK, (int) response.getKey());

        return compositeDesc;
    }

    private class DocumentDescriptionResponse {

        private List<String> documentLinks;

        public List<String> getDocumentLinks() {
            return documentLinks;
        }
    }

    protected class RequestJSONResponseMapper {

        String resourceType;
        String operation;
        String resourceDescriptionLink;
        String groupResourcePlacementLink;
        String taskSubStage;
        List<String> resourceLinks;

    }

    protected class ContainerJSONResponseMapper {
        String documentSelfLink;
        String parentLink;
        String descriptionLink;
        String powerState;
        List<String> names;
    }

    protected class CompositeComponentJSONResponseMapper {
        String documentSelfLink;
        String compositeDescriptionLink;
        List<String> componentLinks;
    }

    protected class NodesStatusJSONResponseMapper {

        Object nodes;

        public void assertProperty(String hostId, String property, String value) {
            assertNotNull("nodes", nodes);
            @SuppressWarnings("unchecked")
            // {ManagementHostClusterOf3NodesIT-20557={groupReference=https://127.0.0.1:20557/core/node-groups/default,
                    // status=AVAILABLE, options=[PEER] ....}}
                    Map<Object, Object> nodes = Utils.fromJson(this.nodes, Map.class);
            @SuppressWarnings("unchecked")
            // {groupReference=https://127.0.0.1:20557/core/node-groups/default,
                    // status=AVAILABLE...}
                    Map<Object, Object> hostProperties = Utils
                    .fromJson(nodes.get(hostId), Map.class);
            assertEquals(hostProperties.get(property), value);
        }

    }

    protected static String getResourceFilePath(String file) {
        return getResourceAsFile(file).getPath();
    }

    protected static File getResourceAsFile(String resourcePath) {
        try {
            InputStream in = BaseTestCase.class.getResourceAsStream(resourcePath);
            if (in == null) {
                return null;
            }

            File tempFile = File.createTempFile(String.valueOf(in.hashCode()), ".tmp");
            tempFile.deleteOnExit();

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            return tempFile;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static TestContext testContext() {
        return TestContext.create(1, TimeUnit.SECONDS.toMicros(10));
    }

    private void waitUntilNodeIsRemovedFromGroup(ManagementHost availableHost) {
        TestContext waiter = new TestContext(1, Duration.ofSeconds(60));

        AtomicBoolean unavailableNodeDetected = new AtomicBoolean(false);

        Operation.createGet(availableHost, ServiceUriPaths.DEFAULT_NODE_GROUP)
                .setReferer(availableHost.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        availableHost.log(Level.WARNING,
                                String.format("Exception while getting state of default group: %s",
                                        e.getMessage()));
                        waiter.fail(e);
                        return;
                    }

                    NodeGroupState state = o.getBody(NodeGroupState.class);

                    if (state.nodes != null && !state.nodes.isEmpty()) {
                        for (Entry<String, NodeState> map : state.nodes.entrySet()) {
                            if (map.getValue().status == NodeStatus.UNAVAILABLE) {
                                unavailableNodeDetected.set(true);
                                state.nodes.remove(map.getKey());
                            }
                        }
                    }

                    if (unavailableNodeDetected.get()) {
                        // Unavailable nodes detected. Going to remove them.
                        removeUnavailableNodeFromGroup(availableHost, state, waiter);
                    } else {
                        // All nodes are available.
                        waiter.completeIteration();
                    }
                }).sendWith(availableHost);

        waiter.await();
    }

    private void removeUnavailableNodeFromGroup(ManagementHost availableHost, NodeGroupState state,
            TestContext waiter) {
        Operation.createPatch(availableHost, ServiceUriPaths.DEFAULT_NODE_GROUP)
                .setBody(state)
                .setReferer(availableHost.getUri())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        waiter.fail(e);
                        return;
                    }
                    // Only available nodes remain.
                    int availableNodes = state.nodes.size();
                    createUpdateQuorumOperation(availableHost, availableNodes, waiter);

                }).sendWith(availableHost);
    }

    protected void createUpdateQuorumOperation(ManagementHost availableHost, int availableNodes,
            TestContext waiter) {
        createUpdateQuorumOperation(availableHost, availableNodes, waiter, null, null);
    }

    protected void createUpdateQuorumOperation(ManagementHost availableHost, int availableNodes,
            TestContext waiter, String authToken, Integer retryCount) {

        availableHost.log(Level.INFO, "Auth token in update quorum request: " + authToken);

        UpdateQuorumRequest request = new UpdateQuorumRequest();
        request.isGroupUpdate = true;
        request.kind = UpdateQuorumRequest.KIND;
        request.membershipQuorum = (availableNodes / 2) + 1;

        availableHost.log(Level.INFO,
                String.format("Updating membershipQuorum to %d", request.membershipQuorum));

        Operation patch = Operation.createPatch(availableHost, ServiceUriPaths.DEFAULT_NODE_GROUP)
                .setBody(request)
                .setReferer(availableHost.getUri())
                .addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, authToken)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                if (retryCount != null && retryCount > 0) {
                                    availableHost.log(Level.INFO, "Retrying %s times to relax "
                                            + "the quorum", retryCount);
                                    availableHost.schedule(() ->
                                                    createUpdateQuorumOperation(availableHost,
                                                            availableNodes, waiter, authToken,
                                                            retryCount - 1),
                                            DELAY_BETWEEN_AUTH_TOKEN_RETRIES, TimeUnit.SECONDS);
                                } else {
                                    waiter.fail(e);
                                }
                            } else {
                                waiter.completeIteration();
                            }
                        });

        if (authToken != null && !authToken.isEmpty()) {
            patch = patch.addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, authToken);
        }

        patch.sendWith(availableHost);
    }

    protected void disableDataCollection(ManagementHost host, String token, TestContext waiter) {

        Operation.createDelete(UriUtils.buildUri(host,
                HostContainerListDataCollection.DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK))
                .setReferer(host.getUri())
                .addRequestHeader(Operation.REQUEST_AUTH_TOKEN_HEADER, token)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        waiter.fail(e);
                        return;
                    }
                    waiter.completeIteration();

                }).sendWith(host);
    }
}
