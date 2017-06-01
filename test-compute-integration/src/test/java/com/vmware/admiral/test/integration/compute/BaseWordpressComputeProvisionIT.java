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

package com.vmware.admiral.test.integration.compute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.net.util.SubnetUtils;

import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.Protocol;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState.Rule.Access;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;

public abstract class BaseWordpressComputeProvisionIT extends BaseComputeProvisionIT {

    protected static final String AWS_DEFAULT_SUBNET_NAME = "subnet1";
    protected static final String AWS_SECONDARY_SUBNET_NAME = "subnet2";

    protected static final String AWS_ISOLATED_VPC_NAME = "isolated-vpc";

    private static final String WP_PATH = "mywordpresssite/wp-admin/install.php?step=1";
    private static final int STATUS_CODE_WAIT_POLLING_RETRY_COUNT = 300; //5 min

    @Override
    protected RequestBrokerState allocateAndProvision(
            String resourceDescriptionLink) throws Exception {
        RequestBrokerState allocateRequest = requestCompute(
                resourceDescriptionLink, true, null);

        allocateRequest = getDocument(allocateRequest.documentSelfLink,
                RequestBrokerState.class);

        assertNotNull(allocateRequest.resourceLinks);
        System.out.println(allocateRequest.resourceLinks);
        for (String link : allocateRequest.resourceLinks) {
            ComputeState computeState = getDocument(link,
                    ComputeState.class);
            assertNotNull(computeState);
        }

        return allocateRequest;
    }

