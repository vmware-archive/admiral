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

package com.vmware.admiral.compute.container;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;

public class ContainerShellServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(ConfigurationFactoryService.SELF_LINK);

        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.ALLOW_SSH_CONSOLE_PROPERTY;
        config.value = "true";
        config.documentSelfLink = config.key;
        doPost(config, ConfigurationFactoryService.SELF_LINK);
    }

    @Test
    public void testGetShellWhenEmbeddedShouldFail() throws Throwable {

        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.EMBEDDED_MODE_PROPERTY;
        config.value = "true";
        config.documentSelfLink = config.key;
        doPost(config, ConfigurationFactoryService.SELF_LINK);

        try {
            getDocument(String.class, ContainerShellService.SELF_LINK);
            fail("It should have been forbidden!");
        } catch (IllegalAccessError e) {
            assertEquals("forbidden", e.getMessage());
        }
    }

    @Test
    public void testGetShellWhenVicShouldFail() throws Throwable {

        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.VIC_MODE_PROPERTY;
        config.value = "true";
        config.documentSelfLink = config.key;
        doPost(config, ConfigurationFactoryService.SELF_LINK);

        try {
            getDocument(String.class, ContainerShellService.SELF_LINK);
            fail("It should have been forbidden!");
        } catch (IllegalAccessError e) {
            assertEquals("forbidden", e.getMessage());
        }
    }

    @Test
    public void testGetShellWhenWhenShellDisabledShouldFail() throws Throwable {

        ConfigurationState config = new ConfigurationState();
        config.key = ConfigurationUtil.ALLOW_SSH_CONSOLE_PROPERTY;
        config.value = "false";
        config.documentSelfLink = config.key;
        doPost(config, ConfigurationFactoryService.SELF_LINK);

        try {
            getDocument(String.class, ContainerShellService.SELF_LINK);
            fail("It should have been forbidden!");
        } catch (IllegalAccessError e) {
            assertEquals("forbidden", e.getMessage());
        }
    }

    @Test
    public void testGetShellNoIdShouldFail() throws Throwable {
        try {
            getDocument(String.class, ContainerShellService.SELF_LINK);
            fail("It should have failed!");
        } catch (LocalizableValidationException e) {
            assertEquals("Container id is required.", e.getMessage());
        }
    }

    @Test
    public void testGetShellInvalidIdShouldFail() throws Throwable {
        try {
            getDocument(String.class, ContainerShellService.SELF_LINK + "?id=invalid");
            fail("It should have failed!");
        } catch (ServiceNotFoundException e) {
            assertTrue(e.getMessage().endsWith("/resources/containers/invalid"));
        }
    }

    @Test
    public void testGetShellNoHostShouldFail() throws Throwable {

        waitForServiceAvailability(ContainerShellService.SELF_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        ContainerState containerState = new ContainerState();
        containerState.image = "test-image";
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);

        try {
            getDocument(String.class, ContainerShellService.SELF_LINK + "?id="
                    + UriUtils.getLastPathSegment(containerState.documentSelfLink));
            fail("It should have failed!");
        } catch (ServiceNotFoundException e) {
            assertEquals("Service not found: " + host.getPublicUriAsString(), e.getMessage());
        }
    }

    @Test
    public void testGetShellNoAgentShouldFail() throws Throwable {

        waitForServiceAvailability(ContainerShellService.SELF_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        ComputeState hostState = new ComputeState();
        hostState.address = host.getPreferredAddress();
        hostState.descriptionLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                "test-host");
        hostState = doPost(hostState, ComputeService.FACTORY_LINK);

        ContainerState containerState = new ContainerState();
        containerState.image = "test-image";
        containerState.parentLink = hostState.documentSelfLink;
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);

        try {
            getDocument(String.class, ContainerShellService.SELF_LINK + "?id="
                    + UriUtils.getLastPathSegment(containerState.documentSelfLink));
            fail("It should have failed!");
        } catch (ServiceNotFoundException e) {
            assertTrue(e.getMessage().contains("/resources/containers/admiral_agent__"));
        }
    }

    @Test
    public void testGetShellInvalidAgentPortShouldFail() throws Throwable {

        waitForServiceAvailability(ContainerShellService.SELF_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        ComputeState hostState = new ComputeState();
        hostState.address = host.getPreferredAddress();
        hostState.descriptionLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                "test-host");
        hostState = doPost(hostState, ComputeService.FACTORY_LINK);

        ContainerState containerState = new ContainerState();
        containerState.image = "test-image";
        containerState.parentLink = hostState.documentSelfLink;
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);

        ContainerState agentState = new ContainerState();
        agentState.image = "test-agent";
        agentState.documentSelfLink = ContainerFactoryService.SELF_LINK + "/admiral_agent__"
                + UriUtils.getLastPathSegment(hostState.documentSelfLink);
        agentState.parentLink = hostState.documentSelfLink;
        agentState = doPost(agentState, ContainerFactoryService.SELF_LINK);

        try {
            getDocument(String.class, ContainerShellService.SELF_LINK + "?id="
                    + UriUtils.getLastPathSegment(containerState.documentSelfLink));
            fail("It should have failed!");
        } catch (LocalizableValidationException e) {
            assertEquals("Could not locate shell port", e.getMessage());
        }
    }

    @Test
    public void testGetShell() throws Throwable {

        waitForServiceAvailability(ContainerShellService.SELF_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(ContainerDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(CompositeDescriptionFactoryService.SELF_LINK);
        waitForServiceAvailability(ContainerFactoryService.SELF_LINK);

        ComputeState hostState = new ComputeState();
        hostState.address = host.getPreferredAddress();
        hostState.descriptionLink = UriUtils.buildUriPath(ComputeService.FACTORY_LINK,
                "test-host");
        hostState = doPost(hostState, ComputeService.FACTORY_LINK);

        ContainerState containerState = new ContainerState();
        containerState.image = "test-image";
        containerState.parentLink = hostState.documentSelfLink;
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);

        ContainerState agentState = new ContainerState();
        agentState.image = "test-agent";
        agentState.documentSelfLink = ContainerFactoryService.SELF_LINK + "/admiral_agent__"
                + UriUtils.getLastPathSegment(hostState.documentSelfLink);
        agentState.parentLink = hostState.documentSelfLink;
        PortBinding binding = new PortBinding();
        binding.containerPort = SystemContainerDescriptions.CORE_AGENT_SHELL_PORT;
        binding.hostIp = "127.0.0.1";
        binding.hostPort = "1025";
        agentState.ports = Arrays.asList(binding);

        agentState = doPost(agentState, ContainerFactoryService.SELF_LINK);

        String document = getDocument(String.class, ContainerShellService.SELF_LINK + "?id="
                + UriUtils.getLastPathSegment(containerState.documentSelfLink));

        assertNotNull(document);
        assertTrue(document.startsWith("/rp/"));
    }

}
