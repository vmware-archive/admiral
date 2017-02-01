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

package com.vmware.admiral.test.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.HostContainerListDataCollectionFactoryService;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.NodeHealthCheckService;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.admiral.test.integration.data.IntegratonTestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public class AdmiralUpgradeIT extends BaseProvisioningOnCoreOsIT {
    private static final String TEMPLATE_FILE_BRANCH = "Admiral_0.9.1_release.yaml";
    private static final String TEMPLATE_FILE_MASTER = "Admiral_master.yaml";
    private static final String ADMIRAL_NAME = "admiral";

    private static final String SEPARATOR = "/";

    private static final String UPGRADE_SKIP_INITIALIZE = "upgrade.skip.initialize";
    private static final String UPGRADE_SKIP_VALIDATE = "upgrade.skip.validate";

    private static final String CREDENTIALS_SELF_LINK = AuthCredentialsService.FACTORY_LINK
            + SEPARATOR +
            IntegratonTestStateFactory.AUTH_CREDENTIALS_ID;
    private static final String COMPUTE_SELF_LINK = ComputeService.FACTORY_LINK + SEPARATOR
            + IntegratonTestStateFactory.DOCKER_COMPUTE_ID;

    private static ServiceClient serviceClient;

    private String compositeDescriptionLink;
    private boolean dataInitialized;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        serviceClient.stop();
    }

    @Before
    public void setUp() throws Exception {
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE_BRANCH);
    }

    @After
    public void cleanUp() {
        setBaseURI(null);
    }

    @Test
    public void testProvision() throws Exception {
        String skipInitialize = getSystemOrTestProp(UPGRADE_SKIP_INITIALIZE, "false");
        if (skipInitialize.equals(Boolean.FALSE.toString())) {
            doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
        }

        String skipValidate = getSystemOrTestProp(UPGRADE_SKIP_VALIDATE, "false");
        if (skipValidate.equals(Boolean.FALSE.toString())) {
            dataInitialized = true;
            compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE_MASTER);
            doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
        }
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

        String admiralContainerLink = cc.componentLinks.stream()
                .filter((l) -> l.contains(ADMIRAL_NAME))
                .collect(Collectors.toList()).get(0);
        waitForStatusCode(URI.create(getBaseUrl() + admiralContainerLink),
                Operation.STATUS_CODE_OK);
        ContainerState admiralContainer = getDocument(admiralContainerLink, ContainerState.class);
        if (!dataInitialized) {
            addContentToTheProvisionedAdmiral(admiralContainer);
            shutDownAdmiralContainer(admiralContainer);
        } else {
            validateContent(admiralContainer);
            removeData(admiralContainer);
        }
    }

    private void removeData(ContainerState admiralContainer) throws Exception {
        changeBaseURI(admiralContainer);
        delete(COMPUTE_SELF_LINK);
        delete(CREDENTIALS_SELF_LINK);
        setBaseURI(null);
    }

    private void validateContent(ContainerState admiralContainer) throws Exception {
        changeBaseURI(admiralContainer);
        // wait for the admiral container to start
        URI uri = URI.create(getBaseUrl() + NodeHealthCheckService.SELF_LINK);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINERS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINER_HOSTS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);

        waitForStateChange(COMPUTE_SELF_LINK, (body) -> {
            if (body == null) {
                return false;
            }
            ComputeState dockerHost = Utils.fromJson(body, ComputeState.class);
            // trust alias custom property must be set eventually after upgrade
            return (ContainerHostUtil.getTrustAlias(dockerHost) != null);
        });
        ComputeState dockerHost = getDocument(
                COMPUTE_SELF_LINK,
                ComputeState.class);
        assertTrue(dockerHost != null);

        AuthCredentialsServiceState credentials = getDocument(
                CREDENTIALS_SELF_LINK,
                AuthCredentialsServiceState.class);
        assertTrue(credentials != null);

        // get all the containers with expand - validate xenon issue:
        // https://www.pivotaltracker.com/n/projects/1471320/stories/137898729
        HttpResponse response = SimpleHttpsClient.execute(HttpMethod.GET,
                getBaseUrl() + buildServiceUri("/resources/containers?expand"));
        assertTrue(response.statusCode == 200);

        // There are at least 2 containers availabale the provisioned admiral and the agent
        JsonElement json = new JsonParser().parse(response.responseBody);
        JsonObject jsonObject = json.getAsJsonObject();
        JsonArray documentLinks = jsonObject.getAsJsonArray("documentLinks");
        for (int i = 0; i < documentLinks.size(); i++) {
            String selfLink = documentLinks.get(i).getAsString();
            ContainerState state = getDocument(selfLink, ContainerState.class);
            assertTrue(state != null);
        }

        setBaseURI(null);
    }

    private void changeBaseURI(ContainerState admiralContainer) throws Exception {
        String parent = admiralContainer.parentLink;
        ComputeState computeState = getDocument(parent, ComputeState.class);
        setBaseURI(String.format("http://%s:%s", computeState.address,
                admiralContainer.ports.get(0).hostPort));
    }

    private void addContentToTheProvisionedAdmiral(ContainerState admiralContainer)
            throws Exception {
        changeBaseURI(admiralContainer);
        // wait for the admiral container to start. In 0.9.1 health check service is not available
        Thread.sleep(20000);
        URI uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINERS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINER_HOSTS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);

        // create documents to check for after upgrade
        setupCoreOsHost(DockerAdapterType.API, false);
        // credentials
        AuthCredentialsServiceState credentials = IntegratonTestStateFactory
                .createAuthCredentials(false);
        postDocument(AuthCredentialsService.FACTORY_LINK, credentials);

        dataInitialized = true;
    }

    private void shutDownAdmiralContainer(ContainerState admiralContainer) throws Exception {

        // send stop request to the admiral container before deleting it
        delete("/core/management");
        setBaseURI(null);

        ContainerListCallback dataCollectionBody = new ContainerListCallback();
        dataCollectionBody.containerHostLink = COMPUTE_SELF_LINK;
        AtomicInteger counter = new AtomicInteger();
        waitForStateChange(
                admiralContainer.documentSelfLink,
                t -> {
                    ContainerState container = Utils.fromJson(t,
                            ContainerState.class);
                    if (counter.getAndIncrement() == 10) {
                        logger.warning(
                                "Container power state was not changed to STOPPED after 10 second");
                        return true;
                    }
                    try {
                        sendRequest(HttpMethod.PATCH,
                                HostContainerListDataCollectionFactoryService.DEFAULT_HOST_CONTAINER_LIST_DATA_COLLECTION_LINK,
                                Utils.toJson(dataCollectionBody));
                    } catch (Exception e) {
                        logger.error(String.format("Unable to trigger data collection: %s",
                                e.getMessage()));
                    }
                    return container.powerState.equals(PowerState.STOPPED);
                });
    }
}
