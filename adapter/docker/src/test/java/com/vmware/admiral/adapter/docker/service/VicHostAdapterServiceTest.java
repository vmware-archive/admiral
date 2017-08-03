/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.UUID;

import javax.net.ssl.TrustManager;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.adapter.docker.mock.BaseMockDockerTestCase;
import com.vmware.admiral.adapter.docker.mock.MockDockerPathConstants;
import com.vmware.admiral.adapter.docker.mock.MockVicHostService;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.service.test.MockDockerHostAdapterService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.CertificateUtil;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;

public class VicHostAdapterServiceTest extends BaseMockDockerTestCase {

    private DockerAdapterCommandExecutor commandExecutor;

    @Before
    public void startServices() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);

        mockDockerHost.startService(
                Operation.createPost(UriUtils.buildUri(
                        mockDockerHost, MockVicHostService.SELF_LINK)),
                new MockVicHostService());
    }

    @Before
    public void setUp() throws Throwable {
        createTestDockerAuthCredentials();
        createVicHostComputeState();
        createProvisioningTask();

        setupDockerAdapterService();

        mockDockerHost.startService(
                Operation.createPost(UriUtils.buildUri(
                        mockDockerHost,
                        MockVicHostService.SELF_LINK + MockDockerPathConstants.INFO)),
                new MockVicHostService());
    }

    @Test
    public void testHostInfoVicHost() throws Throwable {
        dockerHostState = requestDockerHostOperation(MockDockerPathConstants.INFO,
                ContainerHostOperationType.INFO);

        // Verify mem / cpu statistics are computed for /info request in vic host
        String availableMemory = dockerHostState.customProperties
                .get(ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME);
        String cpuUsage = dockerHostState.customProperties
                .get(ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME);
        assertNotNull(availableMemory);
        assertNotNull(cpuUsage);
        assertEquals(4.0 * 1024 * 1024 * 1024, Double.parseDouble(availableMemory), 0.01);
        assertEquals(30.0, Double.parseDouble(cpuUsage), 0.01);
    }

    protected void createVicHostComputeState() throws Throwable {
        waitForServiceAvailability(ComputeService.FACTORY_LINK);

        ComputeDescription computeDescription = new ComputeDescription();
        computeDescription.customProperties = new HashMap<>();
        computeDescription.id = UUID.randomUUID().toString();
        String computeDescriptionLink = doPost(computeDescription,
                ComputeDescriptionService.FACTORY_LINK).documentSelfLink;

        ComputeState computeState = new ComputeState();
        computeState.id = "testVCHComputeState";
        computeState.descriptionLink = computeDescriptionLink;
        computeState.customProperties = new HashMap<>();
        computeState.customProperties.put(
                ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME, testDockerCredentialsLink);
        computeState.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());
        computeState.customProperties.put(
                MockDockerHostAdapterService.DOCKER_INFO_STORAGE_DRIVER_PROP_NAME,
                "vSphere Integrated Containers");

        computeState.address = dockerUri.toString();
        dockerHostState = doPost(computeState, ComputeService.FACTORY_LINK);
    }

    public void setupDockerAdapterService() {
        dockerHostAdapterServiceUri = UriUtils.buildUri(host, DockerHostAdapterService.class);

        DockerHostAdapterService dockerHostAdapterService = new DockerHostAdapterService() {
            @Override
            protected DockerAdapterCommandExecutor getCommandExecutor() {
                return getTestCommandExecutor();
            }
        };

        host.startService(Operation.createPost(dockerHostAdapterServiceUri),
                dockerHostAdapterService);
        host.startService(Operation.createPost(dockerHostAdapterServiceUri),
                new DockerHostAdapterService());

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
