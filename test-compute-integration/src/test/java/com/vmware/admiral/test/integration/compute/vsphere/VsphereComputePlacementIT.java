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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.GroupResourcePlacementService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.endpoint.EndpointAdapterService;
import com.vmware.admiral.request.RequestBrokerFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.utils.RequestUtils;
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
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Test that validates tag-based placement against a vSphere endpoint.
 *
 * Validation is performed by examining local state instead of direct connection to the vSphere
 * endpoint.
 *
 * Possible additions:
 * - Tests with more complex business groups / tenant links.
 * - Validation through direct connection to vSphere.
 * - Test placement memory and cpu figures (not automatically updated yet).
 * - Test that removing a tag removes the host and VMs from the placement.
 */
public class VsphereComputePlacementIT extends BaseIntegrationSupportIT {

    private EndpointState endpoint;
    private RequestBrokerState provisionRequest1;
    private RequestBrokerState provisionRequest2;

    @Override
    protected EndpointType getEndpointType() {
        return EndpointType.vsphere;
    }

    @Override
    protected void extendEndpoint(EndpointState endpoint) {
        endpoint.endpointProperties.put("privateKeyId", getTestRequiredProp(VsphereUtil.VC_USERNAME));
        endpoint.endpointProperties.put("privateKey", getTestRequiredProp(VsphereUtil.VC_PASSWORD));
        endpoint.endpointProperties.put("regionId", getTestRequiredProp(VsphereUtil.VC_DATACENTER_ID));
        endpoint.endpointProperties.put("hostName", getTestRequiredProp(VsphereUtil.VC_HOST));
    }

    @Override
    protected List<String> getTenantLinks() {
        return null;
    }

    @Before
    public void endpointSetup() throws Exception {
        logger.info("Creating endpoint...");
        this.endpoint = createEndpoint(getEndpointType(), TestDocumentLifeCycle.NO_DELETE);
        logger.info("Endpoint created. Starting enumeration...");
        triggerAndWaitForEndpointEnumeration(endpoint);
    }

