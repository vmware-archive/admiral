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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.internal.ComparisonCriteria;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.ComputeBaseTest;

import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerFactoryService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.container.util.ContainerUtil;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
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

        ContainerState containerState = createContainerState(null, true);
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
        ContainerState containerState = createContainerState(null, true);
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

    @Test
    public void testUpdateContainerPorts() throws Throwable {
        ComputeService.ComputeState computeState = createComputeHost();
        ContainerState containerState = createContainerState(computeState.documentSelfLink, true);
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile(computeState,
                containerState, new Long[] {new Long(5000), new Long(3045)});
        final String profileDescrLink = profile.documentSelfLink;

        ContainerState patch = new ContainerState();
        patch.ports = Arrays.stream(new String[] {
                "5000:5000",
                "20080:80" })
                .map((s) -> PortBinding.fromDockerPortMapping(DockerPortMapping.fromString(s)))
                .collect(Collectors.toList());
        String configValue = String.format(
                "{\"Hostname\":\"%s\", \"Domainname\":\"%s\", \"User\":\"%s\", \"WorkingDir\":\"%s\"}",
                HOSTNAME, DOMAIN_NAME, USERNAME, WORKING_DIR);

        patch.attributes = new HashMap<String, String>();

        patch.attributes.put("Config", configValue);

        doOperation(patch, UriUtils.buildUri(host, containerState.documentSelfLink),
                false, Action.PATCH);

        waitFor(() -> {

            HostPortProfileService.HostPortProfileState document = getDocument(
                    HostPortProfileService.HostPortProfileState.class, profileDescrLink);

            return document.reservedPorts.containsKey(new Long(20080));
        });

        Map<Long, String> actualPorts = new HashMap<>();
        actualPorts.put(new Long(20080), containerState.documentSelfLink);
        actualPorts.put(new Long(5000), containerState.documentSelfLink);

        HostPortProfileService.HostPortProfileState document = getDocument(
                HostPortProfileService.HostPortProfileState.class, profileDescrLink);
        new ComparisonCriteria() {
            @Override
            protected void assertElementsEqual(Object expected, Object actual) {
                Map.Entry<Long, String> expectedMapping = (Map.Entry<Long, String>) expected;
                Map.Entry<Long, String> actualMapping = (Map.Entry<Long, String>) actual;

                assertEquals("port", expectedMapping.getKey(), actualMapping.getKey());
                assertEquals("containerLink", expectedMapping.getValue(), actualMapping.getValue());
            }
        }.arrayEquals(null, document.reservedPorts.entrySet(), actualPorts.entrySet());
    }

    @Test
    public void testReleaseRetiredContainerPorts() throws Throwable {
        ComputeService.ComputeState computeState = createComputeHost();
        ContainerState containerState = createContainerState(computeState.documentSelfLink, false);
        doPut(containerState);
        HostPortProfileService.HostPortProfileState profile = createHostPortProfile(computeState,
                containerState, new Long[] {new Long(5000), new Long(3045)});
        final String profileDescrLink = profile.documentSelfLink;

        ContainerState patch = new ContainerState();
        patch.powerState = ContainerState.PowerState.RETIRED;
        String configValue = String.format(
                "{\"Hostname\":\"%s\", \"Domainname\":\"%s\", \"User\":\"%s\", \"WorkingDir\":\"%s\"}",
                HOSTNAME, DOMAIN_NAME, USERNAME, WORKING_DIR);

        patch.attributes = new HashMap<String, String>();

        patch.attributes.put("Config", configValue);

        doOperation(patch, UriUtils.buildUri(host, containerState.documentSelfLink),
                false, Action.PATCH);

        waitFor(() -> {

            HostPortProfileService.HostPortProfileState document = getDocument(
                    HostPortProfileService.HostPortProfileState.class, profileDescrLink);

            return document.reservedPorts.isEmpty();
        });
    }

    private ContainerState createContainerState(String parentLink, boolean isDiscovered) throws Throwable {

        ContainerState containerState = new ContainerState();
        containerState.descriptionLink = isDiscovered ? String.format("%s-%s",
                SystemContainerDescriptions.DISCOVERED_DESCRIPTION_LINK,
                UUID.randomUUID().toString()) : UUID.randomUUID().toString();
        containerState.image = "test-image";
        containerState.tenantLinks = Collections.singletonList(TENANT_LINKS);
        containerState.command = new String[] { CONTAINER_COMMAND };
        containerState.id = UUID.randomUUID().toString();
        containerState.names = new ArrayList<>(Arrays.asList("name_" + containerState.id));
        containerState.parentLink = parentLink;
        containerState = doPost(containerState, ContainerFactoryService.SELF_LINK);

        PortBinding[] ports = new PortBinding[1];
        PortBinding port = new PortBinding();
        port.containerPort = "8263";
        ports[0] = port;
        containerState.ports = Arrays.asList(ports);
        containerState.extraHosts = new String[] { "extra-docker.host" };
        return containerState;

    }

    ComputeService.ComputeState createComputeHost() throws Throwable {
        ComputeService.ComputeState cs = new ComputeService.ComputeState();
        cs.id = UUID.randomUUID().toString();
        cs.primaryMAC = UUID.randomUUID().toString();
        cs.address = "somehost";
        cs.powerState = ComputeService.PowerState.ON;
        cs.descriptionLink = UriUtils.buildUriPath(ComputeDescriptionService.FACTORY_LINK,
                "test-host-compute-desc-id");
        cs.resourcePoolLink = UriUtils.buildUriPath(ResourcePoolService.FACTORY_LINK,
                "test-host-resource-pool");
        cs.adapterManagementReference = URI.create("http://localhost:8081");
        cs.customProperties = new HashMap<>();
        cs.customProperties.put(ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME,
                "API");
        cs.customProperties.put(ComputeConstants.DOCKER_URI_PROP_NAME,
                ContainerDescription.getDockerHostUri(cs).toString());
        cs.documentSelfLink = cs.id;

        cs.tenantLinks = new ArrayList<>();
        return doPost(cs, ComputeService.FACTORY_LINK);
    }

    HostPortProfileService.HostPortProfileState createHostPortProfile(
            ComputeService.ComputeState computeHost, ContainerState containerState,
            Long[] allocatedPorts) throws Throwable {
        HostPortProfileService.HostPortProfileState hostPortProfileState =
                new HostPortProfileService.HostPortProfileState();
        hostPortProfileState.hostLink = computeHost.documentSelfLink;
        hostPortProfileState.id = computeHost.id;
        hostPortProfileState.documentSelfLink = hostPortProfileState.id;
        hostPortProfileState.reservedPorts = new HashMap<>();
        for (Long port : allocatedPorts) {
            hostPortProfileState.reservedPorts.put(port, containerState.documentSelfLink);
        }
        hostPortProfileState = doPost(hostPortProfileState, HostPortProfileService.FACTORY_LINK);
        return hostPortProfileState;
    }
}