    @Override
    protected void doWithResources(Set<String> resourceLinks) throws Throwable {
        Set<ServiceDocument> resources = getResources(resourceLinks);

        ComputeState wordPress = (ComputeState) resources.stream()
                .filter(c -> c instanceof ComputeState)
                .filter(c -> ((ComputeState) c).name.contains("wordpress"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Unable to find the ComputeState corresponding to the Wordpress node"));

        String address = wordPress.address;
        verifyConnectivity(address);
    }

    protected Set<ServiceDocument> getResources(Set<String> resourceLinks) throws Exception {
        CompositeComponent compositeComponent = getDocument(resourceLinks.iterator().next(),
                CompositeComponent.class);
        Set<ServiceDocument> computes = new HashSet<>();
        for (String link : compositeComponent.componentLinks) {
            Class<? extends ResourceState> stateClass = CompositeComponentRegistry
                    .metaByStateLink(link).stateClass;
            ServiceDocument document = getDocument(link, stateClass);
            computes.add(document);
        }

        return computes;
    }

    protected void verifyConnectivity(String address) {
        URI uri = URI.create(String.format("http://%s/%s", address, WP_PATH));

        try {
            waitForStatusCode(uri, Operation.STATUS_CODE_OK, STATUS_CODE_WAIT_POLLING_RETRY_COUNT);
        } catch (Exception eInner) {
            logger.error("Failed to verify wordpress connection: %s", eInner.getMessage());
            fail();
        }
    }

    protected void validateComputeNic(ComputeState computeState, String expectedSubnet)
            throws Exception {
        if (computeState.networkInterfaceLinks == null || computeState.networkInterfaceLinks
                .isEmpty()) {
            fail(String.format("VM '%s' doesn't have any nics", computeState.name));
        }

        NetworkInterfaceState nic = getDocument(computeState.networkInterfaceLinks.get(0),
                NetworkInterfaceState.class);
        if (nic == null) {
            fail(String.format("Unable to find network interface of VM '%s'", computeState.name));
        }

        logger.info("Loading subnet %s for nic %s,on vm %s", nic.subnetLink, nic.documentSelfLink,
                computeState.name);
        SubnetState subnetState = getDocument(nic.subnetLink, SubnetState.class);
        if (subnetState == null) {
            fail(String.format(
                    "Unable to find subnet assigned to network interface of VM '%s' with subnet "
                            + "link: %s",
                    computeState.name, nic.subnetLink));
        }

        if (expectedSubnet != null && !subnetState.name.equals(expectedSubnet)) {
            fail(String.format(
                    "VM '%s' is assigned to unexpected subnet: '%s', expected subnet: '%s'",
                    computeState.name,
                    subnetState.name, expectedSubnet));
        }
    }

    protected static void validateIsolatedNic(Set<ServiceDocument> computes,
            String isolatedNetworkName) {
        validateIsolatedNic(computes, isolatedNetworkName, null);
    }

    protected static void validateIsolatedNic(Set<ServiceDocument> computes,
            String isolatedNetworkName, String subnetCIDR) {
        for (ServiceDocument serviceDocument : computes) {
            if (!(serviceDocument instanceof ComputeState)) {
                continue;
            }

            ComputeState computeState = (ComputeState) serviceDocument;
            try {
                NetworkInterfaceState networkInterfaceState = getDocument(
                        computeState.networkInterfaceLinks.get(0), NetworkInterfaceState.class);

                IsolationSupportType isolationType = getNetworkProfileIsolationType
                        (networkInterfaceState, computes);
                if (isolationType == IsolationSupportType.SUBNET) {
                    SubnetState subnetState = getDocument(
                            networkInterfaceState.subnetLink,
                            SubnetState.class);

                    assertTrue(subnetState.name.contains("wpnet"));

                    NetworkState networkState = getDocument(subnetState.networkLink,
                            NetworkState.class);

                    assertEquals(networkState.name, isolatedNetworkName);

                    //validate the cidr
                    String lowSubnetAddress = new SubnetUtils(subnetState.subnetCIDR).getInfo()
                            .getLowAddress();

                    if (subnetCIDR == null) {
                        subnetCIDR = networkState.subnetCIDR;
                    }

                    assertTrue(new SubnetUtils(subnetCIDR).getInfo().isInRange(lowSubnetAddress));
                } else if (isolationType == IsolationSupportType.SECURITY_GROUP) {
                    assertNotNull(networkInterfaceState.securityGroupLinks);
                    assertTrue(networkInterfaceState.securityGroupLinks.size() > 0);

                    boolean isIsolationSecurityGroup = false;
                    for (String sgLink : networkInterfaceState.securityGroupLinks) {
                        SecurityGroupState securityGroupState = getDocument(sgLink,
                                SecurityGroupState.class);
                        assertNotNull(securityGroupState);
                        assertNotNull(computeState.customProperties);

                        isIsolationSecurityGroup = isIsolationSecurityGroup(securityGroupState,
                                computeState.customProperties.get(FIELD_NAME_CONTEXT_ID_KEY));
                    }

                    assertTrue(isIsolationSecurityGroup);
                }
            } catch (Exception e) {
                fail();
            }
        }

    }

    private static IsolationSupportType getNetworkProfileIsolationType(NetworkInterfaceState nic,
            Set<ServiceDocument> computes) throws Exception {
        ComputeNetwork computeNetwork = null;
        for (ServiceDocument compute : computes) {
            if (compute instanceof ComputeNetwork) {
                ComputeNetworkDescription cnd = getDocument(((ComputeNetwork)compute).descriptionLink,
                        ComputeNetworkDescription.class);
                assertNotNull(cnd);
                assertNotNull(cnd.name);
                if (cnd.name.equals(nic.name)) {
                    computeNetwork = (ComputeNetwork) compute;
                }
            }
        }

        if (computeNetwork == null) {
            throw new AssertionError(
                    "Unable to find the ComputeNetwork corresponding to NIC " + nic.name);
        }

        assertNotNull("Provision profile link must be set", computeNetwork.provisionProfileLink);

        ProfileState profileState = getDocument(computeNetwork.provisionProfileLink,
                ProfileState.class);

        assertNotNull(profileState);
        assertNotNull(profileState.networkProfileLink);

        NetworkProfile networkProfile = getDocument(profileState.networkProfileLink,
                NetworkProfile.class);

        assertNotNull(networkProfile);
        assertNotNull(networkProfile.isolationType);

        return networkProfile.isolationType;
    }

    private static boolean isIsolationSecurityGroup(SecurityGroupState securityGroupState,
            String contextId) {
        assertNotNull(contextId);
        // an isolation security group must be within the same context id as the compute, and
        // have one single ingress/egress firewall rule where all protocols are denied

        if (securityGroupState.customProperties == null ||
                !securityGroupState.customProperties.containsKey(FIELD_NAME_CONTEXT_ID_KEY)) {
            return false;
        }

        if (!securityGroupState.customProperties.get(FIELD_NAME_CONTEXT_ID_KEY).equals(contextId)
                || securityGroupState.egress == null || securityGroupState.egress.size() != 1 ||
                securityGroupState.ingress == null || securityGroupState.ingress.size() != 1) {
            return false;
        }

        Rule rule = securityGroupState.egress.get(0);
        if (rule.access != Access.Deny || !rule.protocol.equals(Protocol.ANY.getName())
                || !rule.ports.equals("1-65535") || !rule.ipRangeCidr.equals("0.0.0.0/0")) {
            return false;
        }

        rule = securityGroupState.ingress.get(0);
        if (rule.access != Access.Deny || !rule.protocol.equals(Protocol.ANY.getName())
                || !rule.ports.equals("1-65535") || !rule.ipRangeCidr.equals("0.0.0.0/0")) {
            return false;
        }

        return true;
    }
}
