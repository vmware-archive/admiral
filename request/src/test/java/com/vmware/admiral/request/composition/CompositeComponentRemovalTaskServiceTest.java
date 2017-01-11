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

package com.vmware.admiral.request.composition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.composition.CompositionSubTaskService.CompositionSubTaskState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

public class CompositeComponentRemovalTaskServiceTest extends RequestBaseTest {

    @Test
    public void testRemoveEmptyCompositeComponent() throws Throwable {
        CompositeComponent composite = createCompositeComponent();

        RequestBrokerState day2RemovalRequest = new RequestBrokerState();
        day2RemovalRequest.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        day2RemovalRequest.resourceLinks = new HashSet<>(Collections.singletonList(
                composite.documentSelfLink));
        day2RemovalRequest.operation = ContainerOperationType.DELETE.id;

        day2RemovalRequest = startRequest(day2RemovalRequest);
        waitForRequestToComplete(day2RemovalRequest);

        // verify the CompositeComponent has been removed
        composite = searchForDocument(CompositeComponent.class, composite.documentSelfLink);
        assertNull(composite);
    }

    @Test
    public void testRemoveCompositeComponentsWithContainers() throws Throwable {
        CompositeComponent composite1 = createCompositeComponent();
        ContainerState container1 = createContainer(composite1);
        ContainerState container2 = createContainer(composite1);

        CompositeComponent composite2 = createCompositeComponent();
        ContainerState container3 = createContainer(composite2);

        RequestBrokerState day2RemovalRequest = new RequestBrokerState();
        day2RemovalRequest.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        day2RemovalRequest.resourceLinks = new HashSet<>(Arrays.asList(
                composite1.documentSelfLink, composite2.documentSelfLink));
        day2RemovalRequest.operation = ContainerOperationType.DELETE.id;

        day2RemovalRequest = startRequest(day2RemovalRequest);
        waitForRequestToComplete(day2RemovalRequest);

        // verify the CompositeComponents has been removed
        verifyRemoved(composite1);
        verifyRemoved(container1);
        verifyRemoved(container2);

        verifyRemoved(composite2);
        verifyRemoved(container3);
    }

    @Test
    public void testRemoveCompositeComponentsWithMix() throws Throwable {
        CompositeComponent composite1 = createCompositeComponent();
        ContainerState container1 = createContainer(composite1);
        ContainerState container2 = createContainer(composite1);
        ContainerNetworkState network = createNetwork(composite1);
        ContainerVolumeState volume = createVolume(composite1);

        // TODO: uncomment when composite component starts handles compute
        // ComputeState compute = createCompute(composite1);

        RequestBrokerState day2RemovalRequest = new RequestBrokerState();
        day2RemovalRequest.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        day2RemovalRequest.resourceLinks = new HashSet<>(
                Arrays.asList(composite1.documentSelfLink));
        day2RemovalRequest.operation = ContainerOperationType.DELETE.id;

        day2RemovalRequest = startRequest(day2RemovalRequest);
        waitForRequestToComplete(day2RemovalRequest);

        verifyRemoved(composite1);
        verifyRemoved(container1);
        verifyRemoved(container2);
        verifyRemoved(network);
        verifyRemoved(volume);

        List<CompositionSubTaskState> queryCompositionSubTasks = queryCompositionSubTasks();

        // 1 for container, 1 for network, 1 for volume
        assertEquals(3, queryCompositionSubTasks.size());

        CompositionSubTaskState containerSubTaskState = null;
        CompositionSubTaskState networkSubTaskState = null;
        CompositionSubTaskState volumeSubTaskState = null;
        for (CompositionSubTaskState compositionSubTaskState : queryCompositionSubTasks) {
            if (compositionSubTaskState.documentSelfLink
                    .endsWith(ResourceType.CONTAINER_TYPE.getName())) {
                containerSubTaskState = compositionSubTaskState;
            } else if (compositionSubTaskState.documentSelfLink
                    .endsWith(ResourceType.CONTAINER_NETWORK_TYPE.getName())) {
                networkSubTaskState = compositionSubTaskState;
            } else if (compositionSubTaskState.documentSelfLink
                    .endsWith(ResourceType.CONTAINER_VOLUME_TYPE.getName())) {
                volumeSubTaskState = compositionSubTaskState;
            } else {
                fail("Unexpected compositionSubTaskState: "
                        + compositionSubTaskState.documentSelfLink);
            }
        }

        assertNotNull(containerSubTaskState);
        assertNotNull(networkSubTaskState);
        assertNotNull(volumeSubTaskState);

        assertNull(containerSubTaskState.dependsOnLinks);
        assertTrue(containerSubTaskState.dependentLinks
                .contains(networkSubTaskState.documentSelfLink));
    }

