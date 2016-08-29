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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class CompositeDescriptionCloneServiceTest extends ComputeBaseTest {

    private ContainerDescription createdFirstContainer;
    private ContainerDescription createdSecondContainer;
    private CompositeDescription createdCompositeWithoutContainers;
    private CompositeDescription createdCompositeWithTwoContainers;

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
    }

    @Before
    public void initObjects() throws Throwable {
        ContainerDescription firstContainer = new ContainerDescription();
        firstContainer.name = "testContainer1";
        firstContainer.image = "registry.hub.docker.com/nginx";
        firstContainer._cluster = 1;
        firstContainer.maximumRetryCount = 1;
        firstContainer.privileged = true;
        firstContainer.affinity = new String[] { "cond1", "cond2" };
        firstContainer.customProperties = new HashMap<String, String>();
        firstContainer.customProperties.put("key1", "value1");
        firstContainer.customProperties.put("key2", "value2");

        ContainerDescription secondContainer = new ContainerDescription();
        secondContainer.name = "testContainer2";
        secondContainer.image = "registry.hub.docker.com/kitematic/hello-world-nginx";

        createdFirstContainer = doPost(firstContainer, ContainerDescriptionService.FACTORY_LINK);
        createdSecondContainer = doPost(firstContainer, ContainerDescriptionService.FACTORY_LINK);

        CompositeDescription firstComposite = new CompositeDescription();
        firstComposite.name = "testComposite1";
        firstComposite.customProperties = new HashMap<String, String>();
        firstComposite.customProperties.put("key1", "value1");
        firstComposite.customProperties.put("key2", "value2");
        firstComposite.descriptionLinks = new ArrayList<String>();

        createdCompositeWithoutContainers = doPost(firstComposite,
                CompositeDescriptionService.SELF_LINK);

        CompositeDescription secondComposite = new CompositeDescription();
        secondComposite.name = "testComposite2";
        secondComposite.customProperties = new HashMap<String, String>();
        secondComposite.customProperties.put("key1", "value1");
        secondComposite.customProperties.put("key2", "value2");
        secondComposite.descriptionLinks = new ArrayList<String>();
        secondComposite.descriptionLinks.add(createdFirstContainer.documentSelfLink);
        secondComposite.descriptionLinks.add(createdSecondContainer.documentSelfLink);

        createdCompositeWithTwoContainers = doPost(secondComposite,
                CompositeDescriptionService.SELF_LINK);
    }

    @Test
    public void testCloneCompositeDescriptionWithTwoContainers() throws Throwable {
        CompositeDescription clonedCompositeDesc = cloneCompositeDesc(createdCompositeWithTwoContainers);

        checkCompositeForEquality(createdCompositeWithTwoContainers, clonedCompositeDesc);

        List<String> containerDescripsions = clonedCompositeDesc.descriptionLinks;

        ContainerDescription clonedFirstContainer = getDocument(ContainerDescription.class,
                containerDescripsions.get(0));
        checkContainersForЕquality(createdFirstContainer, clonedFirstContainer);

        ContainerDescription clonedSecondContainer = getDocument(ContainerDescription.class,
                containerDescripsions.get(1));
        checkContainersForЕquality(createdSecondContainer, clonedSecondContainer);
    }

    @Test
    public void testCloneCompositeDescriptionWithoutContainers() throws Throwable {
        CompositeDescription clonedCompositeDesc = cloneCompositeDesc(createdCompositeWithoutContainers);

        checkCompositeForEquality(createdCompositeWithoutContainers, clonedCompositeDesc);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCloneNoCompositeDescription() throws Throwable {
        cloneCompositeDesc(null);
    }

    private CompositeDescription cloneCompositeDesc(CompositeDescription compositeDesc)
            throws Throwable {
        CompositeDescription[] result = new CompositeDescription[] { null };
        Operation cloneCompositeDesc = Operation.createPost(
                UriUtils.buildUri(host, CompositeDescriptionCloneService.SELF_LINK))
                .setBody(compositeDesc)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                host.log("Can't clone composite description.", Utils.toString(e));
                                host.failIteration(e);
                                return;
                            }

                            CompositeDescription cd = o.getBody(CompositeDescription.class);
                            result[0] = cd;
                            host.completeIteration();
                        });
        host.testStart(1);
        host.send(cloneCompositeDesc);
        host.testWait();

        CompositeDescription clonedCompositeDesc = result[0];

        return clonedCompositeDesc;
    }

    private void checkContainersForЕquality(ContainerDescription createdContainer,
            ContainerDescription clonedContainer) {
        assertNotNull(createdContainer);
        assertNotNull(clonedContainer);
        assertNotEquals(createdContainer.documentSelfLink, clonedContainer.documentSelfLink);
        assertEquals(createdContainer.name, clonedContainer.name);
        assertEquals(createdContainer.image, clonedContainer.image);
        assertEquals(createdContainer._cluster, clonedContainer._cluster);
        assertEquals(createdContainer.maximumRetryCount, clonedContainer.maximumRetryCount);
        assertEquals(createdContainer.privileged, clonedContainer.privileged);
        assertTrue(Arrays.equals(createdContainer.affinity, clonedContainer.affinity));
        assertTrue(Arrays.equals(createdContainer.env, clonedContainer.env));
        assertEquals(createdContainer.customProperties, clonedContainer.customProperties);
        assertEquals(createdContainer.tenantLinks, clonedContainer.tenantLinks);
    }

    private void checkCompositeForEquality(CompositeDescription createdComposite,
            CompositeDescription clonedComposite) {
        assertNotNull(createdComposite);
        assertNotNull(clonedComposite);
        assertNotEquals(createdComposite.documentSelfLink, clonedComposite.documentSelfLink);
        assertEquals(createdComposite.name, clonedComposite.name);
        assertEquals(createdComposite.documentSelfLink, clonedComposite.parentDescriptionLink);
        assertEquals(createdComposite.tenantLinks, clonedComposite.tenantLinks);
        assertEquals(createdComposite.customProperties, clonedComposite.customProperties);
        assertEquals(createdComposite.descriptionLinks.size(),
                clonedComposite.descriptionLinks.size());
    }
}
