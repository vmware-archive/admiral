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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;

public class SystemContainerDescriptionsTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);
    }

    @Test
    public void verifyDefaultContainerDescriptionCreatedOnStartup() throws Throwable {

        ContainerDescription agentContainerDesc = getDocument(
                ContainerDescription.class,
                SystemContainerDescriptions.AGENT_CONTAINER_DESCRIPTION_LINK);
        assertNotNull(agentContainerDesc);
        assertEquals(SystemContainerDescriptions.AGENT_CONTAINER_NAME,
                agentContainerDesc.name);
        String expectedImageName = String.format("%s:%s",
                SystemContainerDescriptions.AGENT_IMAGE_NAME,
                SystemContainerDescriptions.AGENT_IMAGE_VERSION);
        assertEquals(expectedImageName, agentContainerDesc.image);
    }
}
