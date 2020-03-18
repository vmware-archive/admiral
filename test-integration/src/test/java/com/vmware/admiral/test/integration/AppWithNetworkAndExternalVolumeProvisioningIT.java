/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration;

import static com.vmware.admiral.common.util.UriUtilsExtended.MEDIA_TYPE_APPLICATION_YAML;
import static com.vmware.admiral.request.ReservationAllocationTaskService.CONTAINER_HOST_ID_CUSTOM_PROPERTY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.common.test.CommonTestStateFactory;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionFactoryService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.content.CompositeDescriptionContentService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;

/**
 * Test that provisioning of an application with a network and an external volume will succeed.
 */
public class AppWithNetworkAndExternalVolumeProvisioningIT extends BaseProvisioningOnCoreOsIT {
    private static final int TEMPLATE_NUMBER_OF_COMPONENTS = 3;
    private static final String TEMPLATE_CONTAINER_NAME = "some-alpine";
    private static final String TEMPLATE_NETWORK_NAME = "some-network";
    private static final String TEMPLATE_EXTERNAL_VOLUME_NAME = "some-volume";

    private static final int NUMBER_OF_NETWORKS_PER_APPLICATION = 1;

    private String compositeDescriptionLink;

    private static final String TEMPLATE_FILENAME = "Alpine_Network_Extenral_Volume.yaml";

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Test
    public void testProvisionApplication() throws Exception {
        setupCoreOsHost(DockerAdapterType.API, false, null);
        checkNumberOfNetworks(serviceClient, NUMBER_OF_NETWORKS_PER_APPLICATION);

        ContainerVolumeState volume = setupExternalVolume();
        compositeDescriptionLink = importTemplateWithExternalVolume(serviceClient,
                TEMPLATE_FILENAME, volume.name);

        logger.info(
                "---------- 5. Request simple application with a container, a network and an external volume. --------");
        requestContainerAndDelete(getResourceDescriptionLink(false, null));
    }

    @Override
    protected String getResourceDescriptionLink(boolean downloadImage,
            RegistryType registryType) throws Exception {
        return compositeDescriptionLink;
    }

    @Override
    protected void validateAfterStart(String resourceDescLink, RequestBrokerState request)
            throws Exception {

        assertEquals("Unexpected number of resource links", 1,
                request.resourceLinks.size());

        CompositeComponent cc = getDocument(request.resourceLinks.iterator().next(),
                CompositeComponent.class);
        assertEquals("Unexpected number of component links", TEMPLATE_NUMBER_OF_COMPONENTS,
                cc.componentLinks.size());

        logger.info("----------Assert network --------");
        String networkLink = getResourceContaining(cc.componentLinks, TEMPLATE_NETWORK_NAME);
        if (networkLink == null) {
            logger.warning("Cannot find network with name: %s in list components: %s",
                    TEMPLATE_NETWORK_NAME, cc.componentLinks);
        }
        ContainerNetworkState networkState = getDocument(networkLink, ContainerNetworkState.class);
        assertNotNull(networkState);
        assertNotNull(networkState.connectedContainersCount);
        assertEquals(1, networkState.connectedContainersCount.intValue());

        logger.info("----------Assert container --------");
        String sineAlpine = getResourceContaining(cc.componentLinks, TEMPLATE_CONTAINER_NAME);
        assertNotNull(TEMPLATE_CONTAINER_NAME + " not found.", sineAlpine);
        ContainerState containerState = getDocument(sineAlpine, ContainerState.class);
        assertNotNull(TEMPLATE_CONTAINER_NAME + " container state not found.", containerState);

        logger.info("----------Assert volume --------");
        String someVolume = getResourceContaining(cc.componentLinks, TEMPLATE_EXTERNAL_VOLUME_NAME);
        assertNotNull(TEMPLATE_EXTERNAL_VOLUME_NAME + " not found.", someVolume);
        ContainerVolumeState volumeState = getDocument(someVolume, ContainerVolumeState.class);
        assertNotNull(TEMPLATE_EXTERNAL_VOLUME_NAME + " state not found.", volumeState);
    }

    private ContainerVolumeState setupExternalVolume() throws Exception {
        logger.info("Setting up external volume...");

        ContainerVolumeDescription description = new ContainerVolumeDescription();
        description.documentSelfLink = UUID.randomUUID().toString();
        description.name = TEMPLATE_EXTERNAL_VOLUME_NAME;
        description.driver = "local";
        description.tenantLinks = TENANT_LINKS;
        description.customProperties = new HashMap<>();
        description.customProperties.put(CONTAINER_HOST_ID_CUSTOM_PROPERTY,
                Service.getId(getDockerHost().documentSelfLink));

        description = postDocument(ContainerVolumeDescriptionFactoryService.SELF_LINK, description);

        RequestBrokerState request = requestExternalEntity(description.documentSelfLink);

        ContainerVolumeState externalVolume = getDocument(request.resourceLinks.iterator().next(),
                ContainerVolumeState.class);
        assertNotNull(externalVolume);
        logger.info("External volume created.");
        return externalVolume;
    }

    private String importTemplateWithExternalVolume(ServiceClient serviceClient, String filePath,
            String volumeName) throws Exception {
        String template = CommonTestStateFactory.getFileContent(filePath);

        template = template.replaceAll(TEMPLATE_EXTERNAL_VOLUME_NAME, volumeName);

        URI uri = URI.create(
                getBaseUrl() + buildServiceUri(CompositeDescriptionContentService.SELF_LINK));

        Operation op = sendRequest(serviceClient, Operation.createPost(uri)
                .setContentType(MEDIA_TYPE_APPLICATION_YAML)
                .setBody(template));

        String location = op.getResponseHeader(Operation.LOCATION_HEADER);
        assertNotNull("Missing location header", location);

        logger.info("Successfully imported: %s", template);

        return URI.create(location).getPath();
    }

}
