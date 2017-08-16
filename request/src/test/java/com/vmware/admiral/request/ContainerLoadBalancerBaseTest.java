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

import static org.junit.Assert.assertNotNull;

import java.util.UUID;

import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService
        .ContainerLoadBalancerDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService
        .ContainerLoadBalancerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;

public class ContainerLoadBalancerBaseTest extends RequestBaseTest {

    protected ContainerLoadBalancerDescription loadBalancerDesc;
    protected ContainerLoadBalancerState loadBalancerState;

    protected ContainerLoadBalancerDescription createContainerLoadBalancerDescription(String name)
            throws Throwable {
        synchronized (initializationLock) {
            if (loadBalancerDesc == null) {
                ContainerLoadBalancerDescription desc = TestRequestStateFactory
                        .createContainerLoadBalancerDescription(name);
                desc.documentSelfLink = UUID.randomUUID().toString();

                loadBalancerDesc = doPost(desc, ContainerLoadBalancerDescriptionService
                        .FACTORY_LINK);
                assertNotNull(loadBalancerDesc);
            }
            return loadBalancerDesc;
        }
    }

    protected ContainerLoadBalancerState createContainerLoadBalancerState(String lbdLink) throws
            Throwable {
        synchronized (initializationLock) {
            ContainerLoadBalancerState lbState = TestRequestStateFactory
                    .createContainerLoadBalancerState(UUID.randomUUID().toString());
            lbState.descriptionLink = lbdLink;

            loadBalancerState = doPost(lbState, ContainerLoadBalancerService.FACTORY_LINK);
            assertNotNull(loadBalancerState);
            return loadBalancerState;
        }
    }
}
