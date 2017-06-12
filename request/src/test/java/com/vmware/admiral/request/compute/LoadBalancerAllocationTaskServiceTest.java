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

package com.vmware.admiral.request.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.ComputeProfileService;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.compute.profile.StorageProfileService;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.compute.LoadBalancerAllocationTaskService.LoadBalancerAllocationTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService;
import com.vmware.photon.controller.model.resources.LoadBalancerDescriptionService.LoadBalancerDescription;
import com.vmware.photon.controller.model.resources.LoadBalancerService.LoadBalancerState;
import com.vmware.photon.controller.model.resources.SubnetService;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;

/**
 * Tests for the {@link LoadBalancerAllocationTaskService} class.
 */
public class LoadBalancerAllocationTaskServiceTest extends RequestBaseTest {

    private String contextId;

    @Before
    public void initRequestContext() throws Throwable {
        this.contextId = UUID.randomUUID().toString();
    }

    @Test
    public void testAllocationTaskServiceLifeCycle() throws Throwable {
        // create prerequisites
        ComputeDescription computeDesc = createComputeDescription(true);
        List<ComputeState> computes = createComputes(computeDesc, 2);
        ComputeNetworkDescription networkDesc = createComputeNetworkDescription("lb-net");
        SubnetState subnet = createSubnet();
        ProfileState profile = createProfile(Arrays.asList(subnet.documentSelfLink));
        createComputeNetwork("lb-net-1", networkDesc.documentSelfLink,
                Arrays.asList(profile.documentSelfLink));
        LoadBalancerDescription loadBalancerDesc = createLoadBalancerDescription(
                computeDesc.documentSelfLink, networkDesc.name);

        LoadBalancerAllocationTaskState allocationTask = createLoadBalancerAllocationTask(
                loadBalancerDesc.documentSelfLink);
        allocationTask = allocate(allocationTask);

        LoadBalancerState loadBalancerState = getDocument(LoadBalancerState.class,
                allocationTask.resourceLinks.iterator().next());

        assertNotNull(loadBalancerState);
        assertEquals(loadBalancerDesc.documentSelfLink, loadBalancerState.descriptionLink);
        assertTrue(loadBalancerState.name.contains(loadBalancerDesc.name));
        assertEquals(
                computeDesc.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME),
                loadBalancerState.endpointLink);
        assertEquals(computes.size(), loadBalancerState.computeLinks.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoEndpointDetails() throws Throwable {
        // create prerequisites
        ComputeDescription computeDesc = createComputeDescription(false);
        createComputes(computeDesc, 2);
        LoadBalancerDescription loadBalancerDesc = createLoadBalancerDescription(null, "lb-net");

        LoadBalancerAllocationTaskState allocationTask = createLoadBalancerAllocationTask(
                loadBalancerDesc.documentSelfLink);
        allocate(allocationTask);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoComputeDesc() throws Throwable {
        // create prerequisites
        LoadBalancerDescription loadBalancerDesc = createLoadBalancerDescription(null, "lb-net");

        LoadBalancerAllocationTaskState allocationTask = createLoadBalancerAllocationTask(
                loadBalancerDesc.documentSelfLink);
        allocate(allocationTask);
    }

    private LoadBalancerAllocationTaskState createLoadBalancerAllocationTask(
            String loadBalancerDescLink) {
        LoadBalancerAllocationTaskState allocationTask = new LoadBalancerAllocationTaskState();
        allocationTask.resourceDescriptionLink = loadBalancerDescLink;
        allocationTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        allocationTask.customProperties = new HashMap<>();
        allocationTask.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, this.contextId);
        return allocationTask;
    }

    private LoadBalancerAllocationTaskState allocate(LoadBalancerAllocationTaskState allocationTask)
            throws Throwable {
        allocationTask = startAllocationTask(allocationTask);
        host.log("Start allocation test: " + allocationTask.documentSelfLink);

        allocationTask = waitForTaskSuccess(allocationTask.documentSelfLink,
                LoadBalancerAllocationTaskState.class);

        return allocationTask;
    }

    private LoadBalancerAllocationTaskState startAllocationTask(
            LoadBalancerAllocationTaskState allocationTask) throws Throwable {
        LoadBalancerAllocationTaskState outAllocationTask = doPost(
                allocationTask, LoadBalancerAllocationTaskService.FACTORY_LINK);
        assertNotNull(outAllocationTask);
        return outAllocationTask;
    }

    private ComputeDescription createComputeDescription(boolean addEndpointDetails) throws Throwable {
        ComputeDescription cd = TestRequestStateFactory.createComputeDescriptionForVmGuestChildren();
        if (addEndpointDetails) {
            cd.customProperties.put(ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                    createEndpoint().documentSelfLink);
            cd.customProperties.put(ComputeConstants.CUSTOM_PROP_ENDPOINT_TYPE_NAME, "aws");
            cd.regionId = "region-1";
        }
        return doPost(cd, ComputeDescriptionService.FACTORY_LINK);
    }

    private List<ComputeState> createComputes(ComputeDescription cd, int count) throws Throwable {
        List<ComputeState> computes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            computes.add(createCompute(cd));
        }
        return computes;
    }

