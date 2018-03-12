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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
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

    @Test
    public void testCloneCompositeDescriptionWithTwoContainers() throws Throwable {
        initObjectsWithTwoContainers();

        CompositeDescription clonedCompositeDesc = cloneCompositeDesc(
                createdCompositeWithTwoContainers, false);

        checkCompositeForEquality(createdCompositeWithTwoContainers, clonedCompositeDesc, false);

        List<String> containerDescriptions = clonedCompositeDesc.descriptionLinks;

        ContainerDescription clonedFirstContainer = getDocument(ContainerDescription.class,
                containerDescriptions.get(0));
        checkContainersForЕquality(createdFirstContainer, clonedFirstContainer, false);

        ContainerDescription clonedSecondContainer = getDocument(ContainerDescription.class,
                containerDescriptions.get(1));
        checkContainersForЕquality(createdSecondContainer, clonedSecondContainer, false);
    }

    @Test
    public void testCloneCompositeDescriptionWithTwoContainersEmbeded() throws Throwable {

        initObjectsWithTwoContainers();

        ConfigurationState config = new ConfigurationState();
        config.key = "embedded";
        config.value = Boolean.toString(true);
        ConfigurationUtil.initialize(config);

        //when cloning in embedded mode the group tenant link should not be deleted
        CompositeDescription clonedCompositeDesc = cloneCompositeDesc(
                createdCompositeWithTwoContainers, false);

        assertNotNull(clonedCompositeDesc);
        assertNotNull(clonedCompositeDesc.tenantLinks);
        assertEquals(createdCompositeWithTwoContainers.tenantLinks.size(),
                clonedCompositeDesc.tenantLinks.size());

        List<String> containerDescriptions = clonedCompositeDesc.descriptionLinks;

        ContainerDescription clonedFirstContainer = getDocument(ContainerDescription.class,
                containerDescriptions.get(0));

        assertNotNull(clonedFirstContainer);
        assertNotNull(clonedFirstContainer.tenantLinks);
        assertEquals(createdFirstContainer.tenantLinks.size(),
                clonedFirstContainer.tenantLinks.size());

        ContainerDescription clonedSecondContainer = getDocument(ContainerDescription.class,
                containerDescriptions.get(1));

        assertNotNull(clonedSecondContainer);
        assertNotNull(clonedSecondContainer.tenantLinks);
        assertEquals(clonedSecondContainer.tenantLinks.size(),
                clonedSecondContainer.tenantLinks.size());

        config = new ConfigurationState();
        config.key = "embedded";
        config.value = Boolean.toString(false);
        ConfigurationUtil.initialize(config);
    }

    @Test
    public void testCloneCompositeDescriptionWithTwoContainersAndReverseParentLinks()
            throws Throwable {
        initObjectsWithTwoContainers();

        CompositeDescription clonedCompositeDesc = cloneCompositeDesc(
                createdCompositeWithTwoContainers, true);

        checkCompositeForEquality(createdCompositeWithTwoContainers, clonedCompositeDesc, true);

        List<String> containerDescriptions = clonedCompositeDesc.descriptionLinks;

        ContainerDescription clonedFirstContainer = getDocument(ContainerDescription.class,
                containerDescriptions.get(0));
        checkContainersForЕquality(createdFirstContainer, clonedFirstContainer, true);

        ContainerDescription clonedSecondContainer = getDocument(ContainerDescription.class,
                containerDescriptions.get(1));
        checkContainersForЕquality(createdSecondContainer, clonedSecondContainer, true);
    }

    @Test
    public void testCloneCompositeDescriptionWithoutContainers() throws Throwable {
        initObjectsWithoutContainers();

        CompositeDescription clonedCompositeDesc = cloneCompositeDesc(
                createdCompositeWithoutContainers, false);

        checkCompositeForEquality(createdCompositeWithoutContainers, clonedCompositeDesc, false);
    }

    @Test
    public void testCloneCompositeDescriptionWithoutContainersAndReverseParentLinks()
            throws Throwable {
        initObjectsWithoutContainers();

        CompositeDescription clonedCompositeDesc = cloneCompositeDesc(
                createdCompositeWithoutContainers, true);

        checkCompositeForEquality(createdCompositeWithoutContainers, clonedCompositeDesc, true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCloneNoCompositeDescription() throws Throwable {
        cloneCompositeDesc(null, false);
    }

    private void initObjectsWithTwoContainers() throws Throwable {
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
        firstContainer.tenantLinks = new LinkedList<>();
        firstContainer.tenantLinks.add("/tenants/qe");
        firstContainer.tenantLinks.add("/user/fritz@sdfdsf.dsf");
        firstContainer.tenantLinks.add("/tenants/qe/groups/dftyguhijokpl");

        ContainerDescription secondContainer = new ContainerDescription();
        secondContainer.name = "testContainer2";
        secondContainer.image = "registry.hub.docker.com/kitematic/hello-world-nginx";

        createdFirstContainer = doPost(firstContainer, ContainerDescriptionService.FACTORY_LINK);
        createdSecondContainer = doPost(firstContainer, ContainerDescriptionService.FACTORY_LINK);

        CompositeDescription secondComposite = new CompositeDescription();
        secondComposite.name = "testComposite2";
        secondComposite.customProperties = new HashMap<String, String>();
        secondComposite.customProperties.put("key1", "value1");
        secondComposite.customProperties.put("key2", "value2");
        secondComposite.descriptionLinks = new ArrayList<String>();
        secondComposite.descriptionLinks.add(createdFirstContainer.documentSelfLink);
        secondComposite.descriptionLinks.add(createdSecondContainer.documentSelfLink);
        secondComposite.tenantLinks = new LinkedList<>();
        secondComposite.tenantLinks.add("/tenants/qe");
        secondComposite.tenantLinks.add("/user/fritz@sdfdsf.dsf");
        secondComposite.tenantLinks.add("/tenants/qe/groups/dftyguhijokpl");

        createdCompositeWithTwoContainers = doPost(secondComposite,
                CompositeDescriptionService.FACTORY_LINK);
    }

    private void initObjectsWithoutContainers() throws Throwable {
        CompositeDescription firstComposite = new CompositeDescription();
        firstComposite.name = "testComposite1";
        firstComposite.customProperties = new HashMap<String, String>();
        firstComposite.customProperties.put("key1", "value1");
        firstComposite.customProperties.put("key2", "value2");
        firstComposite.descriptionLinks = new ArrayList<String>();

        createdCompositeWithoutContainers = doPost(firstComposite,
                CompositeDescriptionService.FACTORY_LINK);
    }

    private CompositeDescription cloneCompositeDesc(CompositeDescription compositeDesc,
            boolean reverse) throws Throwable {
        CompositeDescription[] result = new CompositeDescription[] { null };
        URI cloneOpUri = UriUtils.buildUri(host, CompositeDescriptionCloneService.SELF_LINK);
        if (reverse) {
            cloneOpUri = UriUtils.appendQueryParam(cloneOpUri,
                    CompositeDescriptionCloneService.REVERSE_PARENT_LINKS_PARAM,
                    Boolean.TRUE.toString());
        }
        Operation cloneCompositeDesc = Operation.createPost(cloneOpUri)
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
            ContainerDescription clonedContainer, boolean reverse) throws Throwable {

        // we have to load the proper createdContainer and clonedContainer because the cloning
        // operations of the composite description components are done in parallel and there's no
        // guarantee that the original order will be kept for the cloned composite description
        // components

        if (reverse) {
            createdContainer = getDocument(ContainerDescription.class,
                    createdContainer.documentSelfLink);

            assertNotNull(createdContainer.parentDescriptionLink);

            clonedContainer = getDocument(ContainerDescription.class,
                    createdContainer.parentDescriptionLink);

            assertEquals(clonedContainer.documentSelfLink, createdContainer.parentDescriptionLink);
            assertNull(clonedContainer.parentDescriptionLink);
        } else {
            assertNotNull(clonedContainer.parentDescriptionLink);

            createdContainer = getDocument(ContainerDescription.class,
                    clonedContainer.parentDescriptionLink);

            assertEquals(createdContainer.documentSelfLink, clonedContainer.parentDescriptionLink);
            assertNull(createdContainer.parentDescriptionLink);
        }

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
            CompositeDescription clonedComposite, boolean reverse) throws Throwable {

        createdComposite = getDocument(CompositeDescription.class,
                createdComposite.documentSelfLink);

        assertNotNull(createdComposite);
        assertNotNull(clonedComposite);
        assertNotEquals(createdComposite.documentSelfLink, clonedComposite.documentSelfLink);
        assertEquals(createdComposite.name, clonedComposite.name);
        if (reverse) {
            assertEquals(clonedComposite.documentSelfLink, createdComposite.parentDescriptionLink);
            assertNull(clonedComposite.parentDescriptionLink);
        } else {
            assertEquals(createdComposite.documentSelfLink, clonedComposite.parentDescriptionLink);
            assertNull(createdComposite.parentDescriptionLink);
        }
        assertEquals(createdComposite.tenantLinks, clonedComposite.tenantLinks);
        assertEquals(createdComposite.customProperties, clonedComposite.customProperties);
        assertEquals(createdComposite.descriptionLinks.size(),
                clonedComposite.descriptionLinks.size());
    }
}
