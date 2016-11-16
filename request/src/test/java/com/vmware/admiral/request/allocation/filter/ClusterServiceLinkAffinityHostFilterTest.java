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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.request.util.TestRequestStateFactory;
import com.vmware.admiral.request.utils.RequestUtils;

public class ClusterServiceLinkAffinityHostFilterTest extends BaseAffinityHostFilterTest {
    @Test
    public void testSelectHostWhenNoClustering() throws Throwable {
        // create composite component
        CompositeComponent component = createComponent("app-with-links-test");

        // create 2 container descriptions with links wordpress -> mysql
        ContainerDescription desc1 = createDescription("mysql", null);
        ContainerDescription desc2 = createDescription("wordpress", new String[] {"mysql:mysql"});

        // create 2 containers in the component
        createContainer(component, desc1);
        createContainer(component, desc2);

        filter = new ClusterServiceLinkAffinityHostFilter(host, desc1);
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectHostExistingContainerWithLink() throws Throwable {
        // create composite component
        CompositeComponent component = createComponent("app-with-links-test");

        // create 2 container descriptions with links wordpress -> mysql
        ContainerDescription desc1 = createDescription("mysql", null);
        ContainerDescription desc2 = createDescription("wordpress", new String[] {"mysql:mysql"});

        // create 2 containers in the component
        createContainer(component, desc1);
        createContainer(component, desc2);

        String contextId = extractId(component.documentSelfLink);
        state.contextId = contextId;
        state.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        state.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP, "true");

        // place a new container which another container has a link to it (worpress -> mysql)
        filter = new ClusterServiceLinkAffinityHostFilter(host, desc1);

        // filter selects the host where existing mysql is placed
        expectedLinks = Arrays.asList(initialHostLinks.get(0));
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectHostExistingContainerWithLinkNoAlias() throws Throwable {
        // create composite component
        CompositeComponent component = createComponent("app-with-links-test");

        // create 2 container descriptions with links wordpress -> mysql
        ContainerDescription desc1 = createDescription("mysql", null);
        // mysql link has no alias (only service)
        ContainerDescription desc2 = createDescription("wordpress", new String[] {"mysql"});

        // create 2 containers in the component
        createContainer(component, desc1);
        createContainer(component, desc2);

        String contextId = extractId(component.documentSelfLink);
        state.contextId = contextId;
        state.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        state.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP, "true");

        // place a new container which another container has a link to it (worpress -> mysql)
        filter = new ClusterServiceLinkAffinityHostFilter(host, desc1);

        // filter selects the host where existing mysql is placed
        expectedLinks = Arrays.asList(initialHostLinks.get(0));
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testSelectHostExistingContainerWithoutLink() throws Throwable {
        // create composite component
        CompositeComponent component = createComponent("app-with-links-test");

        // create 2 container descriptions (no links between them)
        ContainerDescription desc1 = createDescription("mysql", null);
        ContainerDescription desc2 = createDescription("wordpress", null);

        // create 2 containers in the component
        createContainer(component, desc1);
        createContainer(component, desc2);

        String contextId = extractId(component.documentSelfLink);
        state.contextId = contextId;
        state.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, contextId);
        state.addCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP, "true");

        // place a new container (no links from or to it)
        filter = new ClusterServiceLinkAffinityHostFilter(host, desc1);

        // filter selects all the available hosts
        Throwable e = filter(expectedLinks);
        if (e != null) {
            fail("Unexpected exception: " + e);
        }
    }

    private CompositeComponent createComponent(String name) throws Throwable {
        CompositeComponent component = new CompositeComponent();
        component.name = name;
        component = doPost(component, CompositeComponentFactoryService.SELF_LINK);
        assertNotNull(component);
        addForDeletion(component);
        return component;
    }

    private ContainerState createContainer(CompositeComponent component,
            ContainerDescription containerDesc) throws Throwable {
        ContainerState container = TestRequestStateFactory.createContainer();
        container.parentLink = initialHostLinks.get(0);
        container.descriptionLink = containerDesc.documentSelfLink;
        container.adapterManagementReference = containerDesc.instanceAdapterReference;
        container.compositeComponentLink = component.documentSelfLink;
        container.groupResourcePlacementLink = groupPlacementState.documentSelfLink;
        container = doPost(container, ContainerFactoryService.SELF_LINK);
        assertNotNull(container);
        addForDeletion(container);
        return container;
    }

    private ContainerDescription createDescription(String name, String[] links) throws Throwable {
        ContainerDescription desc = TestRequestStateFactory.createContainerDescription(name);
        desc.documentSelfLink = UUID.randomUUID().toString();
        desc.image = "name";
        if (links != null) {
            desc.links = links;
        }
        desc = doPost(desc, ContainerDescriptionService.FACTORY_LINK);
        assertNotNull(desc);
        addForDeletion(desc);
        return desc;
    }

}
