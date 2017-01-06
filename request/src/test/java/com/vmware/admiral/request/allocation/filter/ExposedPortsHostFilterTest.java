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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;

public class ExposedPortsHostFilterTest extends BaseAffinityHostFilterTest {

    @Test
    public void testFilterHostsWithExposedPorts() throws Throwable {
        assertEquals(3, initialHostLinks.size());

        ContainerDescription desc = createDescription(true);
        assertNotNull(desc.portBindings);
        ContainerState c1 = createContainer(desc, initialHostLinks.get(0));
        assertEquals(PowerState.RUNNING, c1.powerState);
        ContainerState c2 = createContainer(desc, initialHostLinks.get(1));
        assertEquals(PowerState.RUNNING, c2.powerState);

        filter = new ExposedPortsHostFilter(host, desc);
        Throwable e = filter(initialHostLinks.subList(2, initialHostLinks.size()));
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testFailOnNoHostsFound() throws Throwable {
        assertEquals(3, initialHostLinks.size());

        ContainerDescription desc = createDescription(true);
        createContainer(desc, initialHostLinks.get(0));
        createContainer(desc, initialHostLinks.get(1));
        createContainer(desc, initialHostLinks.get(2));

        filter = new ExposedPortsHostFilter(host, desc);
        Throwable e = filter(initialHostLinks);
        if (e == null) {
            fail("Expected an exception that no matching hosts were"
                    + "found with unexposed ports.");
        }
    }

    @Test
    public void testFilterNoHostsWhenNoPortsExposed() throws Throwable {
        assertEquals(3, initialHostLinks.size());

        ContainerDescription desc = createDescription(false);
        createContainer(desc, initialHostLinks.get(0));
        createContainer(desc, initialHostLinks.get(1));
        createContainer(desc, initialHostLinks.get(2));

        filter = new ExposedPortsHostFilter(host, desc);
        Throwable e = filter(initialHostLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    private ContainerDescription createDescription(boolean withExposedPorts) throws Throwable {
        ContainerDescription desc = TestRequestStateFactory.createContainerDescription();
        desc.documentSelfLink = UUID.randomUUID().toString();
        if (!withExposedPorts) {
            desc.portBindings = null;
        } else {
            assertNotNull(desc.portBindings);
        }
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);
        return desc;
    }
}
