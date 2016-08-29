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

package com.vmware.admiral.test.integration.compute.aws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.Ignore;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

@Ignore
public class AwsDockerHostProvisionIT extends AwsComputeProvisionIT {
    public static final String CONTAINER_DCP_TEST_LATEST_ID = "dcp-test:latest-id";
    public static final String CONTAINER_DCP_TEST_LATEST_IMAGE = "kitamtic/hello-world-nginx";
    public static final String CONTAINER_DCP_TEST_LATEST_NAME = "docker-dcp-test";
    private static final String[] TEST_COMMAND = { "/etc/hosts", "-" };
    private static final String TEST_PORT_BINDINGS = "127.0.0.1::8282/tcp";
    private static final String TEST_ENV_PROP = "TEST_PROP WITH SPACE=testValue with space ' \" \\' attempt injection";
    private static final String[] TEST_ENV = { TEST_ENV_PROP };
    private static final String TEST_RESTART_POLICY_NAME = "on-failure";
    private static final int TEST_RESTART_POLICY_RETRIES = 3;
    private static final String TEST_USER = "root";
    // private static final int TEST_MEMORY_LIMIT = 5_000_000;
    // private static final int TEST_MEMORY_SWAP = -1;
    private static final int TEST_CPU_SHARES = 512;
    private static final String[] TEST_DNS = { "8.8.8.8", "9.9.9.9" };
    private static final String[] TEST_DNS_SEARCH = { "eng.vmware.com", "vmware.com" };
    private static final String[] TEST_ENTRY_POINT = { "/bin/cat" }; // more than one elements is
    // ignored in SSH adapter

    private static final String[] TEST_VOLUMES = { "/tmp:/mnt/tmp:ro" };
    private static final String[] TEST_CAP_ADD = { "NET_ADMIN" };
    private static final String[] TEST_CAP_DROP = { "MKNOD" };
    private static final String[] TEST_DEVICES = { "/dev/null:/dev/null2:rwm" };
    private static final String TEST_HOSTNAME = "test-hostname";
    private static final String TEST_DOMAINNAME = "eng.vmware.com";
    private static final String[] TEST_EXTRA_HOSTS = { "vmware-vra:10.148.85.240" };
    private static final String TEST_WORKING_DIR = "/tmp";
    private static final boolean TEST_PRIVILEGED = true;

    private AuthCredentialsServiceState dockerRemoteApiClientCredentials;

    private final Set<String> containersToDelete = new HashSet<>();

    @Override
    public void baseTearDown() throws Exception {
        Iterator<String> it = containersToDelete.iterator();
        while (it.hasNext()) {
            String containerLink = it.next();
            ContainerState containerState = getDocument(containerLink, ContainerState.class);
            if (containerState == null) {
                logger.warning(String.format("Unable to find container %s", containerLink));
                continue;
            }

            try {
                logger.info("---------- Clean up: Request Delete the container instance. --------");
                requestContainerDelete(Collections.singletonList(containerLink), false);
            } catch (Throwable t) {
                logger.warning(String.format("Unable to remove container %s: %s", containerLink,
                        t.getMessage()));
            }
        }
        super.baseTearDown();
    }
    @Override
    protected void doWithResources(List<String> resourceLinks) throws Throwable {
        validateHostState(resourceLinks);
        ContainerDescription containerDescription = createContainerDescription();
        requestContainerAndDelete(containerDescription.documentSelfLink);
    }

    @Override
    protected void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception {
        super.extendComputeDescription(computeDescription);
        computeDescription.customProperties.put(ComputeConstants.HOST_AUTH_CREDNTIALS_PROP_NAME,
                dockerRemoteApiClientCredentials.documentSelfLink);

        computeDescription.customProperties
                .put(ComputeAllocationTaskState.FIELD_NAME_CUSTOM_PROP_IMAGE_ID_NAME, "coreos");

        computeDescription.customProperties.put(ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME,
                "true");
        // Set DockerSpecific properties
        computeDescription.customProperties.put(
                ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                DockerAdapterType.API.name());
        computeDescription.customProperties.put(ContainerHostService.DOCKER_HOST_PORT_PROP_NAME,
                "2376");
        computeDescription.customProperties.put(ContainerHostService.DOCKER_HOST_SCHEME_PROP_NAME,
                "https");

        String configContent = getConfigContent();
        if (configContent != null) {
            computeDescription.customProperties
                    .put(ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME, configContent);
        }

    }

    @Override
    protected void doSetUp() throws Exception {
        AuthCredentialsServiceState auth = new AuthCredentialsServiceState();
        auth.privateKey = CommonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.key.file"));
        auth.publicKey = CommonTestStateFactory
                .getFileContent(getTestRequiredProp("docker.client.cert.file"));
        auth.documentSelfLink = UUID.randomUUID().toString();

        dockerRemoteApiClientCredentials = postDocument(AuthCredentialsService.FACTORY_LINK, auth,
                documentLifeCycle);
    }

    private String getConfigContent() {
        return CommonTestStateFactory
                .getFileContent(getTestRequiredProp("cloudinit.content.file"));
    }

