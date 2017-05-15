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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import com.vmware.admiral.common.util.YamlMapper;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.profile.ComputeProfileService.ComputeProfile;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceService.NetworkInterfaceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;

public abstract class BaseWordpressComputeProvisionIT extends BaseComputeProvisionIT {

    protected static final String AWS_DEFAULT_SUBNET_NAME = "subnet1";
    protected static final String AWS_SECONDARY_SUBNET_NAME = "subnet2";

    protected static final String AWS_ISOLATED_VPC_NAME = "isolated-vpc";

    private static final String WP_PATH = "mywordpresssite";
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
            Class stateClass = CompositeComponentRegistry.metaByStateLink(link).stateClass;
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

    protected ComputeProfile loadComputeProfile() {
        URL r = getClass().getClassLoader().getResource("test-aws-compute-profile.yaml");
        try (InputStream is = r.openStream()) {
            return YamlMapper.objectMapper().readValue(is, ComputeProfile.class);
        } catch (Exception e) {
            logger.error("Failure reading default environment: %s, reason: %s", r,
                    e.getMessage());
            return null;
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
                    "Unable to find subnet assigned to network interface of VM '%s'",
                    computeState.name));
        }

        if (expectedSubnet != null && !subnetState.name.equals(expectedSubnet)) {
            fail(String.format(
                    "VM '%s' is assigned to unexpected subnet: '%s', expected subnet: '%s'",
                    computeState.name,
                    subnetState.id, expectedSubnet));
        }
    }
}
