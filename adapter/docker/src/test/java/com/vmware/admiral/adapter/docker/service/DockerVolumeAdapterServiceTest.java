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

import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.adapter.common.service.mock.MockTaskFactoryService;
import com.vmware.admiral.adapter.common.service.mock.MockTaskService.MockTaskState;
import com.vmware.admiral.adapter.docker.mock.BaseMockDockerTestCase;
import com.vmware.admiral.common.test.HostInitTestDcpServicesConfig;
import com.vmware.admiral.common.util.CertificateUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.host.HostInitCommonServiceConfig;
import com.vmware.admiral.host.HostInitComputeServicesConfig;
import com.vmware.admiral.host.HostInitPhotonModelServiceConfig;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.SslTrustCertificateService;
import com.vmware.admiral.service.common.SslTrustCertificateService.SslTrustCertificateState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.FileContentService;

public class DockerVolumeAdapterServiceTest extends BaseMockDockerTestCase {

    private static final String TEST_CUSTOM_PROP_NAME = "Hostname";
    private static final String TEST_CUSTOM_PROP_VALUE = "testhost";
    private static final String TEST_IMAGE_URL_PATH = "/test-files/testimage.tar";
    private static final String TEST_IMAGE_FILE = "/testimage.tar";
    private static final String TEST_GROUP = "test-group";
    private static final String TEST_VOLUME_NAME = "foo";

    private String parentComputeStateLink;
    private String testDockerCredentialsLink;
    private String provisioningTaskLink;
    private URI volumeStateReference;
    private URI dockerVolumeAdapterServiceUri;
    private static URI imageReference;

    private DockerAdapterCommandExecutor commandExecutor;

    private DockerVolumeAdapterService dockerVolumeAdapterService;

    @Before
    public void startServices() throws Throwable {
        HostInitTestDcpServicesConfig.startServices(host);
        HostInitPhotonModelServiceConfig.startServices(host);
        HostInitCommonServiceConfig.startServices(host);
        HostInitComputeServicesConfig.startServices(host);

        host.startService(
                Operation.createPost(UriUtils.buildUri(host, MockTaskFactoryService.SELF_LINK)),
                new MockTaskFactoryService());

        URL testImageResource = DockerAdapterServiceTest.class.getResource(TEST_IMAGE_FILE);
        assertNotNull("Missing test resource: " + TEST_IMAGE_FILE, testImageResource);
        File file = new File(testImageResource.toURI());
        imageReference = UriUtils.buildPublicUri(host, TEST_IMAGE_URL_PATH);
        host.startService(Operation.createPost(imageReference), new FileContentService(file));
    }

    @Before
    public void setupVolumeState() throws Throwable {
        createTestDockerAuthCredentials();

        setupDockerVolumeAdapterService();

        createParentComputeState();

        ContainerVolumeDescription desc = createVolumeDescription(TEST_VOLUME_NAME);

        createVolumeState(desc);

        createVolume();
    }

