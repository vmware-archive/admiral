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

import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
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
import com.vmware.admiral.common.serialization.ReleaseConstants;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
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
import com.vmware.xenon.services.common.ServiceHostManagementService;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class AdmiralUpgradeIT extends BaseProvisioningOnCoreOsIT {
    private static final String DOCUMENT_LINKS = "documentLinks";
    private static final String TEMPLATE_FILE = "Admiral_master_and_0.9.1_release.yaml";
    private static final String TEMPLATE_HELLO_WORLD_NETWORK = "HelloWorld_network.yaml";
    private static final String ADMIRAL_BRANCH_NAME = "admiral-branch";
    private static final String ADMIRAL_MASTER_NAME = "admiral-master";

    private static final String SEPARATOR = "/";
    private static final String EXPAND = "?expand";

    private static final String UPGRADE_SKIP_INITIALIZE = "upgrade.skip.initialize";
    private static final String UPGRADE_SKIP_VALIDATE = "upgrade.skip.validate";

    private static final String CREDENTIALS_SELF_LINK = AuthCredentialsService.FACTORY_LINK
            + SEPARATOR +
            IntegratonTestStateFactory.AUTH_CREDENTIALS_ID;
    private static final String COMPUTE_SELF_LINK = ComputeService.FACTORY_LINK + SEPARATOR
            + IntegratonTestStateFactory.DOCKER_COMPUTE_ID;

    private static final Map<String, String> versionHeader = Collections
            .singletonMap(ReleaseConstants.VERSION_PREFIX, ReleaseConstants.API_VERSION_0_9_1);

    private ContainerState admiralBranchContainer;
    private ContainerState admiralMasterContainer;

    private static ServiceClient serviceClient;

    private String compositeDescriptionLink;

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
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_FILE);
    }

    @After
    public void cleanUp() {
        setBaseURI(null);
    }

    @Test
    public void testUpgradeCompatibility() throws Exception {
        doProvisionDockerContainerOnCoreOS(false, DockerAdapterType.API);
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

        String admiralBranchContainerLink = cc.componentLinks.stream()
                .filter((l) -> l.contains(ADMIRAL_BRANCH_NAME))
                .collect(Collectors.toList()).get(0);
        waitForStatusCode(URI.create(getBaseUrl() + admiralBranchContainerLink),
                Operation.STATUS_CODE_OK);
        admiralBranchContainer = getDocument(admiralBranchContainerLink, ContainerState.class);

        String admiralMasterContainerLink = cc.componentLinks.stream()
                .filter((l) -> l.contains(ADMIRAL_MASTER_NAME))
                .collect(Collectors.toList()).get(0);
        waitForStatusCode(URI.create(getBaseUrl() + admiralMasterContainerLink),
                Operation.STATUS_CODE_OK);
        admiralMasterContainer = getDocument(admiralMasterContainerLink, ContainerState.class);

        String skipInit = getSystemOrTestProp(UPGRADE_SKIP_INITIALIZE, "false");
        if (skipInit.equals(Boolean.FALSE.toString())) {
            logger.info("---------- Initialize content before upgrade. --------");
            addContentToTheProvisionedAdmiral(admiralBranchContainer);
        } else {
            logger.info("---------- Skipping content initialization. --------");
        }

        String skipValidate = getSystemOrTestProp(UPGRADE_SKIP_VALIDATE, "false");
        if (skipValidate.equals(Boolean.FALSE.toString())) {
            logger.info("---------- Migrate data and validate content. --------");
            migrateData(admiralBranchContainer, admiralMasterContainer);
            validateCompatibility(admiralMasterContainer);
            removeData(admiralMasterContainer);
        } else {
            logger.info("---------- Skipping content validation. --------");
            // gracefully shut down Admiral to prevent loss of in-memory data like the authState;
            shutDownAdmiral(admiralBranchContainer);
        }
    }

    private void migrateData(ContainerState sourceContainer, ContainerState targetContainer)
            throws Exception {
        // wait for the admiral container to start. In 0.9.1 health check service is not available.
        // This is needed in case only validated is run
        Thread.sleep(20000);
        String parent = targetContainer.parentLink;
        ComputeState computeState = getDocument(parent, ComputeState.class);
        String source = String.format("http://%s:%s", computeState.address,
                sourceContainer.ports.get(0).hostPort) + ServiceUriPaths.DEFAULT_NODE_GROUP;

        changeBaseURI(targetContainer);

        MigrationRequest migrationRequest = new MigrationRequest();
        migrationRequest.sourceNodeGroup = source;
        try {
            logger.info("---------- Send migration request. --------");
            sendRequest(HttpMethod.POST, "/config/migration",
                    Utils.toJson(migrationRequest));
        } finally {
            setBaseURI(null);
        }

    }

    private void removeData(ContainerState admiralContainer) throws Exception {
        changeBaseURI(admiralBranchContainer);
        requestContainerDelete(new ArrayList<>(containersToDelete), false);
        delete(COMPUTE_SELF_LINK);
        delete(CREDENTIALS_SELF_LINK);
        setBaseURI(null);
    }

    private void validateCompatibility(ContainerState admiralContainer) throws Exception {
        logger.info("---------- Validate compatibility. --------");

        changeBaseURI(admiralContainer);
        // wait for the admiral container to start
        URI uri = URI.create(getBaseUrl() + NodeHealthCheckService.SELF_LINK);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINERS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        uri = URI.create(getBaseUrl() + ManagementUriParts.CONTAINER_HOSTS);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);

        com.vmware.admiral.test.integration.client.ComputeState dockerHost = getDocument(
                COMPUTE_SELF_LINK,
                com.vmware.admiral.test.integration.client.ComputeState.class, versionHeader);
        assertTrue(dockerHost != null);

        com.vmware.admiral.test.integration.client.AuthCredentialsServiceState credentials = getDocument(
                CREDENTIALS_SELF_LINK,
                com.vmware.admiral.test.integration.client.AuthCredentialsServiceState.class,
                versionHeader);
        assertTrue(credentials != null);

        // container
        validateResources(ManagementUriParts.CONTAINERS,
                com.vmware.admiral.test.integration.client.ContainerState.class);

        // application
        validateResources(ManagementUriParts.COMPOSITE_COMPONENT,
                com.vmware.admiral.test.integration.client.CompositeComponent.class);

        // network
        validateResources(ManagementUriParts.CONTAINER_NETWORKS,
                com.vmware.admiral.test.integration.client.ContainerNetworkState.class);

        // event-log
        validateResources(ManagementUriParts.EVENT_LOG,
                com.vmware.admiral.test.integration.client.EventLogState.class);

        // request-status
        validateResources(ManagementUriParts.REQUEST_STATUS,
                com.vmware.admiral.test.integration.client.ContainerRequestStatus.class);

        // placement
        validateResources(ManagementUriParts.RESOURCE_GROUP_PLACEMENTS,
                com.vmware.admiral.test.integration.client.GroupResourcePlacementState.class);

        // composite-description (template)
        validateResources(ManagementUriParts.COMPOSITE_DESC,
                com.vmware.admiral.test.integration.client.CompositeDescription.class);

        // certificate
        validateResources(ManagementUriParts.SSL_TRUST_CERTS,
                com.vmware.admiral.test.integration.client.SslTrustCertificateState.class);

        // placement zone
        validateResources(ManagementUriParts.ELASTIC_PLACEMENT_ZONE_CONFIGURATION,
                com.vmware.admiral.test.integration.client.ElasticPlacementZoneConfigurationState.class);

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
        // Create event. The template does not exist on the new admiral host
        boolean requestContainerFailed = false;
        try {
            requestContainer(getResourceDescriptionLink(false, RegistryType.V1_SSL_SECURE));
        } catch (Exception e) {
            // Do nothing we want the request to fail in order to create an event
            requestContainerFailed = true;
        }
        if (!requestContainerFailed) {
            throw new IllegalStateException("Container request was expected to fail!");
        }

        // create documents to check for after upgrade
        compositeDescriptionLink = importTemplate(serviceClient, TEMPLATE_HELLO_WORLD_NETWORK);
        // try to provision in order to create an event state
        setupCoreOsHost(DockerAdapterType.API, false);
        // provision an application with network
        requestContainer(
                getResourceDescriptionLink(false, RegistryType.V1_SSL_SECURE));

        AuthCredentialsServiceState credentials = IntegratonTestStateFactory
                .createAuthCredentials(false);
        postDocument(AuthCredentialsService.FACTORY_LINK, credentials);

        AuthCredentialsServiceState authCheck = getDocument(CREDENTIALS_SELF_LINK, AuthCredentialsServiceState.class);
        assertTrue(authCheck != null);

        // placement zone
        ElasticPlacementZoneConfigurationState epzConfigState = new ElasticPlacementZoneConfigurationState();
        epzConfigState.resourcePoolState = IntegratonTestStateFactory.createResourcePool();
        postDocument(ManagementUriParts.ELASTIC_PLACEMENT_ZONE_CONFIGURATION, epzConfigState);

        setBaseURI(null);
    }

    private void shutDownAdmiral(ContainerState admiralContainer) throws Exception {
        delete(ServiceHostManagementService.SELF_LINK);

        String hostManagementServiceLink = getBaseUrl()
                + buildServiceUri(ServiceHostManagementService.SELF_LINK);
        try {
            for (int i = 0; i < 10; i++) {
                SimpleHttpsClient.execute(HttpMethod.GET, hostManagementServiceLink);
                Thread.sleep(1000);
            }
        } catch (ConnectException e) {
            // Admiral has shut down
            return;
        }

        logger.error("Admiral did not shut down within expected time!");
    }

    private <T> void validateResources(String endpoint, Class<? extends T> clazz) throws Exception {
        HttpResponse response = SimpleHttpsClient.execute(HttpMethod.GET,
                getBaseUrl() + buildServiceUri(endpoint + EXPAND),
                null, versionHeader, null);
        assertTrue(response.statusCode == 200);
        JsonElement json = new JsonParser().parse(response.responseBody);
        JsonObject jsonObject = json.getAsJsonObject();
        JsonArray documentLinks =  jsonObject.getAsJsonArray(DOCUMENT_LINKS);
        assertTrue(documentLinks.size() > 0);
        for (int i = 0; i < documentLinks.size(); i++) {
            String selfLink = documentLinks.get(i).getAsString();
            T state = getDocument(selfLink, clazz, versionHeader);
            assertTrue(state != null);
        }
    }

    public static class MigrationRequest {
        public String sourceNodeGroup;
        public String destinationNodeGroup;
    }
}
