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

package com.vmware.admiral.test.integration.data;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class IntegratonTestStateFactory extends CommonTestStateFactory {
    public static final long DEFAULT_DOCUMENT_EXPIRATION_MICROS = Long.getLong(
            "dcp.document.test.expiration.time.seconds", TimeUnit.MINUTES.toMicros(30));
    public static final String AUTH_CREDENTIALS_ID = "test-credentials-id";
    public static final String COMPUTE_DESC_ID = "test-continaer-compute-desc-id";
    public static final String REGISTRATION_DOCKER_ID = "test-docker-registration-id";
    public static final String DOCKER_HOST_REGISTRATION_NAME = "docker-host";
    public static final String DOCKER_COMPUTE_ID = "test-docker-host-compute-id";

    public static final String CONTAINER_DESC_LINK_NAME = "dcp-test-docker-desc";
    public static final String CONTAINER_DCP_TEST_LATEST_NAME = "docker-dcp-test";
    public static final String CONTAINER_DCP_TEST_LATEST_ID = "dcp-test:latest-id";
    public static final String CONTAINER_DCP_TEST_LATEST_IMAGE = "dcp-test:latest";
    public static final String CONTAINER_ADMIRAL_IMAGE = "vmware/bellevue";
    public static final String CONTAINER_DCP_TEST_LATEST_COMMAND = "/opt/dcp/bin/dcp-test.sh";
    public static final String CONTAINER_IMAGE_DOWNLOAD_URL_FORMAT = "%s/%s.tgz";
    public static final String RESOURCE_POOL_ID = "test-docker-host-resource-pool";
    public static final String GLOBAL_TEST_RESERVATION_ID = "test-global-test-reservation-docker-host";
    public static final String DOCKER_COMPUTE_DESC_ID = "test-docker-host-compute-desc-id";
    public static final String GROUP = "test-group";

    public static void addDefaultDocumentTimeout(ServiceDocument serviceDocument) {
        serviceDocument.documentExpirationTimeMicros = Utils.getNowMicrosUtc()
                + IntegratonTestStateFactory.DEFAULT_DOCUMENT_EXPIRATION_MICROS;
    }

    public static ResourcePoolState createResourcePool() {
        ResourcePoolState poolState = new ResourcePoolState();
        poolState.documentSelfLink = RESOURCE_POOL_ID;
        poolState.name = RESOURCE_POOL_ID;
        poolState.id = poolState.name;
        poolState.documentSelfLink = poolState.name;
        poolState.documentSelfLink = poolState.id;
        poolState.maxCpuCount = 1600L;
        poolState.minCpuCount = 16L;
        poolState.currencyUnit = "Bitcoin";
        poolState.maxCpuCostPerMinute = 1.0;
        poolState.maxDiskCostPerMinute = 1.0;
        poolState.minMemoryBytes = 1024L * 1024L * 1024L * 46L;
        poolState.maxMemoryBytes = poolState.minMemoryBytes * 2;
        poolState.minDiskCapacityBytes = poolState.maxDiskCapacityBytes = 1024L * 1024L * 1024L * 1024L;
        poolState.tenantLinks = new ArrayList<String>();

        addDefaultDocumentTimeout(poolState);

        return poolState;
    }

    public static ComputeDescription createDockerHostDescription() {
        ComputeDescription hostDescription = new ComputeDescription();
        hostDescription.environmentName = ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        hostDescription.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.toString()));
        hostDescription.documentSelfLink = DOCKER_COMPUTE_DESC_ID;
        hostDescription.id = DOCKER_COMPUTE_DESC_ID;
        hostDescription.instanceAdapterReference = UriUtils.buildUri(ServiceHost.LOCAL_HOST, 8484,
                "compute-test-adapter", null); // not a real references

        addDefaultDocumentTimeout(hostDescription);
        return hostDescription;
    }

    public static ComputeState createDockerComputeHost() {
        ComputeState cs = new ComputeState();
        cs.id = DOCKER_COMPUTE_ID;
        cs.documentSelfLink = cs.id;
        cs.primaryMAC = UUID.randomUUID().toString();
        cs.address = "ssh://somehost:22"; // this will be used for ssh to access the host
        cs.powerState = PowerState.ON;
        cs.resourcePoolLink = GroupResourcePlacementService.DEFAULT_RESOURCE_POOL_LINK;
        cs.adapterManagementReference = URI.create("http://localhost:8081"); // not real reference
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                ContainerHostService.DockerAdapterType.API.name());

        addDefaultDocumentTimeout(cs);
        return cs;
    }

    public static GroupResourcePlacementState createGroupResourcePlacementState() {
        GroupResourcePlacementState rsrvState = new GroupResourcePlacementState();
        rsrvState.documentSelfLink = GLOBAL_TEST_RESERVATION_ID;
        rsrvState.resourcePoolLink = UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK, RESOURCE_POOL_ID);
        rsrvState.name = "test-reservation";
        rsrvState.maxNumberInstances = 10;

        addDefaultDocumentTimeout(rsrvState);
        return rsrvState;
    }
}
