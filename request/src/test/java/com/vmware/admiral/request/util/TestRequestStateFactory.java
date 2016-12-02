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

package com.vmware.admiral.request.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ConfigureHostOverSshTaskService;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class TestRequestStateFactory extends CommonTestStateFactory {
    public static final String COMPUTE_DESC_ID = "test-compute-desc-id";
    public static final String VM_GUEST_COMPUTE_DESC_ID = "test-vm-guest-compute-desc-id";
    public static final String COMPUTE_ADDRESS = "somehost";
    public static final String COMPOSITE_DESC_PARENT_LINK = "test-parent-desc-link";
    public static final String CONTAINER_DESC_LINK_NAME = "dcp-test-docker-desc";
    public static final String CONTAINER_DESC_IMAGE = "dcp-test:latest";
    public static final String CONTAINER_VOLUME_DRIVER = "flocker";
    public static final String CONTAINER_DESC_LINK = UriUtils.buildUriPath(
            ContainerDescriptionService.FACTORY_LINK, CONTAINER_DESC_LINK_NAME);
    public static final URI CONTAINER_IMAGE_REF = URI
            .create("https://enatai-jenkins.eng.vmware.com/job/docker-dcp-test/lastSuccessfulBuild/artifact/docker-dcp-test/dcp-test:latest.tgz");
    public static final String RESOURCE_POOL_ID = "test-docker-host-resource-pool";
    public static final String DOCKER_COMPUTE_DESC_ID = "test-docker-host-compute-desc-id";

    public static final String TENANT_NAME = "admiral";
    public static final String GROUP_NAME_DEVELOPMENT = "Development";
    public static final String GROUP_NAME_FINANCE = "Finance";
    public static final String USER_NAME = "fritz";

    public static final String TENANT_LINK_TEMPLATE = "/tenants/%s";
    public static final String TENANT_LINK_WITH_GROUP_TEMPLATE = "/tenants/%s/groups/%s";
    public static final String USER_LINK_TEMPLATE = "/users/%s";

    public static final String MOCK_COMPUTE_HOST_SERVICE_SELF_LINK = "/mock-boot-service";

    public static CompositeDescription createCompositeDescription(boolean isCloned) {
        CompositeDescription desc = new CompositeDescription();
        desc.name = "composite-admiral-test";
        desc.descriptionLinks = new ArrayList<>();

        if (isCloned) {
            desc.parentDescriptionLink = COMPOSITE_DESC_PARENT_LINK;
        }
        return desc;
    }

    public static CompositeDescription createCompositeDescription() {
        return createCompositeDescription(false);
    }

    public static ContainerDescription createContainerDescription() {
        return createContainerDescription("admiral-test");
    }

    public static ContainerDescription createContainerDescription(String name, boolean isCloned,
            boolean exposePorts) {
        ContainerDescription containerDesc = new ContainerDescription();
        containerDesc.documentSelfLink = CONTAINER_DESC_LINK_NAME;
        containerDesc.name = name;
        containerDesc.tenantLinks = Collections.singletonList("test-group");
        containerDesc.image = CONTAINER_DESC_IMAGE;
        containerDesc.imageReference = CONTAINER_IMAGE_REF;
        containerDesc.command = new String[] { "/opt/dcp/bin/dcp-test.sh" };
        containerDesc.memoryLimit = ContainerDescriptionService.getContainerMinMemoryLimit();
        containerDesc.memorySwapLimit = 0L;
        containerDesc.cpuShares = 5;

        if (exposePorts) {
            containerDesc.portBindings = Arrays.stream(new String[] {
                    "5000:5000",
                    "127.0.0.1::20080",
                    "127.0.0.1:20080:80",
                    "1234:1234/tcp" })
                    .map((s) -> PortBinding.fromDockerPortMapping(DockerPortMapping.fromString(s)))
                    .collect(Collectors.toList())
                    .toArray(new PortBinding[0]);
        }

        containerDesc.dns = new String[] { "0.0.0.0" };
        containerDesc.env = new String[] {
                "ENV_VAR=value",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/local/go/bin"
                        +
                        ":/go/bin" };
        containerDesc.extraHosts = new String[] { "test-hostname:ip" };
        containerDesc.command = new String[] { "/usr/lib/postgresql/9.3/bin/postgres" };
        containerDesc.entryPoint = new String[] { "./dockerui" };
        containerDesc.volumes = new String[] { "/var/run/docker.sock:/var/run/docker.sock" };
        containerDesc.volumeDriver = CONTAINER_VOLUME_DRIVER;
        // containerDesc.volumesFrom = new String[] { "parent", "other:ro" };
        containerDesc.workingDir = "/container";
        // containerDesc.links = new String[] { "name:alias" };
        containerDesc.capAdd = new String[] { "NET_ADMIN" };
        containerDesc.device = new String[] { "/dev/sdc:/dev/xvdc:rwm" };
        // containerDesc.serviceLinks = new String[] { "mydep:alias" };
        containerDesc.customProperties = new HashMap<>(1);
        containerDesc.customProperties.put("propKey string", "customPropertyValue string");

        if (isCloned) {
            containerDesc.parentDescriptionLink = COMPOSITE_DESC_PARENT_LINK;
        }

        return containerDesc;
    }

    public static ContainerDescription createContainerDescription(String name) {
        return createContainerDescription(name, false, true);
    }

    public static ContainerNetworkDescription createContainerNetworkDescription(String name) {
        ContainerNetworkDescription desc = new ContainerNetworkDescription();
        desc.documentSelfLink = "test-network-" + name;
        desc.name = name;
        desc.tenantLinks = Collections.singletonList("test-group");
        desc.customProperties = new HashMap<>();
        return desc;
    }

    public static ContainerVolumeDescription createContainerVolumeDescription(String name) {
        ContainerVolumeDescription desc = new ContainerVolumeDescription();
        desc.documentSelfLink = "test-volume-" + name;
        desc.name = name;
        desc.tenantLinks = Collections.singletonList("test-group");
        desc.customProperties = new HashMap<>();
        return desc;
    }

    public static ResourcePoolState createResourcePool() {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.name = RESOURCE_POOL_ID;
        poolState.id = poolState.name;
        poolState.documentSelfLink = poolState.id;
        poolState.maxCpuCount = 1600L;
        poolState.minCpuCount = 16L;
        poolState.currencyUnit = "Bitcoin";
        poolState.maxCpuCostPerMinute = 1.0;
        poolState.maxDiskCostPerMinute = 1.0;
        poolState.minMemoryBytes = 1024L * 1024L * 1024L * 46L;
        poolState.maxMemoryBytes = poolState.minMemoryBytes * 2;
        poolState.minDiskCapacityBytes = poolState.maxDiskCapacityBytes = 1024L * 1024L * 1024L
                * 1024L;
        poolState.customProperties = new HashMap<>(3);
        poolState.customProperties.put(ComputeConstants.ENDPOINT_AUTH_CREDNTIALS_PROP_NAME,
                CommonTestStateFactory.AUTH_CREDENTIALS_ID);
        poolState.customProperties.put(
                ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                UriUtils.buildUriPath(EndpointService.FACTORY_LINK, ENDPOINT_ID));

        return poolState;
    }

    public static RequestBrokerState createRequestState() {
        return createRequestState(ResourceType.CONTAINER_TYPE.getName(), CONTAINER_DESC_LINK);
    }

    public static RequestBrokerState createComputeRequestState() {
        return createRequestState(ResourceType.COMPUTE_TYPE.getName(), COMPUTE_DESC_ID);
    }

    public static RequestBrokerState createConfigureHostState() {
        HashMap<String, String> customProperties = new HashMap<>();
        customProperties.put(ConfigureHostOverSshTaskService.CONFIGURE_HOST_ADDRESS_CUSTOM_PROP,
                "https://127.0.0.1:2376");
        customProperties.put(
                ConfigureHostOverSshTaskService.CONFIGURE_HOST_AUTH_CREDENTIALS_LINK_CUSTOM_PROP,
                AuthCredentialsService.FACTORY_LINK + "/"
                        + CommonTestStateFactory.AUTH_CREDENTIALS_ID);
        customProperties.put(
                ConfigureHostOverSshTaskService.CONFIGURE_HOST_PLACEMENT_ZONE_LINK_CUSTOM_PROP,
                ResourcePoolService.FACTORY_LINK + "/" + RESOURCE_POOL_ID);
        return createRequestState(ResourceType.CONFIGURE_HOST_TYPE.getName(), null,
                RequestBrokerState.CONFIGURE_HOST_OPERATION, customProperties);
    }

    public static RequestBrokerState createRequestState(String resourceType,
            String resourceDescriptionLink) {
        return createRequestState(resourceType, resourceDescriptionLink, null, new HashMap<>());
    }

    public static RequestBrokerState createRequestState(String resourceType,
            String resourceDescriptionLink, String operation,
            HashMap<String, String> customProperties) {
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = resourceType;
        request.resourceDescriptionLink = resourceDescriptionLink;
        request.tenantLinks = createTenantLinks(TENANT_NAME);
        request.operation = operation;
        request.customProperties = customProperties;
        return request;
    }

    public static GroupResourcePlacementState createGroupResourcePlacementState() {
        return createGroupResourcePlacementState(ResourceType.CONTAINER_TYPE);
    }

    public static GroupResourcePlacementState createGroupResourcePlacementState(
            ResourceType resourceType) {
        GroupResourcePlacementState rsrvState = new GroupResourcePlacementState();
        rsrvState.resourcePoolLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK,
                RESOURCE_POOL_ID);
        rsrvState.tenantLinks = createTenantLinks(TENANT_NAME);
        rsrvState.name = "test-reservation";
        rsrvState.maxNumberInstances = 20;
        rsrvState.memoryLimit = 0L;
        rsrvState.cpuShares = 3;
        rsrvState.resourceType = resourceType.getName();
        return rsrvState;
    }

    public static ComputeDescription createDockerHostDescription() {
        ComputeDescription hostDescription = new ComputeDescription();

        // create compute host descriptions first, then link them in the compute host state
        hostDescription.name = UUID.randomUUID().toString();
        hostDescription.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        hostDescription.id = UUID.randomUUID().toString();
        hostDescription.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));
        hostDescription.documentSelfLink = hostDescription.id;
        hostDescription.authCredentialsLink = UriUtils.buildUriPath(
                AuthCredentialsService.FACTORY_LINK,
                CommonTestStateFactory.AUTH_CREDENTIALS_ID);
        hostDescription.instanceType = "linux";
        hostDescription.customProperties = new HashMap<>();
        hostDescription.customProperties.put(
                ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "linux");

        return hostDescription;
    }

    public static ComputeState createDockerComputeHost() {
        ComputeState cs = new ComputeState();
        cs.id = DOCKER_COMPUTE_ID;
        cs.primaryMAC = UUID.randomUUID().toString();
        cs.address = COMPUTE_ADDRESS; // this will be used for ssh to access the host
        cs.powerState = PowerState.ON;
        cs.descriptionLink = UriUtils.buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                DOCKER_COMPUTE_DESC_ID);
        cs.resourcePoolLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK,
                RESOURCE_POOL_ID);
        cs.adapterManagementReference = URI.create("http://localhost:8081");
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME,
                UriUtils.buildUriPath(
                        AuthCredentialsService.FACTORY_LINK, AUTH_CREDENTIALS_ID));
        return cs;
    }

    public static ComputeDescription createVmGuestComputeDescription() {
        ComputeDescription computeDescription = new ComputeDescription();

        // create compute host descriptions first, then link them in the compute host state
        computeDescription.name = "test-vm-guest-compute-description";
        computeDescription.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        computeDescription.id = UUID.randomUUID().toString();
        computeDescription.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.VM_GUEST.toString()));
        computeDescription.documentSelfLink = VM_GUEST_COMPUTE_DESC_ID;
        computeDescription.authCredentialsLink = UriUtils.buildUriPath(
                AuthCredentialsService.FACTORY_LINK,
                CommonTestStateFactory.AUTH_CREDENTIALS_ID);
        computeDescription.zoneId = ENDPOINT_REGION_ID;
        computeDescription.customProperties = new HashMap<>();

        return computeDescription;
    }

    public static ComputeState createVmGuestComputeState() {
        ComputeState cs = new ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.primaryMAC = UUID.randomUUID().toString();
        cs.address = COMPUTE_ADDRESS; // this will be used for ssh to access the host
        cs.powerState = PowerState.ON;
        cs.descriptionLink = UriUtils.buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                VM_GUEST_COMPUTE_DESC_ID);
        cs.resourcePoolLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK,
                RESOURCE_POOL_ID);
        cs.adapterManagementReference = URI.create("http://localhost:8081");
        cs.customProperties = new HashMap<>();
        return cs;
    }

    public static ContainerState createContainer() {
        ContainerState cont = new ContainerState();
        cont.parentLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK, DOCKER_COMPUTE_ID);
        cont.address = COMPUTE_ADDRESS;
        cont.powerState = com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState.RUNNING;

        return cont;
    }

    public static ContainerNetworkState createNetwork(String name) {
        ContainerNetworkState net = new ContainerNetworkState();
        net.name = name;
        net.parentLinks = new ArrayList<>(Arrays.asList(
                UriUtils.buildUriPath(ComputeService.FACTORY_LINK, DOCKER_COMPUTE_ID)));

        return net;
    }

    public static ComputeState createCompute() {
        ComputeState compute = new ComputeState();
        return compute;
    }

    public static EndpointState createEndpoint() {
        EndpointState endpoint = new EndpointState();
        endpoint.documentSelfLink = UriUtils.buildUriPath(EndpointService.FACTORY_LINK,
                ENDPOINT_ID);
        endpoint.endpointType = "aws";
        endpoint.name = ENDPOINT_ID;
        endpoint.endpointProperties = new HashMap<>();
        endpoint.endpointProperties.put("privateKeyId", "testId");
        endpoint.endpointProperties.put("privateKey",
                getFileContent("docker-host-private-key.PEM"));
        endpoint.endpointProperties.put("regionId", ENDPOINT_REGION_ID);
        endpoint.endpointProperties.put("hostName", "127.0.0.1");

        return endpoint;
    }

    public static List<String> createTenantLinks(String tenant) {

        return createTenantLinks(tenant, null);
    }

    public static List<String> createTenantLinks(String tenant, String subTenant) {

        if (subTenant == null) {
            return new ArrayList<>(
                    Arrays.asList(String.format(TENANT_LINK_TEMPLATE, tenant)));
        }

        return new ArrayList<>(
                Arrays.asList(String.format(TENANT_LINK_TEMPLATE, tenant),
                        String.format(TENANT_LINK_WITH_GROUP_TEMPLATE, tenant, subTenant)));
    }

    public static List<String> createTenantLinks(String tenant, String subTenant, String user) {
        List<String> tenantLinks = new ArrayList<>(3);
        if (tenant != null) {
            tenantLinks.add(String.format(TENANT_LINK_TEMPLATE, tenant));
        }
        if (subTenant != null) {
            tenantLinks.add(String.format(TENANT_LINK_WITH_GROUP_TEMPLATE, tenant, subTenant));
        }
        if (user != null) {
            tenantLinks.add(String.format(USER_LINK_TEMPLATE, user));
        }

        return tenantLinks;
    }
}
