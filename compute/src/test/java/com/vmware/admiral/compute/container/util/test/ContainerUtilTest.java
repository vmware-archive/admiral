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

package com.vmware.admiral.compute.container.util.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

import org.junit.Test;

import com.vmware.admiral.compute.container.ComputeBaseTest;

import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.container.util.ContainerUtil;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.UriUtils;

public class ContainerUtilTest extends ComputeBaseTest {

    private static final String TENANT_LINKS = "/tenants/coke";
    private static final String CONTAINER_COMMAND = "/bin/sh";
    private static final String HOSTNAME = "test-docker.eng.vmware.com";
    private static final String DOMAIN_NAME = "eng.vmware.com";
    private static final String USERNAME = "test-docker-user";
    private static final String WORKING_DIR = "/etc/docker";

    @Test
    public void testCreateContainerDesc() throws Throwable {

        ContainerState containerState = createContainerState();
        ContainerDescription containerDesc = ContainerUtil
                .createContainerDescription(containerState);
        assertEquals(containerState.descriptionLink, containerDesc.documentSelfLink);
        assertEquals(containerDesc.documentDescription, containerState.documentDescription);
        assertEquals(containerDesc.tenantLinks, containerState.tenantLinks);
        assertEquals(containerDesc.image, containerState.image);
        assertEquals(containerDesc.cpuShares, containerState.cpuShares);
        assertEquals(containerDesc.instanceAdapterReference,
                containerState.adapterManagementReference);
        assertEquals(containerDesc.customProperties, containerState.customProperties);
        assertEquals(containerDesc.parentDescriptionLink, containerState.parentLink);
        assertTrue(Arrays.equals(containerDesc.env, containerState.env));
        assertTrue(Arrays.equals(containerDesc.command, containerState.command));
        assertEquals(containerState.extraHosts[0], containerDesc.extraHosts[0]);
        assertEquals(containerState.ports.get(0), containerDesc.portBindings[0]);

    }

    @Test
    public void testUpdateDiscoverContainerDescription() throws Throwable {
        ContainerState containerState = createContainerState();
        ContainerDescription containerDesc = ContainerUtil
                .createContainerDescription(containerState);
        containerDesc = doPost(containerDesc, ContainerDescriptionService.FACTORY_LINK);

        final String containerDescrLink = containerDesc.documentSelfLink;

        ContainerState patch = new ContainerState();
        String configValue = String.format(
                "{\"Hostname\":\"%s\", \"Domainname\":\"%s\", \"User\":\"%s\", \"WorkingDir\":\"%s\"}",
                HOSTNAME, DOMAIN_NAME, USERNAME, WORKING_DIR);

        patch.attributes = new HashMap<String, String>();

        patch.attributes.put("Config", configValue);

        doOperation(patch, UriUtils.buildUri(host, containerState.documentSelfLink),
                false, Action.PATCH);

        waitFor(() -> {

            ContainerDescription document = getDocument(ContainerDescription.class,
                    containerDescrLink);

            return document.hostname != null && document.hostname.equals(HOSTNAME);
        });

        ContainerDescription document = getDocument(ContainerDescription.class,
                containerDescrLink);
        assertEquals(document.hostname, HOSTNAME);
        assertEquals(document.domainName, DOMAIN_NAME);
        assertEquals(document.user, USERNAME);
        assertEquals(document.workingDir, WORKING_DIR);

    }

    private ContainerState createContainerState() throws Throwable {

        ContainerState containerState = new ContainerState();
        containerState.descriptionLink = UriUtils
                .buildUriPath(
                        SystemContainerDescriptions.DISCOVERED_DESCRIPTION_LINK,
                        UUID.randomUUID().toString());
        containerState.image = "test-image";
        containerState.tenantLinks = Collections.singletonList(TENANT_LINKS);
        containerState.command = new String[] { CONTAINER_COMMAND };
        containerState.id = UUID.randomUUID().toString();
        containerState.names = new ArrayList<>(Arrays.asList("name_" + containerState.id));
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);
        PortBinding[] ports = new PortBinding[1];
        PortBinding port = new PortBinding();
        port.containerPort = "8263";
        ports[0] = port;
        containerState.ports = Arrays.asList(ports);
        containerState.extraHosts = new String[] { "extra-docker.host" };
        return containerState;

    }

}
