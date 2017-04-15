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
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.compute.ProvisionContainerHostsTaskService;
import com.vmware.admiral.test.integration.BaseIntegrationSupportIT;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.constants.PhotonModelConstants.EndpointType;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class VsphereProvisionContainerHostIT extends BaseIntegrationSupportIT {

    private EndpointType endpointType;
    private EndpointState endpoint;
    private Set<String> resourceLinks;

    @Before
    public void setUp() throws Throwable {

        endpointType = getEndpointType();
        endpoint = createEndpoint(endpointType, TestDocumentLifeCycle.NO_DELETE);
        triggerAndWaitForEndpointEnumeration(endpoint);

        restrictAvailablePlacements();
    }

    @Override
    @After
    public void baseTearDown() throws Exception {
        deleteDockerHosts();
        super.baseTearDown();
        delete(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK, endpoint.documentSelfLink));
    }

    private void deleteDockerHosts() throws Exception {
        if (this.resourceLinks == null) {
            return;
        }
        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_HOST_TYPE.getName();
        request.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        request.resourceLinks = this.resourceLinks;
        request.tenantLinks = endpoint.tenantLinks;

        RequestBrokerState state = postDocument(RequestBrokerFactoryService.SELF_LINK, request);
        waitForTaskToComplete(state.documentSelfLink);
    }

    @Override
    protected EndpointType getEndpointType() {
        return EndpointType.vsphere;
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        endpoint.endpointProperties.put("privateKeyId",
                getTestRequiredProp(VsphereUtil.VC_USERNAME));
        endpoint.endpointProperties.put("privateKey", getTestRequiredProp(VsphereUtil.VC_PASSWORD));
        endpoint.endpointProperties.put("regionId",
                getTestRequiredProp(VsphereUtil.VC_DATACENTER_ID));
        endpoint.endpointProperties.put("hostName", getTestRequiredProp(VsphereUtil.VC_HOST));
    }

    @Test
    public void testProvision() throws Throwable {
        ComputeDescription cd = createComputeDescription();

        RequestBrokerState request = new RequestBrokerState();
        request.resourceType = ResourceType.CONTAINER_HOST_TYPE.getName();
        request.operation = ProvisionContainerHostsTaskService.PROVISION_CONTAINER_HOSTS_OPERATION;
        request.resourceDescriptionLink = cd.documentSelfLink;
        request.tenantLinks = endpoint.tenantLinks;
        request.resourceCount = 1;

        RequestBrokerState result = postDocument(RequestBrokerFactoryService.SELF_LINK, request);

        waitForTaskToComplete(result.documentSelfLink);

        RequestBrokerState provisionRequest = getDocument(result.documentSelfLink,
                RequestBrokerState.class);
        assertNotNull(provisionRequest);
        assertNotNull(provisionRequest.resourceLinks);
        this.resourceLinks = provisionRequest.resourceLinks;
    }

    protected ComputeDescription createComputeDescription()
            throws Exception {

        ComputeDescription computeDesc = prepareComputeDescription();

        ComputeDescription computeDescription = postDocument(ComputeDescriptionService.FACTORY_LINK,
                computeDesc, TestDocumentLifeCycle.FOR_DELETE);

        return computeDescription;
    }

    protected ComputeDescription prepareComputeDescription() throws Exception {
        String id = name(getEndpointType(), "test", UUID.randomUUID().toString());
        ComputeDescription computeDesc = new ComputeDescription();
        computeDesc.id = id;
        computeDesc.name = nextName("dockervm");
        computeDesc.instanceType = "small";
        computeDesc.tenantLinks = endpoint.tenantLinks;
        computeDesc.dataStoreId = getTestRequiredProp(VsphereUtil.VC_DATASTORE_ID);
        computeDesc.zoneId = getTestRequiredProp(VsphereUtil.VC_RESOURCE_POOL_ID);

        computeDesc.customProperties = new HashMap<>();

        String vmFolder = getTestProp(VsphereUtil.VC_TARGET_FOLDER_PATH);
        if (vmFolder != null) {
            computeDesc.customProperties.put(ComputeProperties.RESOURCE_GROUP_NAME,
                    vmFolder);
        }
        computeDesc.customProperties
                .put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "coreos");
        computeDesc.customProperties.put(ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                endpoint.documentSelfLink);
        return computeDesc;
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
