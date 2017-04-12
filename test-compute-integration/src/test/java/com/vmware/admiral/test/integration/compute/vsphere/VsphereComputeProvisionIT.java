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

package com.vmware.admiral.test.integration.compute.vsphere;

import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.profile.ComputeImageDescription;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ComputeOperationType;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.compute.BaseComputeProvisionIT;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class VsphereComputeProvisionIT extends BaseComputeProvisionIT {
    private static final String VSPHERE_COMPUTE_PROFILE = "/config/compute-profiles/vsphere";
    private static final String VSPHERE_DISK_URI =
            System.getProperty("test.vc.disk.uri", "vc://datastore/sof-20659-local/coreos-991.1.0/coreos-991.1.0.vmdk");
    private static final String DISK_URI_IMAGE_ID = "coreos-disk";
    private static final long HDD_DISK_SIZE = 61 * 1024;

    @Override
    protected EndpointType getEndpointType() {
        return EndpointType.vsphere;
    }

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {
        executeDay2(resourceLinks, ComputeOperationType.POWER_ON);
        waitForComputePowerState(PowerState.ON, resourceLinks);

        executeDay2(resourceLinks, ComputeOperationType.POWER_OFF);
        waitForComputePowerState(PowerState.OFF, resourceLinks);
    }

    @Override
    protected long getRootDiskSize() {
        return HDD_DISK_SIZE;
    }

    @Test
    public void testProvisionWithBootDisk() throws Throwable {
        // Get the vSphere compute profile and update imageMapping for diskUri
        ComputeProfile computeProfile = getDocument(VSPHERE_COMPUTE_PROFILE, ComputeProfile.class);
        ComputeImageDescription computeImageDescription = new ComputeImageDescription();
        computeImageDescription.image = VSPHERE_DISK_URI;
        computeProfile.imageMapping.put(DISK_URI_IMAGE_ID, computeImageDescription);
        patchDocument(computeProfile);

        String resourceDescriptionLink = getResourceDescriptionLink(true, DISK_URI_IMAGE_ID);
        provision(resourceDescriptionLink);
    }

    private void executeDay2(Set<String> resourceLinks, ComputeOperationType computeOperation)
            throws Throwable {
        RequestBrokerState day2StartRequest = new RequestBrokerState();
        day2StartRequest.resourceType = ResourceType.COMPUTE_TYPE.getName();
        day2StartRequest.resourceLinks = resourceLinks;
        day2StartRequest.operation = computeOperation.id;
        day2StartRequest.tenantLinks = endpoint.tenantLinks;
        day2StartRequest = executeRequest(day2StartRequest);

        getDocument(day2StartRequest.documentSelfLink, RequestBrokerState.class);
    }

    protected RequestBrokerState executeRequest(RequestBrokerState requestBrokerState)
            throws Throwable {
        RequestBrokerState request = postDocument(RequestBrokerFactoryService.SELF_LINK,
                requestBrokerState);

        waitForTaskToComplete(request.documentSelfLink);
        return request;
    }

    private void waitForComputePowerState(final PowerState expectedPowerState,
            Collection<String> computeLinks) throws Throwable {
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
    public void extendEndpoint(EndpointState endpoint) {
        endpoint.endpointProperties.put("privateKeyId",
                getTestRequiredProp(VsphereUtil.VC_USERNAME));
        endpoint.endpointProperties.put("privateKey", getTestRequiredProp(VsphereUtil.VC_PASSWORD));
        endpoint.endpointProperties.put("hostName", getTestRequiredProp(VsphereUtil.VC_HOST));

        // for this test we don't specify a datacenter on the endpoint
    }

    @Override
    protected void extendComputeDescription(ComputeDescription computeDescription)
            throws Exception {
        computeDescription.dataStoreId = getTestRequiredProp(VsphereUtil.VC_DATASTORE_ID);
        computeDescription.zoneId = getTestRequiredProp(VsphereUtil.VC_RESOURCE_POOL_ID);
        String vmFolder = getTestProp(VsphereUtil.VC_TARGET_FOLDER_PATH);
        if (vmFolder != null) {
            computeDescription.customProperties.put(ComputeProperties.RESOURCE_GROUP_NAME,
                    vmFolder);
        }
    }

    @Override
    public void doSetUp() throws Exception {
        // restrict available placements to the one specified through the VC_COMPUTE_NAME prop
        // because this is the one which can access the storage used in the tests
        restrictAvailablePlacements();
    }

    private void restrictAvailablePlacements() throws Exception {
        String computePlacementName = getTestRequiredProp(VsphereUtil.VC_TARGET_COMPUTE_NAME);
        ResourcePoolState rp = getDocument(this.endpoint.resourcePoolLink, ResourcePoolState.class);

        Query query = rp.query;
        query.addBooleanClause(QueryUtil.addListValueClause(ComputeState.FIELD_NAME_TYPE,
                Arrays.asList(ComputeType.VM_HOST.name(), ComputeType.ZONE.name()),
                MatchType.TERM));

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(query).addOption(QueryOption.EXPAND_CONTENT).build();

        String responseJson = sendRequest(HttpMethod.POST, ServiceUriPaths.CORE_QUERY_TASKS,
                Utils.toJson(queryTask));
        QueryTask returnedTask = Utils.fromJson(responseJson, QueryTask.class);

        for (Object computeJson : returnedTask.results.documents.values()) {
            ComputeState compute = Utils.fromJson(computeJson, ComputeState.class);
            if (computePlacementName.equals(compute.name)) {
                continue;
            }

            logger.info("Removing %s from the endpoint placement zone", compute.name);

            // clear the resource pool assignment
            compute.resourcePoolLink = null;
            sendRequest(HttpMethod.PUT, compute.documentSelfLink, Utils.toJson(compute));
        }
    }
}
