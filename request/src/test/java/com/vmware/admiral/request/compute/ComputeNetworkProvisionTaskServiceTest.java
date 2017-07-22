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

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.network.ComputeNetworkCIDRAllocationService.ComputeNetworkCIDRAllocationState;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.NetworkProfileService;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.request.compute.ComputeNetworkProvisionTaskService.ComputeNetworkProvisionTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.NetworkService;
import com.vmware.photon.controller.model.resources.SecurityGroupService;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;

public class ComputeNetworkProvisionTaskServiceTest extends ComputeRequestBaseTest {

    private static final String NETWORK_LINK = NetworkService.FACTORY_LINK + "/myNetwork";

    @Override
    @Before
    public void setUp() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(true);
        super.setUp();
    }

    @Test
    public void testProvisionIsolatedNetworkNoCompute() throws Throwable {
        ComputeNetworkDescription computeNetworkDesc = createComputeNetworkDescription(UUID
                .randomUUID().toString(), NetworkType.ISOLATED);

        ComputeNetwork computeNetwork = createComputeNetwork(computeNetworkDesc,
                createIsolatedNetworkProfile(createSecurityGroupState()).documentSelfLink);

        ComputeNetworkProvisionTaskState provisioningTask = createComputeNetworkProvisionTask(
                computeNetworkDesc.documentSelfLink, computeNetwork.documentSelfLink, null, 1);
        provisioningTask = provision(provisioningTask);

        ComputeNetwork networkState = getDocument(ComputeNetwork.class,
                provisioningTask.resourceLinks.iterator().next());

        assertNotNull(networkState);
        assertEquals(computeNetworkDesc.documentSelfLink, networkState.descriptionLink);
        assertTrue(networkState.name.contains(computeNetworkDesc.name));
        assertEquals(provisioningTask.resourceLinks.iterator().next(), networkState.documentSelfLink);
    }

    @Test
    public void testProvisionExternalNetworkNoCompute() throws Throwable {
        ComputeNetworkDescription computeNetworkDesc = createComputeNetworkDescription(UUID
                .randomUUID().toString(), NetworkType.EXTERNAL);

        ComputeNetwork computeNetwork = createComputeNetwork(computeNetworkDesc,
                createProfile().documentSelfLink);

        ComputeNetworkProvisionTaskState provisioningTask = createComputeNetworkProvisionTask(
                computeNetworkDesc.documentSelfLink, computeNetwork.documentSelfLink, null, 1);
        provisioningTask = provision(provisioningTask);

        ComputeNetwork networkState = getDocument(ComputeNetwork.class,
                provisioningTask.resourceLinks.iterator().next());

        assertNotNull(networkState);
        assertEquals(computeNetworkDesc.documentSelfLink, networkState.descriptionLink);
        assertTrue(networkState.name.contains(computeNetworkDesc.name));
        assertEquals(provisioningTask.resourceLinks.iterator().next(), networkState.documentSelfLink);
    }

    @Test
    public void testProvisionIsolatedSubnetNetwork() throws Throwable {
        ComputeNetworkDescription computeNetworkDesc = createComputeNetworkDescription(UUID
                .randomUUID().toString(), NetworkType.ISOLATED);

        ComputeNetwork computeNetwork = createComputeNetwork(computeNetworkDesc,
                createIsolatedSubnetNetworkProfile().documentSelfLink);

        String contextId = UUID.randomUUID().toString();
        ComputeDescription cd = createComputeDescriptionWithNetwork(computeNetwork.name);
        createComputeState(cd.documentSelfLink, contextId);

        ComputeNetworkProvisionTaskState provisioningTask = createComputeNetworkProvisionTask(
                computeNetworkDesc.documentSelfLink, computeNetwork.documentSelfLink, contextId, 1);
        provisioningTask = provision(provisioningTask);

        ComputeNetwork networkState = getDocument(ComputeNetwork.class,
                provisioningTask.resourceLinks.iterator().next());

        assertNotNull(networkState);
        assertEquals(computeNetworkDesc.documentSelfLink, networkState.descriptionLink);
        assertTrue(networkState.name.contains(computeNetworkDesc.name));
        assertEquals(provisioningTask.resourceLinks.iterator().next(), networkState.documentSelfLink);
    }

    @Test
    public void testProvisionIsolatedSubnetNetworkWithExternalIPAllocation() throws Throwable {
        ComputeNetworkDescription computeNetworkDesc = createComputeNetworkDescription(UUID
                .randomUUID().toString(), NetworkType.ISOLATED);

        ComputeNetwork computeNetwork = createComputeNetwork(computeNetworkDesc,
                createIsolatedSubnetNetworkProfileWithExternalSubnetLink().documentSelfLink);

        String contextId = UUID.randomUUID().toString();
        ComputeDescription cd = createComputeDescriptionWithNetwork(computeNetwork.name);
        createComputeState(cd.documentSelfLink, contextId);

        ComputeNetworkProvisionTaskState provisioningTask = createComputeNetworkProvisionTask(
                computeNetworkDesc.documentSelfLink, computeNetwork.documentSelfLink, contextId, 1);
        provisioningTask = provision(provisioningTask);

        ComputeNetwork networkState = getDocument(ComputeNetwork.class,
                provisioningTask.resourceLinks.iterator().next());

        assertNotNull(networkState);
        assertEquals(computeNetworkDesc.documentSelfLink, networkState.descriptionLink);
        assertTrue(networkState.name.contains(computeNetworkDesc.name));
        assertEquals(provisioningTask.resourceLinks.iterator().next(), networkState.documentSelfLink);
        assertNotNull(networkState.subnetLink);

        SubnetState subnetState = getDocument(SubnetState.class, networkState.subnetLink);
        assertNotNull(subnetState);
        assertNotNull(subnetState.customProperties);
        assertNotNull(subnetState.customProperties.get(
                ComputeConstants.CUSTOM_PROP_ISOLATION_EXTERNAL_IP_ADDRESS_LINK));
        assertNotNull(subnetState.customProperties.get(
                ComputeConstants.CUSTOM_PROP_ISOLATION_EXTERNAL_SUBNET_LINK));
    }

    @Test
    public void testProvisionIsolatedSecurityGroupNetwork() throws Throwable {
        ComputeNetworkDescription computeNetworkDesc = createComputeNetworkDescription(UUID
                .randomUUID().toString(), NetworkType.ISOLATED);

        ComputeNetwork computeNetwork = createComputeNetwork(computeNetworkDesc,
                createIsolatedSecurityGroupNetworkProfile().documentSelfLink);

        String contextId = UUID.randomUUID().toString();
        ComputeDescription cd = createComputeDescriptionWithNetwork(computeNetwork.name);
        createComputeState(cd.documentSelfLink, contextId);

        ComputeNetworkProvisionTaskState provisioningTask = createComputeNetworkProvisionTask(
                computeNetworkDesc.documentSelfLink, computeNetwork.documentSelfLink, contextId, 1);
        provisioningTask = provision(provisioningTask);

        ComputeNetwork networkState = getDocument(ComputeNetwork.class,
                provisioningTask.resourceLinks.iterator().next());

        assertNotNull(networkState);
        assertEquals(computeNetworkDesc.documentSelfLink, networkState.descriptionLink);
        assertTrue(networkState.name.contains(computeNetworkDesc.name));
        assertEquals(provisioningTask.resourceLinks.iterator().next(), networkState.documentSelfLink);
    }

    private ComputeNetworkProvisionTaskState createComputeNetworkProvisionTask(
            String networkDescriptionSelfLink, String networkStateSelfLink, String contextId,
            long resourceCount) {

        ComputeNetworkProvisionTaskState provisionTask = new ComputeNetworkProvisionTaskState();
        provisionTask.resourceLinks = new HashSet<>();
        provisionTask.resourceLinks.add(networkStateSelfLink);
        provisionTask.resourceDescriptionLink = networkDescriptionSelfLink;
        provisionTask.resourceCount = resourceCount;
        provisionTask.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        provisionTask.customProperties = new HashMap<>();
        if (contextId != null) {
            provisionTask.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        }
        return provisionTask;
    }

    private ComputeNetworkProvisionTaskState provision(
            ComputeNetworkProvisionTaskState provisionTask)
            throws Throwable {
        provisionTask = startProvisionTask(provisionTask);
        host.log("Start provisioning test: " + provisionTask.documentSelfLink);

        provisionTask = waitForTaskSuccess(provisionTask.documentSelfLink,
                ComputeNetworkProvisionTaskState.class);

        assertNotNull("ResourceLinks null for provisioning: " + provisionTask.documentSelfLink,
                provisionTask.resourceLinks);
        assertEquals("Resource count not equal for: " + provisionTask.documentSelfLink,
                provisionTask.resourceCount, Long.valueOf(provisionTask.resourceLinks.size()));

        host.log("Finished provisioning test: " + provisionTask.documentSelfLink);
        return provisionTask;
    }

    private ComputeNetworkProvisionTaskState startProvisionTask(
            ComputeNetworkProvisionTaskState provisionTask) throws Throwable {
        ComputeNetworkProvisionTaskState outProvisionTask = doPost(
                provisionTask, ComputeNetworkProvisionTaskService.FACTORY_LINK);
        assertNotNull(outProvisionTask);
        return outProvisionTask;
    }

    private ComputeNetworkDescription createComputeNetworkDescription(String name, NetworkType networkType)
            throws Throwable {
        ComputeNetworkDescription desc = createNetworkDescription(name, networkType);
        desc = doPost(desc,
                ComputeNetworkDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        return desc;
    }

    private ComputeNetwork createComputeNetwork(ComputeNetworkDescription cnd,
            String profileLink) throws Throwable {
        ComputeNetwork cn = new ComputeNetwork();
        cn.id = UUID.randomUUID().toString();
        cn.networkType = cnd.networkType;
        cn.customProperties = cnd.customProperties;
        cn.name = cnd.name;
        cn.provisionProfileLink = profileLink;
        cn.tenantLinks = cnd.tenantLinks;
        cn.descriptionLink = cnd.documentSelfLink;
        cn.groupLinks = new HashSet<>();
        cn.groupLinks.add(createResourceGroup("contextId", cnd.tenantLinks)
                .documentSelfLink);
        cn = doPost(cn, ComputeNetworkService.FACTORY_LINK);
        assertNotNull(cn);
        return cn;
    }

    private ComputeNetworkDescription createNetworkDescription(String name, NetworkType networkType) {
        ComputeNetworkDescription desc = TestRequestStateFactory
                .createComputeNetworkDescription(name);
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.networkType = networkType;
        return desc;
    }

    private SecurityGroupState createSecurityGroupState() throws Throwable {

        SecurityGroupState securityGroupState = new SecurityGroupState();
        securityGroupState.name = "securityGroupStateName";
        securityGroupState.documentSelfLink = UUID.randomUUID().toString();
        securityGroupState.egress = new ArrayList<>();
        securityGroupState.ingress = new ArrayList<>();
        securityGroupState.regionId = "regionId";
        securityGroupState.authCredentialsLink = UUID.randomUUID().toString();
        securityGroupState.resourcePoolLink = UUID.randomUUID().toString();
        securityGroupState.instanceAdapterReference = new URI("http://instanceAdapterReference");
        securityGroupState = doPost(securityGroupState, SecurityGroupService.FACTORY_LINK);

        return securityGroupState;
    }

    private ProfileState createIsolatedNetworkProfile(SecurityGroupState securityGroupState) throws Throwable {

        ComputeNetworkCIDRAllocationState cidrAllocation = createNetworkCIDRAllocationState();

        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationNetworkLink = cidrAllocation.networkLink;
        networkProfile.isolationNetworkCIDR = "192.168.0.0/16";
        networkProfile.securityGroupLinks = Arrays.asList(securityGroupState.documentSelfLink);

        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        ProfileState profile = super.createProfile(null, null, networkProfile, null, null);
        assertNotNull(profile);

        return profile;
    }

    private ProfileState createIsolatedSecurityGroupNetworkProfile() throws Throwable {

        NetworkProfile networkProfile = new NetworkProfile();
        networkProfile.name = "networkProfileName";
        networkProfile.isolationType = IsolationSupportType.SECURITY_GROUP;
        networkProfile.subnetLinks = Arrays.asList(createSubnetState(null).documentSelfLink);
        networkProfile = doPost(networkProfile, NetworkProfileService.FACTORY_LINK);

        ProfileState profile = super.createProfile(null, null, networkProfile, null, null);
        assertNotNull(profile);

        return profile;
    }
}
