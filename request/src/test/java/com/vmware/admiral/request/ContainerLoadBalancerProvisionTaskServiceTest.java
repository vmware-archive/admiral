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

package com.vmware.admiral.request;

import static junit.framework.TestCase.assertNotNull;

import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Tests for the {@link ContainerLoadBalancerProvisionTaskService} class.
 */
public class ContainerLoadBalancerProvisionTaskServiceTest extends ContainerLoadBalancerBaseTest {

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();

        // setup description
        TestRequestStateFactory.createContainerDescription("wp", false, false);
        createContainerLoadBalancerDescription(UUID.randomUUID().toString());
    }

    @Test
    public void testProvisionTaskServiceLifeCycle() throws Throwable {
        ServiceDocument[] composition = new ServiceDocument[2];
        composition[0] = loadBalancerDesc;
        composition[1] = TestRequestStateFactory.createContainerDescription("wp", false, false);
        CompositeDescription compositeDesc = createCompositeDesc(composition);

        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.tenantLinks = groupPlacementState.tenantLinks;

        request = startRequest(request);
        request = waitForTaskSuccess(request.documentSelfLink, RequestBrokerState.class);
        assertNotNull(request.resourceLinks);
    }

    @Test
    public void testProvisionAndRemoveContainerLoadBalancer() throws Throwable {
        createContainerLoadBalancerDescription(UUID.randomUUID().toString());
        ServiceDocument[] composition = new ServiceDocument[2];
        composition[0] = loadBalancerDesc;
        composition[1] = TestRequestStateFactory.createContainerDescription("wp", false, false);
        CompositeDescription compositeDesc = createCompositeDesc(composition);

        RequestBrokerState request = TestRequestStateFactory.createRequestState(
                ResourceType.COMPOSITE_COMPONENT_TYPE.getName(), compositeDesc.documentSelfLink);
        request.tenantLinks = groupPlacementState.tenantLinks;

        request = startRequest(request);
        request = waitForTaskSuccess(request.documentSelfLink, RequestBrokerState.class);
        assertNotNull(request.resourceLinks);

        CompositeComponent cc = getDocumentNoWait(CompositeComponent.class, request.resourceLinks
                .iterator().next());

        String loadBalancerComponentLink = cc.componentLinks.stream()
                .filter(link -> link.startsWith(ContainerLoadBalancerService.FACTORY_LINK))
                .collect(Collectors.toList()).iterator().next();

        RequestBrokerState req = TestRequestStateFactory.createRequestState();
        req.resourceType = ResourceType.CONTAINER_LOAD_BALANCER_TYPE.getName();
        req.operation = ContainerLoadBalancerOperationType.DELETE.id;
        req.resourceLinks = new HashSet<>();
        req.resourceLinks.add(loadBalancerComponentLink);
        req = startRequest(req);

        waitForTaskSuccess(req.documentSelfLink, RequestBrokerState.class);
    }

}

