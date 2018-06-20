/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.ContainerDescriptionFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionFactoryService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeFactoryService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class AdmiralUpgradeWithoutMigrationIT extends AdmiralUpgradeBaseIT {

    private static final String ADMIRAL_STORAGE_LOCATION = "/var/admiral";

    private String oldAdmiralImageAndTag = TestPropertiesUtil
            .getTestRequiredProp("test.upgrade.admiral.image-and-tag.old");
    // TODO change 'master' to 'upgraded' in prop name. Also update the integration.properties file
    // and the pipeline
    private String upgradedAdmiralImageAndTag = TestPropertiesUtil
            .getTestRequiredProp("test.upgrade.admiral.image-and-tag.master");

    private String oldAdmiralDescriptionLink;
    private String upgradedAdmiralDescriptionLink;

    private ContainerVolumeState storageVolume;
    private ContainerState oldAdmiral;
    private ContainerState upgradedAdmiral;

    @Before
    public void setUp() throws Throwable {
        logger.info("---------- Add a Docker host. --------");
        setupCoreOsHost(DockerAdapterType.API);

        logger.info("---------- Prepare volumes and descriptions. --------");
        logger.info("----- Prepare storage volume -----");
        storageVolume = createAndGetContainerVolume("admiral-storage");

        logger.info("----- Prepare old Admiral description for image %s and volume %s -----",
                oldAdmiralImageAndTag, storageVolume.name);
        oldAdmiralDescriptionLink = createAndGetAdmiralContainerDescription("old-admiral",
                oldAdmiralImageAndTag, storageVolume.name).documentSelfLink;

        logger.info("----- Prepare upgraded Admiral description for image %s and volume %s -----",
                upgradedAdmiralImageAndTag, storageVolume.name);
        upgradedAdmiralDescriptionLink = createAndGetAdmiralContainerDescription("upgraded-admiral",
                upgradedAdmiralImageAndTag, storageVolume.name).documentSelfLink;
    }

    @After
    public void cleanUp() {
        // more specialized cleanup is done here that cannot be automatically
        // handled by the superclass automatic cleanup implementation

        cleanupResourcesOnAdmiralInstance();

        Stream.of(upgradedAdmiral, oldAdmiral, storageVolume)
                .filter(Objects::nonNull)
                .map(rs -> rs.documentSelfLink)
                .filter(Objects::nonNull)
                .forEach(this::cleanupResourceIgnoreErrors);
    }

    @Test
    public void testUpgradeWithoutMigration() throws Throwable {
        logger.info("---------- Test upgrade with old storage (no migration). --------");
        logger.info("----- Provision old Admiral. -----");
        oldAdmiral = provisionAdmiralContainer(oldAdmiralDescriptionLink);
        logger.info("----- Add content to old Admiral. -----");
        waitForSuccessfulHealthcheck(oldAdmiral);
        addContentToTheProvisionedAdmiral(oldAdmiral);
        logger.info("----- Validate the content on the old Admiral. -----");
        validateContent(oldAdmiral);
        logger.info("----- Stop the old Admiral container. -----");
        stopContainer(oldAdmiral.documentSelfLink);

        logger.info("----- Provision upgraded Admiral with the same storage. -----");
        upgradedAdmiral = provisionAdmiralContainer(upgradedAdmiralDescriptionLink);
        logger.info("----- Validate the content on the upgraded Admiral. -----");
        validateContent(upgradedAdmiral);
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        // not used by the test, any value will do
        return null;
    }

    private ContainerVolumeState createAndGetContainerVolume(String name) throws Throwable {
        ContainerVolumeDescription description = new ContainerVolumeDescription();
        description.name = name;
        description.driver = "local";
        description.external = false;
        description.customProperties = new HashMap<>();
        description.customProperties.put("__containerHostId",
                Service.getId(getDockerHost().documentSelfLink));
        description.instanceAdapterReference = UriUtils
                .buildUri(ManagementUriParts.ADAPTER_DOCKER_VOLUME);
        description = postDocument(ContainerVolumeDescriptionFactoryService.SELF_LINK, description);
        cleanUpAfter(description);

        RequestBrokerState request = requestVolume(description.documentSelfLink);
        String volumeStateSelfLink = request.resourceLinks.iterator().next();

        return getDocument(volumeStateSelfLink, ContainerVolumeState.class);
    }

    private ContainerDescription createAndGetAdmiralContainerDescription(String containerName,
            String imageAndTag, String volumeName) throws Exception {
        ContainerDescription description = new ContainerDescription();
        description.name = containerName;
        description.image = imageAndTag;

        // attach the Admiral storage volume
        description.volumes = new String[] {
                buildVolumeMapping(volumeName, ADMIRAL_STORAGE_LOCATION) };

        // publish the default port
        PortBinding portBinding = new PortBinding();
        portBinding.containerPort = "8282";
        description.portBindings = new PortBinding[] { portBinding };

        // honor existing local images
        description.customProperties = new HashMap<>();
        description.customProperties.put(
                DockerAdapterCommandExecutor.DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY,
                Boolean.TRUE.toString());

        description = postDocument(ContainerDescriptionFactoryService.SELF_LINK, description);
        cleanUpAfter(description);
        return description;
    }

    private String buildVolumeMapping(String volumeName, String containerPath) {
        return String.format("%s:%s", volumeName, containerPath);
    }

    private ContainerState provisionAdmiralContainer(String admiralDescriptionLink)
            throws Throwable {
        RequestBrokerState request = requestContainer(admiralDescriptionLink);
        String containerLink = request.resourceLinks.iterator().next();
        waitForStatusCode(URI.create(getBaseUrl() + containerLink), Operation.STATUS_CODE_OK);
        return getDocument(containerLink, ContainerState.class);
    }

    private void cleanupResourcesOnAdmiralInstance() {

        ContainerState admiralInstance = upgradedAdmiral != null ? upgradedAdmiral : oldAdmiral;

        if (admiralInstance != null) {
            logger.info("----- Cleanup data on the Admiral instance. -----");
            try {
                removeData(admiralInstance);
            } catch (Throwable e) {
                logger.error(
                        "Unexpected error was thrown while cleaning up resources on the Admiral instance: %s",
                        Utils.toString(e));
            }

            logger.info("----- Stop the Admiral container. -----");
            try {
                stopContainer(admiralInstance.documentSelfLink);
            } catch (Throwable e) {
                logger.error("Unexpected error was thrown while stopping the Admiral container: %s",
                        Utils.toString(e));
            }
        }
    }

    private void cleanupResourceIgnoreErrors(String documentLink) {
        setBaseURI(null);
        try {
            if (documentLink.startsWith(ContainerVolumeFactoryService.SELF_LINK)) {
                requestVolumeDelete(Collections.singleton(documentLink), true);
            } else {
                requestContainerDelete(Collections.singleton(documentLink), true);
            }
        } catch (Throwable e) {
            logger.error("Unexpected error was thrown while cleaning up resource [%s]: %s",
                    documentLink, Utils.toString(e));
        }
    }

}