    protected void requestContainerAndDelete(String resourceDescLink) throws Exception {
        logger.info("********************************************************************");
        logger.info("---------- Create RequestBrokerState and start the request --------");
        logger.info("********************************************************************");

        logger.info("---------- 1. Request container instance. --------");
        RequestBrokerState request = requestContainer(resourceDescLink);

        logger.info(
                "---------- 2. Verify the request is successful and container instance is created. --------");
        validateAfterStart(resourceDescLink, request);

        logger.info("---------- 3. Request Delete the container instance. --------");
        requestContainerDelete(request.resourceLinks, true);
    }

    protected RequestBrokerState requestContainer(String resourceDescLink)
            throws Exception {

        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_TYPE.getName();
        request.resourceDescriptionLink = resourceDescLink;
        request.tenantLinks = getTenantLinks();
        request = postDocument(RequestBrokerFactoryService.SELF_LINK, request);

        waitForTaskToComplete(request.documentSelfLink);

        request = getDocument(request.documentSelfLink, RequestBrokerState.class);
        for (String containerLink : request.resourceLinks) {
            containersToDelete.add(containerLink);
        }

        return request;
    }

    protected void requestContainerDelete(List<String> resourceLinks, boolean verifyDelete)
            throws Exception {

        RequestBrokerState day2DeleteRequest = new RequestBrokerState();
        day2DeleteRequest.resourceType = ResourceType.CONTAINER_TYPE.getName();
        day2DeleteRequest.operation = ContainerOperationType.DELETE.id;
        day2DeleteRequest.resourceLinks = resourceLinks;
        day2DeleteRequest = postDocument(RequestBrokerFactoryService.SELF_LINK, day2DeleteRequest);

        waitForTaskToComplete(day2DeleteRequest.documentSelfLink);

        if (!verifyDelete) {
            return;
        }

        for (String containerLink : resourceLinks) {
            ContainerState conState = getDocument(containerLink, ContainerState.class);
            assertNull(conState);
            String computeStateLink = UriUtils
                    .buildUriPath(ComputeService.FACTORY_LINK, extractId(containerLink));
            ComputeState computeState = getDocument(computeStateLink, ComputeState.class);
            assertNull(computeState);
            containersToDelete.remove(containerLink);
        }
    }

    protected void validateHostState(List<String> resourceLinks)
            throws Exception {
        String computeStateLink = resourceLinks.get(0);
        ComputeState computeState = getDocument(computeStateLink, ComputeState.class);

        assertNotNull(computeState);
        assertEquals(com.vmware.photon.controller.model.resources.ComputeService.PowerState.ON,
                computeState.powerState);
    }

    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {
        String containerStateLink = request.resourceLinks.get(0);
        ContainerState containerState = getDocument(containerStateLink, ContainerState.class);

        assertNotNull(containerState);
        assertEquals(PowerState.RUNNING, containerState.powerState);
        assertEquals(resourceDescLink, containerState.descriptionLink);
    }

    private ContainerDescription createContainerDescription() throws Exception {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.documentSelfLink = CONTAINER_DCP_TEST_LATEST_ID;

        containerDesc.image = CONTAINER_DCP_TEST_LATEST_IMAGE;

        containerDesc.customProperties = new HashMap<String, String>();

        containerDesc.name = CONTAINER_DCP_TEST_LATEST_NAME;
        containerDesc.command = TEST_COMMAND;
        containerDesc.instanceAdapterReference = URI
                .create(getBaseUrl() + buildServiceUri(ManagementUriParts.ADAPTER_DOCKER));

        containerDesc.portBindings = new PortBinding[] { PortBinding
                .fromDockerPortMapping(DockerPortMapping.fromString(TEST_PORT_BINDINGS)) };

        containerDesc.logConfig = createLogConfig();
        containerDesc.env = TEST_ENV;
        containerDesc.restartPolicy = TEST_RESTART_POLICY_NAME;
        containerDesc.maximumRetryCount = TEST_RESTART_POLICY_RETRIES;
        containerDesc.user = TEST_USER;
        // TODO: Enable once the Reservations, ResourcePool properties and host stats
        /// gathering are implemented fully
        // containerDesc.memoryLimit = TEST_MEMORY_LIMIT;
        // containerDesc.memorySwap = TEST_MEMORY_SWAP;
        containerDesc.cpuShares = TEST_CPU_SHARES;
        containerDesc.dns = TEST_DNS;
        containerDesc.dnsSearch = TEST_DNS_SEARCH;
        containerDesc.entryPoint = TEST_ENTRY_POINT;
        containerDesc.volumes = TEST_VOLUMES;
        containerDesc.capAdd = TEST_CAP_ADD;
        containerDesc.capDrop = TEST_CAP_DROP;
        containerDesc.device = TEST_DEVICES;
        containerDesc.hostname = TEST_HOSTNAME;
        containerDesc.domainName = TEST_DOMAINNAME;
        containerDesc.extraHosts = TEST_EXTRA_HOSTS;
        containerDesc.workingDir = TEST_WORKING_DIR;
        containerDesc.privileged = TEST_PRIVILEGED;
        containerDesc = postDocument(ContainerDescriptionService.FACTORY_LINK, containerDesc);

        return containerDesc;
    }

    private LogConfig createLogConfig() {
        LogConfig logConfig = new LogConfig();
        logConfig.type = "json-file";
        logConfig.config = new HashMap<>();
        logConfig.config.put("max-size", "200k");
        return logConfig;
    }
}
