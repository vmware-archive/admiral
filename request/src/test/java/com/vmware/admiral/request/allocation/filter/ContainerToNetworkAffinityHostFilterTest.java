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

package com.vmware.admiral.request.allocation.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.UriUtils;

public class ContainerToNetworkAffinityHostFilterTest extends BaseAffinityHostFilterTest {

    @Test
    public void testFilterDoesNotAffectHosts() throws Throwable {
        assertEquals(3, initialHostLinks.size());

        ContainerDescription desc = createDescription(new String[] {});
        createContainer(desc, initialHostLinks.get(0));
        createContainer(desc, initialHostLinks.get(1));

        filter = new ContainerToNetworkAffinityHostFilter(host, desc);
        Throwable e = filter(initialHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testResolveContainerActualStateNames() throws Throwable {
        ContainerNetworkDescription networkDescription1 = createNetworkDescription("my-test-net");
        String randomName = networkDescription1.name + "-name35";
        ContainerNetworkState networkState1 = createNetwork(networkDescription1, randomName);

        ContainerNetworkDescription networkDescription2 = createNetworkDescription(
                "my-other-test-net");
        randomName = networkDescription2.name + "-name270";
        ContainerNetworkState networkState2 = createNetwork(networkDescription2, randomName);

        ContainerDescription desc = createDescription(new String[] { networkDescription1.name,
                networkDescription2.name });

        filter = new ContainerToNetworkAffinityHostFilter(host, desc);
        Map<String, HostSelection> filterToHostSelectionMap = filterToHostSelectionMap();

        for (HostSelection hs : filterToHostSelectionMap.values()) {
            String mappedName = hs.mapNames(new String[] { networkDescription1.name })[0];
            assertEquals(networkState1.name, mappedName);

            mappedName = hs.mapNames(new String[] { networkDescription2.name })[0];
            assertEquals(networkState2.name, mappedName);
        }
    }

    @Test
    public void testInactiveWithoutNetworks() throws Throwable {
        ContainerDescription desc = createDescription(new String[] {});
        filter = new ContainerToNetworkAffinityHostFilter(host, desc);
        assertFalse(filter.isActive());
    }

    @Test
    public void testAffinityConstraintsToNetworks() throws Throwable {
        ContainerDescription desc = createDescription(new String[] { "net1", "net2", "net3" });
        filter = new ContainerToNetworkAffinityHostFilter(host, desc);
        assertTrue(filter.isActive());

        Set<String> affinityConstraintsKeys = filter.getAffinityConstraints().keySet();

        HashSet<Object> expectedNets = new HashSet<>();
        expectedNets.add("net1");
        expectedNets.add("net2");
        expectedNets.add("net3");
        assertEquals(expectedNets, affinityConstraintsKeys);
    }

    private ContainerDescription createDescription(String[] networkNames)
            throws Throwable {
        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.name = desc.documentSelfLink;

        Map<String, ServiceNetwork> serviceNetworks = new HashMap<String, ServiceNetwork>();
        for (String networkName : networkNames) {
            serviceNetworks.put(networkName, new ServiceNetwork());
        }
        desc.networks = serviceNetworks;

        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }

    private ContainerNetworkDescription createNetworkDescription(String networkName)
            throws Throwable {
        ContainerNetworkDescription desc = new ContainerNetworkDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.name = networkName;

        desc = doPost(desc, ContainerNetworkDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);

        return desc;
    }

    private ContainerNetworkState createNetwork(ContainerNetworkDescription desc, String name)
            throws Throwable {
        ContainerNetworkState containerNetwork = new ContainerNetworkState();
        containerNetwork.descriptionLink = desc.documentSelfLink;
        containerNetwork.id = UUID.randomUUID().toString();
        containerNetwork.name = name;
        containerNetwork.compositeComponentLink = UriUtils.buildUriPath(
                CompositeComponentFactoryService.SELF_LINK, state.contextId);
        containerNetwork = doPost(containerNetwork, ContainerNetworkService.FACTORY_LINK);
        assertNotNull(containerNetwork);
        addForDeletion(containerNetwork);
        return containerNetwork;
    }

    private Map<String, HostSelection> filterToHostSelectionMap() throws Throwable {
        Throwable[] error = new Throwable[] { null };
        final Map<String, HostSelection> hostSelectionMap = new HashMap<>();
        for (String hostLink : initialHostLinks) {
            HostSelection hostSelection = new HostSelection();
            hostSelection.hostLink = hostLink;
            hostSelectionMap.put(hostLink, hostSelection);
        }
        host.testStart(1);
        filter
                .filter(
                        state,
                        hostSelectionMap,
                        (filteredHostSelectionMap, e) -> {
                            if (e != null) {
                                error[0] = e;
                            } else {
                                hostSelectionMap.clear();
                                hostSelectionMap.putAll(filteredHostSelectionMap);
                            }
                            host.completeIteration();
                        });

        host.testWait();
        if (error[0] != null) {
            throw error[0];
        }
        return hostSelectionMap;
    }

}
