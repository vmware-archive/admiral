/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.docker.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.net.ssl.TrustManager;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.adapter.docker.mock.BaseMockDockerTestCase;
import com.vmware.admiral.adapter.docker.mock.MockDockerNetworkService;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.FileContentService;

public class DockerNetworkAdapterServiceTest extends BaseMockDockerTestCase {

    private static final String TEST_CUSTOM_PROP_NAME = "Hostname";
    private static final String TEST_CUSTOM_PROP_VALUE = "testhost";
    private static final String TEST_IMAGE_URL_PATH = "/test-files/testimage.tar";
    private static final String TEST_IMAGE_FILE = "/testimage.tar";
    private static final String TEST_GROUP = "test-group";
    private static final String TEST_NETWORK_NAME = "mynet";
    private static final String TEST_NETWORK_DRIVER = "bridge";

    private static final String TEST_NETWORK_NAME_KEY = "Name";
    private static final String TEST_NETWORK_DRIVER_KEY = "Driver";
    private static final String TEST_NETWORK_ID_KEY = "Id";

    private static Integer NETWORK_LIST_RETRY_COUNT = 5;
    private static Integer TIME_BETWEEN_RETRIES_IN_MILSEC = 1000;

    private String parentComputeStateLink;
    private URI networkStateReference;
    private URI dockerNetworkAdapterServiceUri;
    private static URI imageReference;

    private DockerAdapterCommandExecutor commandExecutor;

    private DockerNetworkAdapterService dockerNetworkAdapterService;

    @Before
    public void startServices() throws Throwable {

        URL testImageResource = DockerAdapterServiceTest.class.getResource(TEST_IMAGE_FILE);
        assertNotNull("Missing test resource: " + TEST_IMAGE_FILE, testImageResource);
        File file = new File(testImageResource.toURI());
        imageReference = UriUtils.buildPublicUri(host, TEST_IMAGE_URL_PATH);
        host.startService(Operation.createPost(imageReference), new FileContentService(file));
    }

    @Before
    public void setupNetworkState() throws Throwable {
        createTestDockerAuthCredentials();

        setupDockerNetworkAdapterService();

        createParentComputeState();

        ContainerNetworkDescription desc = createNetworkDescription(TEST_NETWORK_NAME);

        createNetworkState(desc);

        createNetwork();
    }

    @Test
    public void testNetworkCreation() throws Throwable {
        verifyNetworkListContainsName(TEST_NETWORK_NAME);
    }

    @Test
    public void testNetworkInspect() throws Throwable {
        // Network creation is not direct operation, this means it will take some time.
        while ((MockDockerNetworkService.networksMap == null
                || MockDockerNetworkService.networksMap.isEmpty())
                && NETWORK_LIST_RETRY_COUNT > 0) {
            Thread.sleep(TIME_BETWEEN_RETRIES_IN_MILSEC);
            NETWORK_LIST_RETRY_COUNT--;
        }

        String testNetworkId = MockDockerNetworkService.networksMap.keySet().iterator().next();

        CommandInput commandInput = new CommandInput().withDockerUri(getDockerVersionedUri())
                .withCredentials(getDockerCredentials());
        commandInput.getProperties().put(TEST_NETWORK_ID_KEY, testNetworkId);

        host.testStart(1);
        getTestCommandExecutor().inspectNetwork(commandInput, (o, ex) -> {
            if (ex != null) {
                host.failIteration(ex);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, String> body = o.getBody(Map.class);

                try {
                    assertEquals(body.get(TEST_NETWORK_ID_KEY), testNetworkId);
                    assertEquals(body.get(TEST_NETWORK_NAME_KEY), TEST_NETWORK_NAME);
                    assertEquals(body.get(TEST_NETWORK_DRIVER_KEY), TEST_NETWORK_DRIVER);

                    host.completeIteration();
                } catch (Throwable x) {
                    host.failIteration(x);
                }

            }
        });

        host.testWait();
    }