    @Test
    public void testRemoveCompositeComponentsWithExternalNetwork() throws Throwable {
        CompositeComponent composite1 = createCompositeComponent();
        ContainerState container1 = createContainer(composite1);
        ContainerNetworkState network = createNetwork(composite1, true);

        RequestBrokerState day2RemovalRequest = new RequestBrokerState();
        day2RemovalRequest.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        day2RemovalRequest.resourceLinks = new HashSet<>(
                Arrays.asList(composite1.documentSelfLink));
        day2RemovalRequest.operation = ContainerOperationType.DELETE.id;

        day2RemovalRequest = startRequest(day2RemovalRequest);
        waitForRequestToComplete(day2RemovalRequest);

        verifyRemoved(composite1);
        verifyRemoved(container1);

        ContainerNetworkState net = getDocument(ContainerNetworkState.class, network.documentSelfLink);

        assertEquals(0, net.compositeComponentLinks.size());
    }

    private CompositeComponent createCompositeComponent() throws Throwable {
        CompositeComponent composite = new CompositeComponent();
        composite.name = "test-name";
        composite = doPost(composite, CompositeComponentFactoryService.SELF_LINK);
        addForDeletion(composite);
        return composite;
    }

    private ContainerState createContainer(CompositeComponent composite) throws Throwable {
        ContainerState container = TestRequestStateFactory.createContainer();
        container.descriptionLink = containerDesc.documentSelfLink;
        container.adapterManagementReference = containerDesc.instanceAdapterReference;
        container.compositeComponentLink = composite.documentSelfLink;
        container.groupResourcePlacementLink = groupPlacementState.documentSelfLink;
        container = doPost(container, ContainerFactoryService.SELF_LINK);
        addForDeletion(container);
        return container;
    }

    private ContainerNetworkState createNetwork(CompositeComponent composite) throws Throwable {
        return createNetwork(composite, false);
    }

    private ContainerNetworkState createNetwork(CompositeComponent composite, boolean isExternal) throws Throwable {
        ContainerNetworkDescription networkDesc = TestRequestStateFactory
                .createContainerNetworkDescription("test-net");
        networkDesc = doPost(networkDesc, ContainerNetworkDescriptionService.FACTORY_LINK);
        addForDeletion(networkDesc);

        ContainerNetworkState network = TestRequestStateFactory.createNetwork("test-net-003");
        network.compositeComponentLinks = new ArrayList<>();
        network.compositeComponentLinks.add(composite.documentSelfLink);
        network.adapterManagementReference = networkDesc.instanceAdapterReference;
        network.descriptionLink = networkDesc.documentSelfLink;
        network.external = isExternal;
        network = doPost(network, ContainerNetworkService.FACTORY_LINK);
        addForDeletion(network);
        return network;
    }

    private ContainerVolumeState createVolume(CompositeComponent composite) throws Throwable {
        return createVolume(composite, false);
    }

    private ContainerVolumeState createVolume(CompositeComponent composite, boolean isExternal) throws Throwable {
        ContainerVolumeDescription volumeDesc = TestRequestStateFactory
                .createContainerVolumeDescription("test-volume");
        volumeDesc = doPost(volumeDesc, ContainerVolumeDescriptionService.FACTORY_LINK);
        addForDeletion(volumeDesc);

        ContainerVolumeState volume = TestRequestStateFactory.createVolume("test-volume-003");
        volume.compositeComponentLinks = new ArrayList<>();
        volume.compositeComponentLinks.add(composite.documentSelfLink);
        volume.adapterManagementReference = volumeDesc.instanceAdapterReference;
        volume.descriptionLink = volumeDesc.documentSelfLink;
        volume.external = isExternal;
        volume = doPost(volume, ContainerVolumeService.FACTORY_LINK);
        addForDeletion(volume);
        return volume;
    }

    private void verifyRemoved(ServiceDocument doc) throws Throwable {
        assertNull(searchForDocument(doc.getClass(), doc.documentSelfLink));
    }

    private List<CompositionSubTaskState> queryCompositionSubTasks() {
        QueryTask q = QueryUtil.buildQuery(CompositionSubTaskState.class, false);
        q.querySpec.options = EnumSet.of(QueryOption.EXPAND_CONTENT);
        ServiceDocumentQuery<CompositionSubTaskState> query = new ServiceDocumentQuery<>(host,
                CompositionSubTaskState.class);

        List<CompositionSubTaskState> result = new ArrayList<>();
        TestContext ctx = testCreate(1);

        query.query(q, (r) -> {
            if (r.hasException()) {
                ctx.failIteration(r.getException());
            } else if (r.hasResult()) {
                result.add(r.getResult());
            } else {
                ctx.completeIteration();
            }
        });

        ctx.await();

        return result;
    }
}