    private ComputeState createCompute(ComputeDescription cd) throws Throwable {
        ComputeState cs = TestRequestStateFactory.createVmHostComputeState();
        cs.descriptionLink = cd.documentSelfLink;
        cs.name = UUID.randomUUID().toString();
        return doPost(cs, ComputeService.FACTORY_LINK);
    }

    private LoadBalancerDescription createLoadBalancerDescription(String cdLink, String networkName)
            throws Throwable {
        LoadBalancerDescription desc = TestRequestStateFactory
                .createLoadBalancerDescription(UUID.randomUUID().toString());
        desc.computeDescriptionLink = cdLink;
        desc.networkName = networkName;
        return doPost(desc, LoadBalancerDescriptionService.FACTORY_LINK);
    }

    private ComputeNetworkDescription createComputeNetworkDescription(String name)
            throws Throwable {
        ComputeNetworkDescription desc = TestRequestStateFactory
                .createComputeNetworkDescription(name);
        return doPost(desc, ComputeNetworkDescriptionService.FACTORY_LINK);
    }

    private ComputeNetwork createComputeNetwork(String name, String descLink,
            List<String> profileLinks) throws Throwable {
        ComputeNetwork state = TestRequestStateFactory.createComputeNetworkState(name, descLink);
        state.customProperties.put(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, this.contextId);
        state.profileLinks = profileLinks;
        return doPost(state, ComputeNetworkService.FACTORY_LINK);
    }

    private SubnetState createSubnet() throws Throwable {
        SubnetState subnet = TestRequestStateFactory.createSubnetState("subnet");
        return doPost(subnet, SubnetService.FACTORY_LINK);
    }

    private ProfileState createProfile(List<String> subnetLinks) throws Throwable {
        ComputeProfile computeProfile = TestRequestStateFactory.createComputeProfile("net");
        computeProfile = doPost(computeProfile, ComputeProfileService.FACTORY_LINK);
        StorageProfile storageProfile = TestRequestStateFactory.createStorageProfile("net");
        storageProfile = doPost(storageProfile, StorageProfileService.FACTORY_LINK);

        NetworkProfile networkProfile = TestRequestStateFactory.createNetworkProfile("net");
        networkProfile.subnetLinks = subnetLinks;
        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        ProfileState profile = TestRequestStateFactory.createProfile("profile",
                networkProfile.documentSelfLink, storageProfile.documentSelfLink,
                computeProfile.documentSelfLink);
        profile.endpointLink = createEndpoint().documentSelfLink;
        profile.endpointType = null;

        return doPost(profile, ProfileService.FACTORY_LINK);
    }
}
