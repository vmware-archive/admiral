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

package com.vmware.iaas.consumer.api.service;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import org.junit.Before;
import org.junit.Test;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;

public class MachineServiceTest extends BaseServiceTest {
    @Before
    public void setup() throws Throwable {
        this.host.startService(new MachineService());
        waitForServiceAvailability(MachineService.SELF_LINK);
    }

    @Test
    public void testGetSucceeds() throws Throwable {
        ComputeState compute = createCompute("testCompute", ComputeType.VM_GUEST, "endpointLink");

        ComputeState[] res = getDocument(ComputeState[].class, MachineService.SELF_LINK);

        assertTrue(res.length == 1);
        assertEquals(res[0].documentSelfLink, compute.documentSelfLink);
    }

    private ComputeState createCompute(String name, ComputeType type, String endpointLink, String... tagLinks)
            throws Throwable {
        ComputeDescription computeDescription = new ComputeDescriptionService.ComputeDescription();
        ArrayList<String> children = new ArrayList<>();
        children.add(type.toString());
        computeDescription.supportedChildren = children;
        computeDescription.bootAdapterReference = new URI("http://bootAdapterReference");
        computeDescription.powerAdapterReference = new URI("http://powerAdapterReference");
        computeDescription.instanceAdapterReference = new URI("http://instanceAdapterReference");
        computeDescription.healthAdapterReference = new URI("http://healthAdapterReference");
        computeDescription.enumerationAdapterReference = new URI("http://enumerationAdapterReference");
        computeDescription.dataStoreId = null;
        computeDescription.environmentName = ComputeDescriptionService.ComputeDescription.ENVIRONMENT_NAME_ON_PREMISE;
        computeDescription.cpuMhzPerCore = 1000;
        computeDescription.cpuCount = 2;
        computeDescription.gpuCount = 1;
        computeDescription.totalMemoryBytes = Integer.MAX_VALUE;
        computeDescription.id = UUID.randomUUID().toString();
        computeDescription.name = "friendly-name";
        computeDescription.regionId = "provider-specific-regions";
        computeDescription.zoneId = "provider-specific-zone";
        computeDescription = doPost(computeDescription, ComputeDescriptionService.FACTORY_LINK);

        ComputeStateWithDescription computeState = new ComputeService.ComputeStateWithDescription();
        computeState.name = name;
        computeState.type = type;
        computeState.id = UUID.randomUUID().toString();
        computeState.description = computeDescription;
        computeState.descriptionLink = computeDescription.documentSelfLink;
        computeState.resourcePoolLink = null;
        computeState.address = "10.0.0.1";
        computeState.primaryMAC = "01:23:45:67:89:ab";
        computeState.powerState = ComputeService.PowerState.ON;
        computeState.adapterManagementReference = URI.create("https://esxhost-01:443/sdk");
        computeState.diskLinks = new ArrayList<>();
        computeState.diskLinks.add("http://disk");
        computeState.networkInterfaceLinks = new ArrayList<>();
        computeState.networkInterfaceLinks.add("http://network");
        computeState.customProperties = new HashMap<>();
        computeState.endpointLink = endpointLink;
        computeState.tagLinks = new HashSet<String>(Arrays.asList(tagLinks));
        return doPost(computeState, ComputeService.FACTORY_LINK);
    }
}
