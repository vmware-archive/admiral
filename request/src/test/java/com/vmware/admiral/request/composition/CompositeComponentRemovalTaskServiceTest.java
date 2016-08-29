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

import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.RequestBaseTest;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.ServiceDocument;

public class CompositeComponentRemovalTaskServiceTest extends RequestBaseTest {

    @Test
    public void testRemoveEmptyCompositeComponent() throws Throwable {
        CompositeComponent composite = createCompositeComponent();

        RequestBrokerState day2RemovalRequest = new RequestBrokerState();
        day2RemovalRequest.resourceType = ResourceType.COMPOSITE_COMPONENT_TYPE.getName();
        day2RemovalRequest.resourceLinks = Collections.singletonList(composite.documentSelfLink);
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
        day2RemovalRequest.resourceLinks = new ArrayList<>(Arrays.asList(
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

    private CompositeComponent createCompositeComponent()throws Throwable {
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
        container.groupResourcePolicyLink = groupPolicyState.documentSelfLink;
        container = doPost(container, ContainerFactoryService.SELF_LINK);
        addForDeletion(container);
        return container;
    }

    private void verifyRemoved(ServiceDocument doc) throws Throwable {
        assertNull(searchForDocument(doc.getClass(), doc.documentSelfLink));
    }
}
