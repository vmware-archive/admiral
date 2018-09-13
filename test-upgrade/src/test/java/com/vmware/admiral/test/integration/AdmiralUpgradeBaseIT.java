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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.test.integration.TestPropertiesUtil.getSystemOrTestProp;

import java.io.File;
import java.io.PrintWriter;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceClientFactory;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.container.ContainerDescriptionFactoryService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService.ContainerHostDataCollectionState;
import com.vmware.admiral.compute.container.ContainerLogService;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.admiral.service.common.NodeHealthCheckService;
import com.vmware.admiral.service.common.NodeMigrationService;
import com.vmware.admiral.service.common.NodeMigrationService.MigrationRequest;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpMethod;
import com.vmware.admiral.test.integration.SimpleHttpsClient.HttpResponse;
import com.vmware.admiral.test.integration.data.IntegratonTestStateFactory;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.ServiceUriPaths;

public abstract class AdmiralUpgradeBaseIT extends BaseProvisioningOnCoreOsIT {
    private static final String DOCUMENT_LINKS = "documentLinks";
    private static final String TEMPLATE_HELLO_WORLD_NETWORK = getSystemOrTestProp(
            "test.upgrade.template.hello-world-with-network", "HelloWorld_network.yaml");

    private static final String SEPARATOR = "/";
    private static final String EXPAND = "?expand";

    private static final String CREDENTIALS_SELF_LINK = AuthCredentialsService.FACTORY_LINK
            + SEPARATOR + IntegratonTestStateFactory.AUTH_CREDENTIALS_ID;
    private static final String COMPUTE_SELF_LINK = ComputeService.FACTORY_LINK + SEPARATOR
            + IntegratonTestStateFactory.DOCKER_COMPUTE_ID;

    private static final String UPGRADE_CONTAINERS_LOGS_RETRIES = "upgrade.containers.logs.retires";
    private static final String UPGRADE_CONTAINERS_LOGS_RETRY_DELAY_MS = "upgrade.containers.logs.retry.delay.ms";

    private static final Long DEFAULT_CONTAINER_LOGS_RETRIES = 5L;
    private static final Long DEFAULT_CONTAINER_LOGS_RETRY_DELAY_MS = 1000L;

    private Set<String> applicationsToDelete = new HashSet<>();

    protected static ServiceClient serviceClient;

    @BeforeClass
    public static void beforeClass() {
        serviceClient = ServiceClientFactory.createServiceClient(null);
    }

    @AfterClass
    public static void afterClass() {
        try {
            serviceClient.stop();
        } catch (Exception ignore) {
        }
    }

    protected void migrateData(ContainerState sourceContainer, ContainerState targetContainer)
            throws Exception {
        // wait for the admiral container to start. In 0.9.1 health check service is not available.
        // This is needed in case only validated is run
        Thread.sleep(30000);
        String parent = targetContainer.parentLink;
        ComputeState computeState = getDocument(parent, ComputeState.class);
        String source = String.format("http://%s:%s", computeState.address,
                sourceContainer.ports.get(0).hostPort) + ServiceUriPaths.DEFAULT_NODE_GROUP;

        changeBaseURI(targetContainer);

        MigrationRequest migrationRequest = new MigrationRequest();
        migrationRequest.sourceNodeGroup = source;
        try {
            logger.info("---------- Send migration request. --------");
            sendRequest(HttpMethod.POST, NodeMigrationService.SELF_LINK,
                    Utils.toJson(migrationRequest));
        } finally {
            setBaseURI(null);
        }

    }

    protected void removeData(ContainerState admiralContainer) throws Exception {
        changeBaseURI(admiralContainer);
        if (!applicationsToDelete.isEmpty()) {
            // Wait until certificates are reloaded on Docker hosts in order to avoid [General SSL
            // engine] error.
            Thread.sleep(60000);
            requestContainerDelete(applicationsToDelete, false);
        }
        delete(COMPUTE_SELF_LINK);
        delete(CREDENTIALS_SELF_LINK);
        setBaseURI(null);

    }