    protected void createParentComputeState() throws Throwable {
        waitForServiceAvailability(ComputeService.FACTORY_LINK);

        ComputeDescription computeDescription = new ComputeDescription();
        computeDescription.customProperties = new HashMap<String, String>();
        computeDescription.id = UUID.randomUUID().toString();

        String computeDescriptionLink = doPost(computeDescription,
                ComputeDescriptionService.FACTORY_LINK).documentSelfLink;

        ComputeState computeState = new ComputeState();
        computeState.id = "testParentComputeState";
        computeState.descriptionLink = computeDescriptionLink;
        computeState.customProperties = new HashMap<String, String>();
        computeState.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                testDockerCredentialsLink);
        computeState.address = dockerUri.getHost();
        computeState.customProperties.put(ContainerHostService.DOCKER_HOST_SCHEME_PROP_NAME,
                String.valueOf(dockerUri.getScheme()));
        computeState.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME,
                String.valueOf(dockerUri.getPort()));
        computeState.customProperties.put(ContainerHostService.DOCKER_HOST_PATH_PROP_NAME,
                String.valueOf(dockerUri.getPath()));
        computeState.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());

        computeState = doPost(computeState, ComputeService.FACTORY_LINK);

        this.parentComputeStateLink = computeState.documentSelfLink;
    }

    protected ContainerNetworkDescription createNetworkDescription(String name) throws Throwable {
        ContainerNetworkDescription networkDescription = new ContainerNetworkDescription();
        networkDescription.name = name;
        networkDescription.driver = TEST_NETWORK_DRIVER;
        networkDescription.documentSelfLink = UUID.randomUUID().toString();

        return doPost(networkDescription, ContainerNetworkDescriptionService.FACTORY_LINK);
    }

    protected void createNetworkState(ContainerNetworkDescription desc) throws Throwable {
        waitForServiceAvailability(ContainerNetworkService.FACTORY_LINK);

        ContainerNetworkState networkState = new ContainerNetworkState();
        assertNotNull("parentLink", parentComputeStateLink);
        networkState.originatingHostLink = parentComputeStateLink;
        networkState.descriptionLink = desc.documentSelfLink;
        networkState.name = desc.name;
        networkState.driver = desc.driver;

        networkState.tenantLinks = new ArrayList<>();
        networkState.tenantLinks.add(TEST_GROUP);

        // add a custom property
        networkState.customProperties = new HashMap<>();
        networkState.customProperties.put(TEST_CUSTOM_PROP_NAME, TEST_CUSTOM_PROP_VALUE);

        ContainerNetworkState network = doPost(networkState, ContainerNetworkService.FACTORY_LINK);
        networkStateReference = UriUtils.extendUri(host.getUri(), network.documentSelfLink);
    }

    public void setupDockerNetworkAdapterService() {

        dockerNetworkAdapterServiceUri = UriUtils.buildUri(host, DockerNetworkAdapterService.class);

        dockerNetworkAdapterService = new DockerNetworkAdapterService() {
            @Override
            protected DockerAdapterCommandExecutor getCommandExecutor() {
                return getTestCommandExecutor();
            }
        };

        host.startService(Operation.createPost(dockerNetworkAdapterServiceUri),
                dockerNetworkAdapterService);
    }

    private DockerAdapterCommandExecutor getTestCommandExecutor() {
        if (commandExecutor == null) {
            String serverTrust = getDockerServerTrust().certificate;
            TrustManager testTrustManager = null;

            if (serverTrust != null && !serverTrust.isEmpty()) {
                testTrustManager = CertificateUtil.getTrustManagers("docker",
                        getDockerServerTrust().certificate)[0];
            }

            commandExecutor = new RemoteApiDockerAdapterCommandExecutorImpl(host, testTrustManager);

        }

        return commandExecutor;
    }

    /**
     * Create a network, store its id in this.containerId
     *
     * @throws Throwable
     */
    protected void createNetwork() throws Throwable {

        sendContainerNetworkRequest(NetworkOperationType.CREATE);

        verifyNetworkStateExists(networkStateReference);

        // get and validate the network state
        sendGetNetworkStateRequest();

    }

    private void sendContainerNetworkRequest(NetworkOperationType type) throws Throwable {

        // create a fresh provisioning task for each request
        createProvisioningTask();

        AdapterRequest request = new AdapterRequest();
        request.resourceReference = networkStateReference;
        request.operationTypeId = type.id;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);

        Operation createNetwork = Operation.createPatch(dockerNetworkAdapterServiceUri)
                .setReferer(URI.create("/"))
                .setBody(request).setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    }

                    host.completeIteration();
                });

        host.testStart(1);
        host.send(createNetwork);
        host.testWait();

        if (!isMockTarget()) {
            // in case of testing with a real docker server, give it some time to settle
            Thread.sleep(2000L);
        }
    }

    /*
     * Perform a query for the document self link to see that it exists in the index
     */
    protected void verifyNetworkStateExists(URI networkStateReference) throws Throwable {

        host.createAndWaitSimpleDirectQuery(ServiceDocument.FIELD_NAME_SELF_LINK,
                networkStateReference.getPath(), 0, 1);
    }

    private void sendGetNetworkStateRequest() throws Throwable {
        Operation getNetworkState = Operation.createGet(networkStateReference)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);

                    } else {
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(getNetworkState);
        host.testWait();
    }

    protected void verifyNetworkListContainsName(String networkName) throws Throwable {

        CommandInput commandInput = new CommandInput().withDockerUri(getDockerVersionedUri())
                .withCredentials(getDockerCredentials());

        host.testStart(1);
        getTestCommandExecutor().listNetworks(commandInput, (o, ex) -> {
            if (ex != null) {
                host.failIteration(ex);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> body = o.getBody(List.class);
                host.log(Level.INFO, "Retrieved networks list: %s", body);

                Set<Object> networkNames = body.stream().map((item) -> item.get("Name"))
                        .collect(Collectors.toSet());

                try {
                    assertTrue(
                            String.format("Network name [%s] not found in networks list: %s ",
                                    networkName, networkNames),
                            networkNames.contains(networkName));
                    host.completeIteration();
                } catch (Throwable x) {
                    host.failIteration(x);
                }

            }
        });
        host.testWait();
    }

}
