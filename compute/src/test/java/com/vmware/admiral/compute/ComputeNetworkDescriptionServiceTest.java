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

package com.vmware.admiral.compute;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.vmware.admiral.compute.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.xenon.common.FactoryService;

public class ComputeNetworkDescriptionServiceTest extends ComputeBaseTest {

    @Test
    public void testComputeNetworkDescriptionServices() throws Throwable {
        verifyService(
                FactoryService.create(ComputeNetworkDescriptionService.class),
                ComputeNetworkDescription.class,
                (prefix, index) -> {
                    ComputeNetworkDescription networkDesc = new ComputeNetworkDescription();
                    networkDesc.name = prefix + "name" + index;
                    return networkDesc;
                },
                (prefix, serviceDocument) -> {
                    ComputeNetworkDescription networkDesc = (ComputeNetworkDescription) serviceDocument;
                    assertTrue(networkDesc.name.startsWith(prefix + "name"));
                });
    }

}