    protected void validateContent(ContainerState admiralContainer) throws Exception {
        logger.info("---------- Validate compatibility. --------");
        waitForSuccessfulHealthcheck(admiralContainer);

        changeBaseURI(admiralContainer);
        // wait for the admiral container to start
        logger.info("--- Ensure all important services are up and running. ---");
        waitForServiceToBecomeAvailable(ManagementUriParts.CONTAINERS);
        waitForServiceToBecomeAvailable(ManagementUriParts.CONTAINER_HOSTS);

        com.vmware.admiral.test.integration.client.ComputeState dockerHost = getDocument(
                COMPUTE_SELF_LINK,
                com.vmware.admiral.test.integration.client.ComputeState.class);
        assertNotNull("Expected a compute state with selflink " + COMPUTE_SELF_LINK + " to exist",
                dockerHost);
        // Run the data collection in order to update the host
        ContainerHostDataCollectionState dataCollectionBody = new ContainerHostDataCollectionState();
        sendRequest(HttpMethod.PATCH,
                ContainerHostDataCollectionService.HOST_INFO_DATA_COLLECTION_LINK,
                Utils.toJson(dataCollectionBody));
        waitForStateChange(COMPUTE_SELF_LINK, (body) -> {
            if (body == null) {
                return false;
            }
            com.vmware.admiral.test.integration.client.ComputeState host = Utils.fromJson(body,
                    com.vmware.admiral.test.integration.client.ComputeState.class);
            // trust alias custom property must be set eventually after upgrade
            return (host.customProperties
                    .get(ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME) != null);
        });

        logger.info("--- Validate credentials. ---");
        com.vmware.admiral.test.integration.client.AuthCredentialsServiceState credentials = getDocument(
                CREDENTIALS_SELF_LINK,
                com.vmware.admiral.test.integration.client.AuthCredentialsServiceState.class);
        assertNotNull("Expected credentials with selflink " + CREDENTIALS_SELF_LINK + " to exist",
                credentials);

        // container
        logger.info("--- Validate containers. ---");
        validateResources(ManagementUriParts.CONTAINERS,
                com.vmware.admiral.test.integration.client.ContainerState.class);

        // application
        logger.info("--- Validate applications. ---");
        validateResources(ManagementUriParts.COMPOSITE_COMPONENT,
                com.vmware.admiral.test.integration.client.CompositeComponent.class);

        // network
        logger.info("--- Validate networks. ---");
        validateResources(ManagementUriParts.CONTAINER_NETWORKS,
                com.vmware.admiral.test.integration.client.ContainerNetworkState.class);

        // event-log
        logger.info("--- Validate event logs. ---");
        validateResources(ManagementUriParts.EVENT_LOG,
                com.vmware.admiral.test.integration.client.EventLogState.class);

        // request-status
        logger.info("--- Validate request statuses. ---");
        validateResources(ManagementUriParts.REQUEST_STATUS,
                com.vmware.admiral.test.integration.client.ContainerRequestStatus.class);

        // placement
        logger.info("--- Validate placements. ---");
        validateResources(ManagementUriParts.RESOURCE_GROUP_PLACEMENTS,
                com.vmware.admiral.test.integration.client.GroupResourcePlacementState.class);

        // composite-description (template)
        logger.info("--- Validate composite descriptions. ---");
        validateResources(ManagementUriParts.COMPOSITE_DESC,
                com.vmware.admiral.test.integration.client.CompositeDescription.class);

         // certificate
        logger.info("--- Validate certificates. ---");
        validateResources(ManagementUriParts.SSL_TRUST_CERTS,
                com.vmware.admiral.test.integration.client.SslTrustCertificateState.class);

        // placement zone
        logger.info("--- Validate placement zones. ---");
        validateResources(ManagementUriParts.ELASTIC_PLACEMENT_ZONE_CONFIGURATION,
                com.vmware.admiral.test.integration.client.ElasticPlacementZoneConfigurationState.class);

        setBaseURI(null);
    }

