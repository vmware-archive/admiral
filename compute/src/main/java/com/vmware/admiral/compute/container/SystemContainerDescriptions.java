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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.image.service.SystemImagesService;
import com.vmware.xenon.common.UriUtils;

public class SystemContainerDescriptions {
    /*Instance to link to when existing containers are discovered on a host */
    public static final String DISCOVERED_INSTANCE = "discovered";
    public static final String DISCOVERED_DESCRIPTION_LINK = UriUtils.buildUriPath(
            ContainerDescriptionService.FACTORY_LINK, DISCOVERED_INSTANCE);

    public static final String AGENT_CONTAINER_DESCRIPTION_ID = "admiral_agent";
    public static final String AGENT_CONTAINER_DESCRIPTION_LINK = UriUtilsExtended
            .buildUriPath(ContainerDescriptionService.FACTORY_LINK,
                    AGENT_CONTAINER_DESCRIPTION_ID);
    public static final String AGENT_CONTAINER_NAME = AGENT_CONTAINER_DESCRIPTION_ID;
    private static final String AGENT_CONTAINER_ID_SEPARATOR = "__";
    public static final String CORE_AGENT_SHELL_PORT = System.getProperty(
            "dcp.management.host.container.agent.shell.port", "4200");
    //The image name is referenced in agents/core/Makefile
    public static final String AGENT_IMAGE_NAME = System.getProperty(
            "dcp.management.images.agent.name", "vmware/admiral_agent");
    public static final String AGENT_IMAGE_TAR_FILENAME = "admiral_agent";
    public static final String AGENT_IMAGE_VERSION = System.getProperty(
            "dcp.management.images.agent.version", "0.5.0");
    public static final String AGENT_IMAGE_REFERENCE = System.getProperty(
            "dcp.management.images.agent.reference",
            SystemImagesService.buildSystemImageUriPath(AGENT_IMAGE_TAR_FILENAME + ".tar"));

    /** Create a container description to be used for installing host agents containers. */
    public static ContainerDescription buildCoreAgentContainerDescription(URI baseImageUri) {
        ContainerDescription cd = new ContainerDescription();
        cd.documentSelfLink = AGENT_CONTAINER_DESCRIPTION_LINK;
        cd.name = AGENT_CONTAINER_NAME;
        cd.image = String.format("%s:%s", AGENT_IMAGE_NAME, AGENT_IMAGE_VERSION);
        if (AGENT_IMAGE_REFERENCE != null && !AGENT_IMAGE_REFERENCE.isEmpty()) {
            // download image from a reference URI if the image doesn't have registry host as a prefix
            cd.imageReference = UriUtils.buildUri(baseImageUri, AGENT_IMAGE_REFERENCE);
        }

        cd.volumes = new String[] { "/var/run/docker.sock:/var/run/docker.sock",
                "/etc/docker:/etc/docker" };
        cd.restartPolicy = "always";
        cd.networkMode = "host";

        return cd;
    }

    public static boolean isDiscoveredContainer(ContainerState containerState) {
        // container is discovered if its description link is discovered, or it is a
        // system container without groupResourcePolicyLink
        return (containerState.descriptionLink != null && containerState.descriptionLink
                .startsWith(DISCOVERED_DESCRIPTION_LINK))
                || (Boolean.TRUE.equals(containerState.system)
                && (containerState.groupResourcePolicyLink == null
                || containerState.groupResourcePolicyLink.isEmpty()));
    }

    public static String getSystemContainerSelfLink(String systemContainerName, String hostId) {
        return UriUtils.buildUriPath(ContainerFactoryService.SELF_LINK, systemContainerName
                + AGENT_CONTAINER_ID_SEPARATOR + ContainerHostUtil.extractHostId(hostId));
    }

    public static List<String> getSystemContainerNames() {
        List<String> result = new ArrayList<>();
        result.add(AGENT_CONTAINER_NAME);
        return result;
    }
}
