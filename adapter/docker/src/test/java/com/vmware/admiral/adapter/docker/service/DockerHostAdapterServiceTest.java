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

package com.vmware.admiral.adapter.docker.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.net.ssl.TrustManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.docker.mock.BaseMockDockerTestCase;
import com.vmware.admiral.adapter.docker.mock.MockDockerHostService;
import com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.admiral.service.test.MockDockerAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

/**
 * Test the DockerAdapterService operations (create/delete container)
 */
public class DockerHostAdapterServiceTest extends BaseMockDockerTestCase {
    private static final String TASK_INFO_STAGE = TaskServiceDocument.FIELD_NAME_TASK_STAGE;
    private URI dockerHostAdapterServiceUri;
    private ComputeState dockerHostState;
    private ContainerState shellContainerState;
    private String testDockerCredentialsLink;
    private String provisioningTaskLink;

    private DockerAdapterCommandExecutor commandExecutor;

    @Before
    public void startServices() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);

        mockDockerHost.startService(
                Operation.createPost(UriUtils.buildUri(
                        mockDockerHost, MockDockerHostService.SELF_LINK)),
                new MockDockerHostService());
        MockDockerAdapterService.resetContainers();
    }

    @Before
    public void setUp() throws Throwable {
        createTestDockerAuthCredentials();
        createDockerHostComputeState();
        createHostShellContainer();
        createProvisioningTask();

        setupDockerAdapterService();

        mockDockerHost.startService(
                Operation.createPost(UriUtils.buildUri(
                        mockDockerHost,
                        MockDockerHostService.SELF_LINK + MockDockerPathConstants.INFO)),
                new MockDockerHostService());
    }

    @After
    public void tearDown() throws Throwable {
        deleteHostShellContainer();
        deleteDockerHostComputeState();
    }

    @Test
    public void testHostDirectPing() throws Throwable {
        mockDockerHost.waitForServiceAvailable(MockDockerHostService.SELF_LINK
                + MockDockerPathConstants._PING);

        ContainerHostRequest request = new ContainerHostRequest();
        request.resourceReference = UriUtils.buildUri(host, ComputeService.FACTORY_LINK);
        request.operationTypeId = ContainerHostOperationType.PING.id;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);
        request.customProperties = new HashMap<>();
        request.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                testDockerCredentialsLink);
        request.customProperties.put(ContainerHostService.DOCKER_HOST_ADDRESS_PROP_NAME,
                dockerUri.toString());
        request.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());

        sendContainerHostRequest(request);
    }

    @Test
    public void testHostPingWithResourceReference() throws Throwable {
        dockerHostState = requestDockerHostOperaton(MockDockerPathConstants._PING,
                ContainerHostOperationType.PING);
    }

    @Test
    public void testHostVersion() throws Throwable {
        dockerHostState = requestDockerHostOperaton(MockDockerPathConstants.VERSION,
                ContainerHostOperationType.VERSION);
        assertEquals(MockDockerPathConstants.API_VERSION,
                dockerHostState.customProperties.get("__ApiVersion"));
    }

    @Test
    public void testHostVersion1() throws Throwable {
        mockDockerHost.waitForServiceAvailable(MockDockerHostService.SELF_LINK
                + MockDockerPathConstants.VERSION);

        sendContainerHostRequest(ContainerHostOperationType.VERSION,
                UriUtils.buildUri(host, dockerHostState.documentSelfLink));

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, TASK_INFO_STAGE,
                TaskState.TaskStage.FINISHED);

        dockerHostState = retrieveDockerHostState();
        assertEquals(MockDockerPathConstants.API_VERSION,
                dockerHostState.customProperties.get("__ApiVersion"));
    }

    @Test
    public void testHostPowerStateAfterInfoOperation() throws Throwable {
        ComputeState state = new ComputeState();
        state.powerState = PowerState.SUSPEND;
        doOperation(state,
                UriUtils.buildUri(host, dockerHostState.documentSelfLink), false,
                Action.PATCH);
        waitFor(() -> {
            dockerHostState = retrieveDockerHostState();
            return PowerState.SUSPEND.equals(dockerHostState.powerState);
        });
        dockerHostState = requestDockerHostOperaton(MockDockerPathConstants.INFO,
                ContainerHostOperationType.INFO);

        // If the container host is in state suspended it should not be changed after the info
        // operation
        dockerHostState = retrieveDockerHostState();
        assertEquals(PowerState.SUSPEND, dockerHostState.powerState);
    }

    @Test
    public void testHostInfo() throws Throwable {
        dockerHostState = requestDockerHostOperaton(MockDockerPathConstants.INFO,
                ContainerHostOperationType.INFO);

        String numberOfContainersValue = dockerHostState.customProperties
                .get(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);
        // We do not expect to have the containers count in compute state before having stored all
        // the container states
        assertNull(numberOfContainersValue);
    }

    @Test
    public void testHostAvailableAfterFailure() throws Throwable {
        ComputeState state = new ComputeState();
        state.powerState = PowerState.SUSPEND;
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(ContainerHostService.RETRIES_COUNT_PROP_NAME, "1");
        state.customProperties = properties;
        doOperation(state,
                UriUtils.buildUri(host, dockerHostState.documentSelfLink), false,
                Action.PATCH);
        waitFor(() -> {
            dockerHostState = retrieveDockerHostState();
            return PowerState.SUSPEND.equals(dockerHostState.powerState);
        });

        ContainerHostDataCollectionState dataCollectionState = new ContainerHostDataCollectionState();
        dataCollectionState.computeContainerHostLinks = new ArrayList<String>();
        dataCollectionState.computeContainerHostLinks
                .add(retrieveDockerHostState().documentSelfLink);
        doOperation(dataCollectionState, UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        waitFor(() -> {
            dockerHostState = retrieveDockerHostState();
            return PowerState.ON.equals(dockerHostState.powerState);
        });
    }

    @Test
    public void testHostNotAvailable() throws Throwable {
        ComputeState state = new ComputeState();
        state.powerState = PowerState.ON;
        doOperation(state,
                UriUtils.buildUri(host, dockerHostState.documentSelfLink), false,
                Action.PATCH);
        waitFor(() -> {
            dockerHostState = retrieveDockerHostState();
            return PowerState.ON.equals(dockerHostState.powerState);
        });
        MockDockerHostService service = new MockDockerHostService();
        service.setSelfLink(
                MockDockerPathConstants.BASE_VERSIONED_PATH + MockDockerPathConstants.INFO);
        mockDockerHost.stopService(service);

        ContainerHostDataCollectionState dataCollectionState = new ContainerHostDataCollectionState();
        dataCollectionState.computeContainerHostLinks = new ArrayList<String>();
        dataCollectionState.computeContainerHostLinks
                .add(retrieveDockerHostState().documentSelfLink);
        doOperation(dataCollectionState, UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        waitFor(() -> {
            dockerHostState = retrieveDockerHostState();
            return PowerState.SUSPEND.equals(dockerHostState.powerState);
        });
    }

    @Test
    public void testHostPermanentlyNotAvailable() throws Throwable {
        ComputeState state = new ComputeState();
        state.powerState = PowerState.SUSPEND;
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(ContainerHostService.RETRIES_COUNT_PROP_NAME, "2");
        state.customProperties = properties;
        doOperation(state,
                UriUtils.buildUri(host, dockerHostState.documentSelfLink), false,
                Action.PATCH);
        waitFor(() -> {
            dockerHostState = retrieveDockerHostState();
            return PowerState.SUSPEND.equals(dockerHostState.powerState);
        });
        MockDockerHostService service = new MockDockerHostService();
        service.setSelfLink(
                MockDockerPathConstants.BASE_VERSIONED_PATH + MockDockerPathConstants.INFO);
        mockDockerHost.stopService(service);

        ContainerHostDataCollectionState dataCollectionState = new ContainerHostDataCollectionState();
        dataCollectionState.computeContainerHostLinks = new ArrayList<String>();
        dataCollectionState.computeContainerHostLinks
                .add(retrieveDockerHostState().documentSelfLink);
        doOperation(dataCollectionState, UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        waitFor(() -> {
            ComputeState dockerHostState = retrieveDockerHostState();
            return PowerState.OFF.equals(dockerHostState.powerState);
        });
    }

    @Test
    public void testMarkContainerInErrorWhenNotAvailable() throws Throwable {
        ComputeState state = new ComputeState();
        state.powerState = PowerState.SUSPEND;
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(ContainerHostService.RETRIES_COUNT_PROP_NAME, "2");
        state.customProperties = properties;
        doOperation(state,
                UriUtils.buildUri(host, dockerHostState.documentSelfLink), false,
                Action.PATCH);
        waitFor(() -> {
            dockerHostState = retrieveDockerHostState();
            return PowerState.SUSPEND.equals(dockerHostState.powerState);
        });
        MockDockerHostService service = new MockDockerHostService();
        service.setSelfLink(
                MockDockerPathConstants.BASE_VERSIONED_PATH + MockDockerPathConstants.INFO);
        mockDockerHost.stopService(service);

        ContainerHostDataCollectionState dataCollectionState = new ContainerHostDataCollectionState();
        dataCollectionState.computeContainerHostLinks = new ArrayList<String>();
        dataCollectionState.computeContainerHostLinks
                .add(retrieveDockerHostState().documentSelfLink);

        doOperation(dataCollectionState, UriUtils.buildUri(host,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK),
                false,
                Service.Action.PATCH);

        waitFor(() -> {
            ContainerState containerState = retrieveShellContainerState();
            return ContainerService.ContainerState.PowerState.ERROR
                    .equals(containerState.powerState);
        });
    }

    @Ignore("This test takes a lot of time to complete because DockerAdapterServiceTest sets pragma"
            + " Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY")
    @Test
    public void testListContainersNotExistingHost() throws Throwable {
        AdapterRequest request = new AdapterRequest();
        request.operationTypeId = ContainerHostOperationType.LIST_CONTAINERS.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.resourceReference = UriUtils.buildUri(host, "not-existing-host");

        Operation startContainer = Operation
                .createPatch(dockerHostAdapterServiceUri)
                .setReferer(URI.create("/")).setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex == null) {
                        fail();
                    }
                });

        host.setTimeoutSeconds(120);
        host.testStart(1);
        host.send(startContainer);
        host.testWait();
    }

    private ComputeState requestDockerHostOperaton(String mockDockerPath,
            ContainerHostOperationType operationType) throws Throwable {
        mockDockerHost.waitForServiceAvailable(MockDockerHostService.SELF_LINK + mockDockerPath);

        sendContainerHostRequest(operationType,
                UriUtils.buildUri(host, dockerHostState.documentSelfLink));

        // wait for provisioning task stage to change to finish
        waitForPropertyValue(provisioningTaskLink, MockTaskState.class, TASK_INFO_STAGE,
                TaskState.TaskStage.FINISHED);

        dockerHostState = retrieveDockerHostState();

        return dockerHostState;
    }

    protected void createProvisioningTask() throws Throwable {
        MockTaskState provisioningTask = new MockTaskState();
        provisioningTaskLink = doPost(provisioningTask,
                MockTaskFactoryService.SELF_LINK).documentSelfLink;
    }

    protected void createTestDockerAuthCredentials() throws Throwable {
        testDockerCredentialsLink = doPost(getDockerCredentials(),
                AuthCredentialsService.FACTORY_LINK).documentSelfLink;
        SslTrustCertificateState dockerServerTrust = getDockerServerTrust();
        if (dockerServerTrust != null && dockerServerTrust.certificate != null
                && !dockerServerTrust.certificate.isEmpty()) {
            doPost(dockerServerTrust, SslTrustCertificateService.FACTORY_LINK);
        }
    }

    protected void createDockerHostComputeState() throws Throwable {
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
        computeState.customProperties.put(
                ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, testDockerCredentialsLink);
        computeState.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());

        computeState.address = dockerUri.toString();
        dockerHostState = doPost(computeState, ComputeService.FACTORY_LINK);
    }

    protected void createHostShellContainer() throws Throwable {
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        ContainerState state = new ContainerState();
        String hostId = Service.getId(dockerHostState.documentSelfLink);
        state.documentSelfLink = SystemContainerDescriptions.getSystemContainerSelfLink(
                SystemContainerDescriptions.AGENT_CONTAINER_NAME, hostId);
        state.parentLink = dockerHostState.documentSelfLink;

        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "80";
        portBinding.hostPort = "80";
        state.ports = new ArrayList<PortBinding>();
        state.ports.add(portBinding);

        shellContainerState = doPost(state, ContainerFactoryService.SELF_LINK);
    }

    protected void deleteDockerHostComputeState() throws Throwable {
        doDelete(UriUtils.buildUri(host, dockerHostState.documentSelfLink), false);
    }

    protected void deleteHostShellContainer() throws Throwable {
        doDelete(UriUtils.buildUri(host, shellContainerState.documentSelfLink), false);
    }

    public void setupDockerAdapterService() {
        dockerHostAdapterServiceUri = UriUtils.buildUri(host, DockerHostAdapterService.class);

        DockerHostAdapterService dockerHostAdapterService = new DockerHostAdapterService() {
            @Override
            protected DockerAdapterCommandExecutor getApiCommandExecutor() {
                return getTestCommandExecutor();
            }
        };

        host.startService(Operation.createPost(dockerHostAdapterServiceUri),
                dockerHostAdapterService);
        host.startService(Operation.createPost(dockerHostAdapterServiceUri),
                new DockerHostAdapterService());

    }

    private void sendContainerHostRequest(ContainerHostOperationType type, URI computeStateReference)
            throws Throwable {
        ContainerHostRequest request = new ContainerHostRequest();
        request.resourceReference = computeStateReference;
        request.operationTypeId = type.id;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);

        sendContainerHostRequest(request);
    }

    private void sendContainerHostRequest(ContainerHostRequest request) throws Throwable {

        Operation startContainer = Operation
                .createPatch(dockerHostAdapterServiceUri)
                .setReferer(URI.create("/")).setBody(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    }

                    host.completeIteration();
                });

        host.testStart(1);
        host.send(startContainer);
        host.testWait();

        if (!isMockTarget()) {
            // in case of testing with a real docker server, give it some time to settle
            Thread.sleep(100L);
        }
    }

    private ComputeState retrieveDockerHostState() throws Throwable {
        Operation getContainerState = Operation.createGet(UriUtils.buildUri(host,
                dockerHostState.documentSelfLink))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);

                    } else {
                        dockerHostState = o.getBody(ComputeState.class);
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(getContainerState);
        host.testWait();

        return dockerHostState;
    }

    private ContainerState retrieveShellContainerState() throws Throwable {
        Operation getShellContainerState = Operation.createGet(UriUtils.buildUri(host,
                shellContainerState.documentSelfLink))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);

                    } else {
                        shellContainerState = o.getBody(ContainerState.class);
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(getShellContainerState);
        host.testWait();

        return shellContainerState;
    }

    protected void verifyContainerStateExists(URI containerStateReference) throws Throwable {
        host.createAndWaitSimpleDirectQuery(
                ServiceDocument.FIELD_NAME_SELF_LINK, containerStateReference.getPath(), 0, 1);
    }

    private DockerAdapterCommandExecutor getTestCommandExecutor() {
        if (commandExecutor == null) {
            String serverTrust = getDockerServerTrust().certificate;
            TrustManager testTrustManager = null;

            if (serverTrust != null && !serverTrust.isEmpty()) {
                testTrustManager = CertificateUtil.getTrustManagers("docker",
                        getDockerServerTrust().certificate)[0];
            }

            commandExecutor = new RemoteApiDockerAdapterCommandExecutorImpl(host,
                    testTrustManager) {

                @Override
                protected void prepareRequest(Operation op, boolean longRunningRequest) {
                    // modify the stats path which is different in the mock docker server
                    if (isMockTarget()) {
                        URI uri = op.getUri();
                        String path = uri.getRawPath();
                        if (path.endsWith("/stats")) {
                            String newPath = path.replace("/stats",
                                    MockDockerPathConstants.STATS);

                            try {
                                URI newUri = new URI(uri.getScheme(), uri.getUserInfo(),
                                        uri.getHost(), uri.getPort(), newPath, uri.getQuery(),
                                        uri.getFragment());

                                op.setUri(newUri);

                            } catch (URISyntaxException x) {
                                throw new RuntimeException(x);
                            }
                        }
                    }

                    super.prepareRequest(op, longRunningRequest);
                }

            };
        }

        return commandExecutor;
    }

}