    protected void changeBaseURI(ContainerState admiralContainer) throws Exception {
        String parent = admiralContainer.parentLink;
        ComputeState computeState = getDocument(parent, ComputeState.class);
        String baseUri = String.format("http://%s:%s", computeState.address,
                admiralContainer.ports.get(0).hostPort);
        logger.warning("Changing base URI to [%s]", baseUri);
        setBaseURI(baseUri);
    }

    protected void addContentToTheProvisionedAdmiral(ContainerState admiralContainer)
            throws Exception {
        changeBaseURI(admiralContainer);

        // wait for the admiral container to start. In 0.9.1 health check service is not available
        logger.info("--- Giving Admiral some time to boot. ---");
        Thread.sleep(20000);

        // ensure some core services are up and running
        logger.info("--- Ensure all important services are up and running. ---");
        waitForServiceToBecomeAvailable(ManagementUriParts.CONTAINERS);
        waitForServiceToBecomeAvailable(ManagementUriParts.REQUESTS);
        waitForServiceToBecomeAvailable(ManagementUriParts.REQUEST_STATUS);
        waitForServiceToBecomeAvailable(ManagementUriParts.CONTAINER_HOSTS);

        // Create event. The template does not exist on the new admiral host
        logger.info("--- Create a dummy failed event log. ---");
        boolean requestContainerFailed = false;
        try {
            requestContainer(UriUtils.buildUriPath(
                    ContainerDescriptionFactoryService.SELF_LINK,
                    "no-such-description"));
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("Service not found")) {
                // Do nothing we want the request to fail in order to create an event
                requestContainerFailed = true;
            } else {
                logger.error("Unexpected error thrown: %s", Utils.toString(e));
            }
        }
        if (!requestContainerFailed) {
            throw new IllegalStateException("Container request was expected to fail!");
        }

        // create documents to check for after upgrade
        logger.info("--- Import a sample template. ---");
        String templateLink = importTemplate(serviceClient, TEMPLATE_HELLO_WORLD_NETWORK);
        // try to provision in order to create an event state
        logger.info("--- Add a Docker host. ---");
        setupCoreOsHost(DockerAdapterType.API, false, null);
        // provision an application with network
        logger.info("--- Provision the imported template. ---");
        requestContainer(templateLink);

        logger.info("--- Create some credentials. ---");
        AuthCredentialsServiceState credentials = IntegratonTestStateFactory
                .createAuthCredentials(false);
        postDocument(AuthCredentialsService.FACTORY_LINK, credentials);

        // placement zone
        logger.info("--- Create a placement zone. ---");
        ElasticPlacementZoneConfigurationState epzConfigState = new ElasticPlacementZoneConfigurationState();
        epzConfigState.resourcePoolState = IntegratonTestStateFactory.createResourcePool();
        postDocument(ManagementUriParts.ELASTIC_PLACEMENT_ZONE_CONFIGURATION, epzConfigState);

