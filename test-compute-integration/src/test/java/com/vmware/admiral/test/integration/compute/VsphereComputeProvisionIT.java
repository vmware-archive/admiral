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

package com.vmware.admiral.test.integration.compute;

import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;

import com.vmware.admiral.compute.EnvironmentMappingService;
import com.vmware.admiral.compute.EnvironmentMappingService.EnvironmentMappingState;
import com.vmware.admiral.compute.PropertyMapping;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.endpoint.EndpointService.EndpointState;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ComputeOperationType;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;

public class VsphereComputeProvisionIT extends BaseComputeProvisionIT {
    public static final String VC_USERNAME = "test.vsphere.username";
    public static final String VC_PASSWORD = "test.vsphere.password";
    public static final String VC_HOST = "test.vsphere.hostname";
    public static final String VC_RESOURCE_POOL_ID = "test.vsphere.resource.pool.path";
    public static final String VC_DATACENTER_ID = "test.vsphere.datacenter";
    public static final String VC_DATASTORE_ID = "test.vsphere.datastore.path";
    public static final String VC_NETWORK_ID = "test.vsphere.network.id";
    public static final String VC_TARGET_FOLDER_PATH = "test.vsphere.vm.folder";
    public static final String VC_VM_DISK_URI = "test.vsphere.disk.uri";

    @Override
    protected EndpointType getEndpointType() {
        return EndpointType.vsphere;
    }

    @Override
    protected void doWithResources(List<String> resourceLinks) throws Throwable {
        executeDay2(resourceLinks, ComputeOperationType.POWER_ON);
        waitForComputePowerState(PowerState.ON, resourceLinks);

        executeDay2(resourceLinks, ComputeOperationType.POWER_OFF);
        waitForComputePowerState(PowerState.OFF, resourceLinks);
    }

    private void executeDay2(List<String> resourceLinks, ComputeOperationType computeOperation)
            throws Throwable, Exception {
        RequestBrokerState day2StartRequest = new RequestBrokerState();
        day2StartRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
        day2StartRequest.resourceLinks = resourceLinks;
        day2StartRequest.operation = computeOperation.id;
        day2StartRequest = executeRequest(day2StartRequest);

        day2StartRequest = getDocument(day2StartRequest.documentSelfLink, RequestBrokerState.class);
    }

    protected RequestBrokerState executeRequest(RequestBrokerState requestBrokerState)
            throws Throwable {
        RequestBrokerState request = postDocument(RequestBrokerFactoryService.SELF_LINK,
                requestBrokerState);

        waitForTaskToComplete(request.documentSelfLink);
        return request;
    }

    private void waitForComputePowerState(final PowerState expectedPowerState,
            List<String> computeLinks) throws Throwable {
        assertNotNull(computeLinks);
        waitFor(() -> {
            for (String computeLink : computeLinks) {
                ComputeState computeState = getDocument(computeLink, ComputeState.class);
                if (computeState.powerState == expectedPowerState) {
                    continue;
                }
                logger.info(
                        "Container PowerState is: %s. Expected powerState: %s. Retrying for container: %s...",
                        computeState.powerState, expectedPowerState,
                        computeState.documentSelfLink);
                return false;
            }
            return true;
        });
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        endpoint.privateKeyId = getTestRequiredProp(VC_USERNAME);
        endpoint.privateKey = getTestRequiredProp(VC_PASSWORD);
        endpoint.regionId = getTestRequiredProp(VC_DATACENTER_ID);
        endpoint.endpointHost = getTestRequiredProp(VC_HOST);
    }

    @Override
    protected void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception {
        computeDescription.dataStoreId = getTestRequiredProp(VC_DATASTORE_ID);
        computeDescription.networkId = getTestRequiredProp(VC_NETWORK_ID);
        computeDescription.zoneId = getTestRequiredProp(VC_RESOURCE_POOL_ID);
        String vmFolder = getTestProp(VC_TARGET_FOLDER_PATH);
        if (vmFolder != null) {
            computeDescription.customProperties.put(ComputeProperties.RESOURCE_GROUP_NAME,
                    vmFolder);
        }
    }

    @Override
    protected void doSetUp() throws Exception {
        EnvironmentMappingState ems = new EnvironmentMappingState();
        ems.endpointType = getEndpointType().name();
        ems.name = ems.endpointType;
        ems.properties = new HashMap<>();

        PropertyMapping instanceType = new PropertyMapping();
        instanceType.mappings = new HashMap<>();
        instanceType.mappings.put("small", "small");
        ems.properties.put("instanceType", instanceType);

        PropertyMapping imageRefs = new PropertyMapping();
        imageRefs.mappings = new HashMap<>();
        imageRefs.mappings.put("linux", getDiskUri());
        imageRefs.mappings.put("coreos", getDiskUri());
        ems.properties.put("imageType", imageRefs);

        postDocument(EnvironmentMappingService.FACTORY_LINK,
                ems, documentLifeCycle);
    }

    private String getDiskUri() {
        String diskUri = getTestProp(VC_VM_DISK_URI);
        if (diskUri == null) {
            return null;
        } else {
            return diskUri;
        }
    }

}