    @Test
    public void testVolumeCreation() throws Throwable {
        verifyVolumeListContainsId(TEST_VOLUME_NAME);
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
        computeState.customProperties.put(ComputeConstants.HOST_AUTH_CREDNTIALS_PROP_NAME,
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

    protected ContainerVolumeDescription createVolumeDescription(String name) throws Throwable {
        ContainerVolumeDescription volumeDescription = new ContainerVolumeDescription();
        volumeDescription.name = name;
        volumeDescription.mountpoint = new File("/tmp");
        volumeDescription.documentSelfLink = UUID.randomUUID().toString();

        return doPost(volumeDescription, ContainerVolumeDescriptionService.FACTORY_LINK);
    }

    protected void createVolumeState(ContainerVolumeDescription desc) throws Throwable {
        waitForServiceAvailability(ContainerVolumeService.FACTORY_LINK);

        ContainerVolumeState containerState = new ContainerVolumeState();
        assertNotNull("parentLink", parentComputeStateLink);
        containerState.originatingHostReference = UriUtils.buildUri(host, parentComputeStateLink);
        containerState.descriptionLink = desc.documentSelfLink;
        containerState.name = desc.name;
        containerState.mountpoint = desc.mountpoint;
        List<String> tenantLinks = new ArrayList<String>();
        tenantLinks.add(TEST_GROUP);
        containerState.tenantLinks = tenantLinks;

        // add a custom property
        containerState.customProperties = new HashMap<>();
        containerState.customProperties.put(TEST_CUSTOM_PROP_NAME, TEST_CUSTOM_PROP_VALUE);

        ContainerVolumeState volume = doPost(containerState, ContainerVolumeService.FACTORY_LINK);
        volumeStateReference = UriUtils.extendUri(host.getUri(), volume.documentSelfLink);
    }

    public void setupDockerVolumeAdapterService() {

        dockerVolumeAdapterServiceUri = UriUtils.buildUri(host, DockerVolumeAdapterService.class);

        dockerVolumeAdapterService = new DockerVolumeAdapterService() {
            @Override
            protected DockerAdapterCommandExecutor getApiCommandExecutor() {
                return getTestCommandExecutor();
            }
        };

        host.startService(Operation.createPost(dockerVolumeAdapterServiceUri),
                dockerVolumeAdapterService);

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
     * Create a volume, store its id in this.containerId
     *
     * @throws Throwable
     */
    protected void createVolume() throws Throwable {

        sendContainerVolumeRequest(VolumeOperationType.CREATE);

        verifyVolumeStateExists(volumeStateReference);

        // get and validate the volume state
        sendGetVolumeStateRequest();

    }

    private void sendContainerVolumeRequest(VolumeOperationType type) throws Throwable {

        // create a fresh provisioning task for each request
        createProvisioningTask();

        ContainerVolumeRequest request = new ContainerVolumeRequest();
        request.resourceReference = volumeStateReference;
        request.operationTypeId = type.id;
        request.serviceTaskCallback = ServiceTaskCallback.create(provisioningTaskLink);

        Operation startContainer = Operation.createPatch(dockerVolumeAdapterServiceUri)
                .setReferer(URI.create("/"))
                .setBody(request).setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);
                    }

                    host.completeIteration();
                });

        host.testStart(1);
        host.send(startContainer);
        host.testWait();

        if (!isMockTarget()) {
            // in case of testing with a real docker server, give it some time
            // to settle
            Thread.sleep(2000L);
        }
    }

    protected void createProvisioningTask() throws Throwable {
        MockTaskState provisioningTask = new MockTaskState();
        provisioningTaskLink = doPost(provisioningTask,
                MockTaskFactoryService.SELF_LINK).documentSelfLink;
    }

    /*
     * Perform a query for the document self link to see that it exists in the index
     */
    protected void verifyVolumeStateExists(URI volumeStateReference) throws Throwable {

        host.createAndWaitSimpleDirectQuery(ServiceDocument.FIELD_NAME_SELF_LINK,
                volumeStateReference.getPath(), 0, 1);
    }

    private void sendGetVolumeStateRequest() throws Throwable {
        Operation getVolumeState = Operation.createGet(volumeStateReference)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.failIteration(ex);

                    } else {
                        host.completeIteration();
                    }
                });

        host.testStart(1);
        host.send(getVolumeState);
        host.testWait();
    }

    protected void verifyVolumeListContainsId(String volumeName) throws Throwable {

        CommandInput commandInput = new CommandInput().withDockerUri(getDockerVersionedUri())
                .withCredentials(getDockerCredentials());

        host.testStart(1);
        getTestCommandExecutor().listVolumes(commandInput, (o, ex) -> {
            if (ex != null) {
                host.failIteration(ex);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> body = o.getBody(List.class);
                host.log(Level.INFO, "Retrieved volumes list: %s", body);

                Set<Object> volumeNames = body.stream().map((item) -> item.get("Id"))
                        .collect(Collectors.toSet());

                try {
                    assertTrue(
                            String.format("Volume name [%s] not found in volumes list: %s ",
                                    volumeName, volumeNames),
                            volumeNames.contains(volumeName));
                    host.completeIteration();
                } catch (Throwable x) {
                    host.failIteration(x);
                }

            }
        });
        host.testWait();
    }

}