        setBaseURI(null);
    }

    protected void storeContainerLogs(ContainerState container, String logsDir) throws Throwable {
        File logFile = new File(logsDir, generateLogNameForContainer(container));

        String containerName = container.names.iterator().next();
        logger.info("Trying to save logs for container %s in %s", containerName,
                logFile.getAbsolutePath());
        String logs = retrieveContainerLogs(container);
        if (logs == null) {
            logger.warning("No logs found for container %s. Nothing is saved.", containerName);
            return;
        }

        try (PrintWriter writer = new PrintWriter(logFile)) {
            IOUtils.write(logs, writer);
        }
    }

    private String generateLogNameForContainer(ContainerState container) {
        return String.format("%s-%s-%s.log",
                getClass().getSimpleName(),
                container.names.iterator().next(),
                System.currentTimeMillis());
    }

    private String retrieveContainerLogs(ContainerState container) throws Throwable {
        // Calling the API for container logs will trigger a call for the logs to docker. However,
        // the backend is not waiting for the result to be returned from docker and gets whatever is
        // currently stored in memory. In our case, since there were no previous calls for logs, no
        // logs will be returned the first time. If there is some network latency and the logs are
        // big enough, a few subsequent requests for logs might return nothing as well.

        long retries = Long.valueOf(getSystemOrTestProp(UPGRADE_CONTAINERS_LOGS_RETRIES,
                "" + DEFAULT_CONTAINER_LOGS_RETRIES));
        return retrieveContainerLogs(container, retries);
    }

    private String retrieveContainerLogs(ContainerState container, long retries) throws Throwable {
        String logs = null;

        long delay = Long.valueOf(getSystemOrTestProp(UPGRADE_CONTAINERS_LOGS_RETRY_DELAY_MS,
                "" + DEFAULT_CONTAINER_LOGS_RETRY_DELAY_MS));

        while ((logs = doRetrieveContainerLogs(container)) == null && retries-- > 0) {
            logger.warning("No logs found for container %s. Will retry %d times.",
                    container.names.iterator().next(), retries);
            Thread.sleep(delay);
        }

        return logs;
    }

    private String doRetrieveContainerLogs(ContainerState container) throws Throwable {
        String containerId = Service.getId(container.documentSelfLink);
        String query = UriUtils.buildUriQuery(
                "id", containerId,
                "timestamps", Boolean.toString(true),
                "since", "" + Duration.ofHours(1).getSeconds());
        String url = ContainerLogService.SELF_LINK + UriUtils.URI_QUERY_CHAR + query;

        LogServiceState logState = getDocument(url, LogServiceState.class);
        if (logState.logs == null) {
            return null;
        }

        String logs = new String(logState.logs);
        return (logs.isEmpty() || "--".equals(logs)) ? null : logs;
    }

    protected void waitForSuccessfulHealthcheck(ContainerState admiralContainer) throws Exception {
        logger.info("--- Waiting for a successful healthcheck. ---");
        changeBaseURI(admiralContainer);
        waitForServiceToBecomeAvailable(NodeHealthCheckService.SELF_LINK);
        setBaseURI(null);
    }

    private void waitForServiceToBecomeAvailable(String serviceSelfLink) throws Exception {
        logger.info("- Waiting for service to return status code OK [%s] -", serviceSelfLink);
        URI uri = URI.create(getBaseUrl() + serviceSelfLink);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);
        logger.info("- Service is now available [%s] -", serviceSelfLink);
    }

    private <T> void validateResources(String endpoint, Class<? extends T> clazz)
            throws Exception {
        URI uri = URI.create(getBaseUrl() + endpoint);
        waitForStatusCode(uri, Operation.STATUS_CODE_OK);

        String targetUrl = getBaseUrl() + buildServiceUri(endpoint + EXPAND);
        HttpResponse response = SimpleHttpsClient.execute(HttpMethod.GET, targetUrl, null, null);
        assertEquals("Unexpected status code when trying to get " + targetUrl,
                Operation.STATUS_CODE_OK, response.statusCode);
        JsonElement json = new JsonParser().parse(response.responseBody);
        JsonObject jsonObject = json.getAsJsonObject();
        JsonArray documentLinks =  jsonObject.getAsJsonArray(DOCUMENT_LINKS);
        assertTrue("No documentLinks found in response of GET " + targetUrl,
                documentLinks.size() > 0);
        for (int i = 0; i < documentLinks.size(); i++) {
            String selfLink = documentLinks.get(i).getAsString();
            T state = getDocument(selfLink, clazz);
            assertNotNull("Expected non-null instance of class " + clazz.getName(), state);
            // add applications created in the initialization phase for deletion
            if (com.vmware.admiral.test.integration.client.CompositeComponent.class.equals(clazz)) {
                applicationsToDelete.add(selfLink);
            }
        }
    }

    protected void deleteApplicationAfter(String compositeLink) {
        if (compositeLink != null) {
            applicationsToDelete.add(compositeLink);
        }
    }
}