    @After
    public void endpointCleanup() throws Exception {
        for (RequestBrokerState provisionRequest : Arrays.asList(this.provisionRequest1,
                this.provisionRequest2)) {
            if (provisionRequest != null) {
                try {
                    logger.info("Cleaning up resources %s...", provisionRequest.resourceLinks);
                    removeVm(provisionRequest);
                    logger.info("Resources cleaned up.");
                } catch (Exception e) {
                    logger.warning("Error cleaning up resources %s: %s",
                            provisionRequest.resourceLinks, e.toString());
                }
            }
        }

        if (this.endpoint != null) {
            logger.info("Deleting endpoint...");
            delete(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK,
                    this.endpoint.documentSelfLink));
            logger.info("Endpoint deleted.");
        }
    }

    /**
     *    placement A (pri 5, cap 2)           placement B (pri 10, cap unlimited)
     *        zone A (sofia+dev)                          zone B (pa+qa)
     *             compute0                            compute1, compute2
     *
     * 3 random computes are chosen from the vSphere endpoint and their local storage is used for
     * deploying an empty VM.
     */
    @Test
    public void testTagBasedPlacement() throws Exception {

        TagState tagSofia = createTag("loc", "sofia");
        TagState tagPA = createTag("loc", "pa");
        TagState tagDev = createTag("dept", "dev");
        TagState tagQA = createTag("dept", "qa");
        ResourcePoolState rpA = createEpz("RP A", this.endpoint, tagSofia, tagDev);
        ResourcePoolState rpB = createEpz("RP B", this.endpoint, tagPA, tagQA);
        GroupResourcePlacementState reservationA = createReservation("Placement A", rpA, 5, 2);
        GroupResourcePlacementState reservationB = createReservation("Placement B", rpB, 10, 0);

        List<ComputeState> computes = selectComputes(this.endpoint.resourcePoolLink, 3);
        setTags(computes.get(0), tagSofia, tagDev);
        setTags(computes.get(1), tagPA, tagQA);
        setTags(computes.get(2), tagPA, tagQA);

        this.provisionRequest1 = provisionVm("placement-vm", 1, computes.subList(0, 1));
        validateReservations(reservationA, reservationB, 1, 0);
        removeVm(this.provisionRequest1);
        this.provisionRequest1 = null;
        validateReservations(reservationA, reservationB, 0, 0);

        this.provisionRequest2 = provisionVm("placement-app", 3, computes.subList(1, 3));
        validateReservations(reservationA, reservationB, 0, 3);

        removeVm(this.provisionRequest2);
        this.provisionRequest2 = null;
        validateReservations(reservationA, reservationB, 0, 0);
    }

    private TagState createTag(String key, String value) throws Exception {
        TagState tagState = new TagState();
        tagState.key = key;
        tagState.value = value;
        tagState.tenantLinks = QueryUtil.getTenantLinks(this.endpoint.tenantLinks);
        return postDocument(TagService.FACTORY_LINK, tagState);
    }

    private ResourcePoolState createEpz(String name, EndpointState endpoint, TagState... tags)
            throws Exception {
        ElasticPlacementZoneConfigurationState epzState = new ElasticPlacementZoneConfigurationState();
        epzState.resourcePoolState = new ResourcePoolState();
        epzState.resourcePoolState.name = name;
        epzState.resourcePoolState.customProperties = new HashMap<>();
        if (endpoint != null) {
            epzState.resourcePoolState.customProperties.put(
                    ComputeProperties.ENDPOINT_LINK_PROP_NAME, endpoint.documentSelfLink);
        }
        epzState.tenantLinks = endpoint.tenantLinks;
        epzState.epzState = new ElasticPlacementZoneState();
        epzState.epzState.tagLinksToMatch = new HashSet<>();
        for (TagState tag : tags) {
            epzState.epzState.tagLinksToMatch.add(tag.documentSelfLink);
        }

        ElasticPlacementZoneConfigurationState returnedState = postDocument(
                ElasticPlacementZoneConfigurationService.SELF_LINK,
                epzState, TestDocumentLifeCycle.NO_DELETE);
        cleanUpAfter(returnedState.epzState);
        return returnedState.resourcePoolState;
    }

    private GroupResourcePlacementState createReservation(String name, ResourcePoolState rp, int priority,
            int maxInstances) throws Exception {
        GroupResourcePlacementState reservation = new GroupResourcePlacementState();
        reservation.resourceType = ResourceType.COMPUTE_TYPE.getName();
        reservation.name = name;
        reservation.resourcePoolLink = rp.documentSelfLink;
        reservation.priority = priority;
        reservation.tenantLinks = rp.tenantLinks;
        reservation.maxNumberInstances = maxInstances;
        return postDocument(GroupResourcePlacementService.FACTORY_LINK, reservation);
    }

    private void validateReservations(GroupResourcePlacementState res1,
            GroupResourcePlacementState res2, long expectedAllocated1, long expectedAllocated2)
            throws Exception {
        GroupResourcePlacementState res1CurrentState = getDocument(res1.documentSelfLink,
                GroupResourcePlacementState.class);
        GroupResourcePlacementState res2CurrentState = getDocument(res2.documentSelfLink,
                GroupResourcePlacementState.class);
        assertEquals(expectedAllocated1, res1CurrentState.allocatedInstancesCount);
        assertEquals(expectedAllocated2, res2CurrentState.allocatedInstancesCount);
    }

    private void setTags(ResourceState state, TagState ...tags) throws Exception {
        state.tagLinks = new HashSet<>();
        for (TagState tag : tags) {
            state.tagLinks.add(tag.documentSelfLink);
        }
        sendRequest(HttpMethod.PUT, state.documentSelfLink, Utils.toJson(state));
    }

    private List<ComputeState> selectComputes(String resourcePoolLink, int count) throws Exception {
        ResourcePoolState rp = getDocument(resourcePoolLink, ResourcePoolState.class);

        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setQuery(rp.query).addOption(QueryOption.EXPAND_CONTENT).build();
        String responseJson = sendRequest(HttpMethod.POST, ServiceUriPaths.CORE_QUERY_TASKS,
                Utils.toJson(queryTask));
        QueryTask returnedTask = Utils.fromJson(responseJson, QueryTask.class);

        final List<ComputeState> selected = new ArrayList<>(returnedTask.results.documents.size());
        returnedTask.results.documents.values().forEach(json -> {
            ComputeState cs = Utils.fromJson(json, ComputeState.class);
            if (cs.type == ComputeType.VM_HOST) {
                selected.add(cs);
            }
        });
        assertTrue("Unsufficient compute resources on the vCenter server", selected.size() >= count);

        Collections.shuffle(selected);
        if (selected.size() > count) {
            logger.info("%d compute resources not used in this test: %s", selected.size() - count,
                    selected.subList(count, selected.size()).stream().map(cs -> cs.name)
                            .collect(Collectors.toList()));
            selected.subList(count, selected.size()).clear();
        }

        int index = 0;
        for (ComputeState compute : selected) {
            logger.info("Selected compute %d: %s", ++index, compute.name);

            // clear the resource pool assignment
            compute.resourcePoolLink = null;
            sendRequest(HttpMethod.PUT, compute.documentSelfLink, Utils.toJson(compute));
        }

        return selected;
    }

    private RequestBrokerState provisionVm(String vmName, int count,
            List<ComputeState> expectedPlacements) throws Exception {
        List<String> expectedPlacementLinks = expectedPlacements.stream()
                .map(cs -> cs.documentSelfLink).collect(Collectors.toList());

        logger.info("Starting VM provisioning: %s, %d instance(s)", vmName, count);
        ComputeDescription vmDesc = createVmComputeDescription(vmName);

        RequestBrokerState allocateRequest = requestCompute(vmDesc.documentSelfLink,
                Long.valueOf(count), null);
        assertNotNull(allocateRequest.resourceLinks);

        for (String resourceLink : allocateRequest.resourceLinks) {
            ComputeState resource = getDocument(resourceLink, ComputeState.class);
            String placementLink = resource.customProperties.get(ComputeProperties.PLACEMENT_LINK);
            assertTrue(
                    String.format(
                            "Unexpected placement '%s' found for VM '%s'; expected placements: %s",
                            placementLink, resource.name, expectedPlacementLinks),
                    expectedPlacementLinks.contains(placementLink));
        }

        RequestBrokerState provisionRequest = requestCompute(vmDesc.documentSelfLink, null,
                allocateRequest.resourceLinks);

        logger.info("VM %s provisioning completed.", vmName);
        return provisionRequest;
    }

    private void removeVm(RequestBrokerState provisionRequest) throws Exception {
        logger.info("Removing VM...");
        RequestBrokerState removeRequest = new RequestBrokerState();
        removeRequest.resourceType = provisionRequest.resourceType;
        removeRequest.resourceLinks = provisionRequest.resourceLinks;
        removeRequest.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;

        RequestBrokerState returnedState = postDocument(RequestBrokerFactoryService.SELF_LINK,
                removeRequest);
        waitForTaskToComplete(returnedState.documentSelfLink);
        logger.info("VM removed.");
    }

    private ComputeDescription createVmComputeDescription(String vmName) throws Exception {
        ComputeDescription cd = new ComputeDescription();
        cd.name = vmName;
        cd.tenantLinks = this.endpoint.tenantLinks;
        cd.customProperties = new HashMap<>();
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME, "coreos");
        return postDocument(ComputeDescriptionService.FACTORY_LINK, cd);
    }

    private RequestBrokerState requestCompute(String resourceDescLink,
            Long count, Set<String> resourceLinks) throws Exception {
        RequestBrokerState requestBrokerState = new RequestBrokerState();

        requestBrokerState.resourceType = ResourceType.COMPUTE_TYPE.getName();
        requestBrokerState.resourceDescriptionLink = resourceDescLink;

        requestBrokerState.resourceCount = count != null ? count : resourceLinks.size();
        requestBrokerState.resourceLinks = resourceLinks;
        requestBrokerState.tenantLinks = this.endpoint.tenantLinks;
        requestBrokerState.customProperties = new HashMap<>();
        if (count != null) {
            requestBrokerState.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                    "true");
        } else {
            requestBrokerState.operation = ContainerOperationType.CREATE.id;
        }

        RequestBrokerState request = postDocument(RequestBrokerFactoryService.SELF_LINK,
                requestBrokerState);

        waitForTaskToComplete(request.documentSelfLink);
        return getDocument(request.documentSelfLink, RequestBrokerState.class);
    }
}
