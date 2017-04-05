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

package com.vmware.admiral.compute.endpoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static com.vmware.admiral.compute.PlacementZoneConstants.RESOURCE_TYPE_CUSTOM_PROP_NAME;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.ComputeBaseTest;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.test.TestContext;
import com.vmware.xenon.services.common.AuthCredentialsService;

public class EndpointAdapterServiceTest extends ComputeBaseTest {

    @Before
    public void setUp() throws Throwable {
        waitForServiceAvailability(EndpointService.FACTORY_LINK);
        waitForServiceAvailability(ComputeDescriptionService.FACTORY_LINK);
        waitForServiceAvailability(ComputeService.FACTORY_LINK);
        waitForServiceAvailability(AuthCredentialsService.FACTORY_LINK);
        DeploymentProfileConfig.getInstance().setTest(true);
    }

    @After
    public void tearDown() throws Throwable {
        DeploymentProfileConfig.getInstance().setTest(false);
    }

    @Test
    public void testListEndpoints() throws Throwable {
        EndpointState endpoint1 = createEndpoint("ep1");
        allocateEndpoint(endpoint1);

        EndpointState endpoint2 = createEndpoint("ep2");
        allocateEndpoint(endpoint2);

        ServiceDocumentQueryResult queryResult = getDocument(ServiceDocumentQueryResult.class,
                EndpointAdapterService.SELF_LINK);
        assertNotNull(queryResult);
        assertNotNull(queryResult.documentLinks);
        assertEquals(2, queryResult.documentLinks.size());
    }

    @Test
    public void testListEndpointsExpanded() throws Throwable {
        EndpointState endpoint1 = createEndpoint("ep1");
        allocateEndpoint(endpoint1);

        EndpointState endpoint2 = createEndpoint("ep2");
        allocateEndpoint(endpoint2);

        TestContext ctx = testCreate(1);
        AtomicReference<ServiceDocumentQueryResult> result = new AtomicReference<>();
        URI uri = UriUtils
                .buildExpandLinksQueryUri(
                        UriUtils.buildUri(host, EndpointAdapterService.SELF_LINK));

        Operation list = Operation.createGet(uri);
        list.setCompletion((o, e) -> {
            if (e != null) {
                host.log("Can't load endpoint state objects. Error: %s", Utils.toString(e));
                ctx.failIteration(e);
            } else {
                result.set(o.getBody(ServiceDocumentQueryResult.class));
                ctx.completeIteration();
            }
        });
        host.send(list);
        ctx.await();

        ServiceDocumentQueryResult queryResult = result.get();
        assertNotNull(queryResult);
        assertNotNull(queryResult.documentLinks);
        assertEquals(2, queryResult.documentLinks.size());
        assertNotNull(queryResult.documents);
        assertEquals(2, queryResult.documents.size());
    }

    @Test
    public void testGetEndpoint() throws Throwable {
        EndpointState endpoint1 = createEndpoint("ep1");

        EndpointAllocationTaskState newEndpointState1 = allocateEndpoint(endpoint1);

        String uriPath = UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK,
                newEndpointState1.endpointState.documentSelfLink);

        EndpointState endpointState = getDocument(EndpointState.class, uriPath);

        assertNotNull(endpointState);
        assertEquals(newEndpointState1.endpointState.documentSelfLink,
                endpointState.documentSelfLink);
    }

    @Test
    public void testCreateEndpoint() throws Throwable {
        EndpointState endpoint = createEndpoint("ep");

        EndpointState newEndpointState = doPost(endpoint,
                EndpointAdapterService.SELF_LINK);

        assertNotNull(newEndpointState);
        assertNotNull(newEndpointState.documentSelfLink);
        assertNotNull(newEndpointState.authCredentialsLink);
        assertNotNull(newEndpointState.computeLink);

        // Verify EP default ResourcePool has RESOURCE_TYPE_CUSTOM_PROP_NAME set.
        ResourcePoolState epRp = getDocument(
                ResourcePoolState.class,
                newEndpointState.resourcePoolLink);
        assertNotNull(epRp);
        assertNotNull(epRp.customProperties);
        assertEquals(RESOURCE_TYPE_CUSTOM_PROP_NAME + " custom property is not set.",
                ResourceType.COMPUTE_TYPE.getName(),
                epRp.customProperties.get(RESOURCE_TYPE_CUSTOM_PROP_NAME));
    }

    @Test
    public void testValidateEndpoint() throws Throwable {
        EndpointState endpoint = createEndpoint("ep");

        DeploymentProfileConfig.getInstance().setTest(true);
        host.testStart(1);
        URI validateUri = UriUtils.buildUri(this.host, EndpointAdapterService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
        Operation op = Operation
                .createPut(validateUri)
                .setBody(endpoint)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        host.failIteration(e);
                        return;
                    }
                    if (o.getStatusCode() != HttpURLConnection.HTTP_NO_CONTENT) {
                        host.failIteration(new IllegalStateException(
                                "Expected status code 204 when validation is successful. Status: "
                                        + o.getStatusCode()));
                        return;
                    }

                    if (o.getBodyRaw() != null) {
                        host.failIteration(new IllegalStateException(
                                "No body expected when validation is successful."));
                        return;
                    }

                    host.completeIteration();
                });

        host.send(op);
        host.testWait();
    }

    @Test
    public void testCreateEndpointWithEnumerationEnabled() throws Throwable {

        ServiceDocumentQueryResult queryResult = getDocument(
                ServiceDocumentQueryResult.class,
                ResourcePoolService.FACTORY_LINK);

        final String endpointAdapterUri = String.format("%s?operation=%s&%s=%s",
                EndpointAdapterService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_ENUMERATE_OPERATION_NAME,
                ManagementUriParts.REQUEST_PARAM_TARGET_RESOURCE_POOL_LINK,
                queryResult.documents.keySet().stream().findFirst().get());

        EndpointState endpoint = createEndpoint("testCreateEndpointWithEnumerationEnabled");

        endpoint = doPost(endpoint, endpointAdapterUri);

        assertNotNull(endpoint);
        assertNotNull(endpoint.documentSelfLink);
        assertNotNull(endpoint.authCredentialsLink);
        assertNotNull(endpoint.computeLink);

        // Check resource-enumeration scheduled task was created
        {
            String scheduledTaskLink = UriUtils.buildUriPath(
                    ScheduledTaskService.FACTORY_LINK,
                    UriUtils.getLastPathSegment(endpoint.documentSelfLink));

            ScheduledTaskState scheduledTask = getDocument(
                    ScheduledTaskState.class,
                    scheduledTaskLink);
            assertNotNull("resource-enumeration scheduled task is NOT created", scheduledTask);
        }

        {
            // Check stats-collection scheduled task was created
            String scheduledTaskLink = UriUtils.buildUriPath(
                    ScheduledTaskService.FACTORY_LINK,
                    EndpointAdapterService.statsCollectionId(endpoint.documentSelfLink));

            ScheduledTaskState scheduledTask = getDocument(
                    ScheduledTaskState.class,
                    scheduledTaskLink);
            assertNotNull("stats-collection scheduled task is NOT created", scheduledTask);
        }

        {
            // Check PRIVATE image-enumeration scheduled task was created

            String privateImagesEnumerationId = EndpointAdapterService.privateImagesEnumerationId(
                    endpoint.documentSelfLink);

            String scheduledTaskLink = UriUtils.buildUriPath(
                    ScheduledTaskService.FACTORY_LINK,
                    privateImagesEnumerationId);

            ScheduledTaskState scheduledTask = getDocument(
                    ScheduledTaskState.class,
                    scheduledTaskLink);
            assertNotNull("Private image-enumeration scheduled task is NOT created", scheduledTask);

            /*
            // Check PRIVATE image-enumeration task was created

            Query imageEnumTasksQuery = Query.Builder.create()
                    .addKindFieldClause(ImageEnumerationTaskState.class)
                    .addCompositeFieldClause(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.CREATE_CONTEXT_PROP_NAME,
                            scheduledTask.documentSelfLink)
                    .build();

            List<String> imageEnumTaskLinks = QueryTop.waitToComplete(
                    new QueryTop<>(this.host, imageEnumTasksQuery, ImageEnumerationTaskState.class,
                            scheduledTask.tenantLinks)
                                    .setMaxResultsLimit(1)
                                    .collectLinks(Collectors.toList()));

            final String imageEnumTaskLink = imageEnumTaskLinks.get(0);

            // Wait for image-enumeration task cause it is scheduled with a delay
            // NOTE: the task is created with SELF_DELETE_ON_COMPLETION so wait for its availability
            host.waitForServiceAvailable(imageEnumTaskLink);
            */
        }

        {
            // Check PUBLIC image-enumeration scheduled task was created

            String publicImagesEnumerationId = EndpointAdapterService.publicImagesEnumerationId(
                    endpoint.endpointType,
                    endpoint.endpointProperties.get(EndpointConfigRequest.REGION_KEY));

            String scheduledTaskLink = UriUtils.buildUriPath(
                    ScheduledTaskService.FACTORY_LINK,
                    publicImagesEnumerationId);

            ScheduledTaskState scheduledTask = getDocument(
                    ScheduledTaskState.class,
                    scheduledTaskLink);
            assertNotNull("Public image-enumeration scheduled task is NOT created", scheduledTask);

            /*
            // Check PUBLIC image-enumeration task was created

            Query imageEnumTasksQuery = Query.Builder.create()
                    .addKindFieldClause(ImageEnumerationTaskState.class)
                    .addCompositeFieldClause(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            ComputeProperties.CREATE_CONTEXT_PROP_NAME,
                            scheduledTask.documentSelfLink)
                    .build();

            List<String> imageEnumTaskLinks = QueryTop.waitToComplete(
                    new QueryTop<>(this.host, imageEnumTasksQuery, ImageEnumerationTaskState.class,
                            scheduledTask.tenantLinks)
                                    .setMaxResultsLimit(1)
                                    .collectLinks(Collectors.toList()));

            final String imageEnumTaskLink = imageEnumTaskLinks.get(0);

            // Wait for image-enumeration task cause it is scheduled with a delay
            // NOTE: the task is created with SELF_DELETE_ON_COMPLETION so wait for its availability
            host.waitForServiceAvailable(imageEnumTaskLink);
            */
        }
    }

    @Test
    public void testCreateEndpointsOfSameTypeWithEnumerationEnabled() throws Throwable {

        final String endpointAdapterUri = String.format("%s?operation=%s",
                EndpointAdapterService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_ENUMERATE_OPERATION_NAME);

        // Create two EPs of the same type {{
        EndpointState endpoint1 = createEndpoint(
                "testCreateEndpointsOfSameTypeWithEnumerationEnabled_1");

        endpoint1 = doPost(endpoint1, endpointAdapterUri);

        EndpointState endpoint2 = createEndpoint(
                "testCreateEndpointsOfSameTypeWithEnumerationEnabled_2");

        endpoint2 = doPost(endpoint2, endpointAdapterUri);
        // }}

        // Calculate links of expected ScheduledTasks {{
        String ep1PrivateImagesScheduledTaskLink = UriUtils.buildUriPath(
                ScheduledTaskService.FACTORY_LINK,
                EndpointAdapterService.privateImagesEnumerationId(endpoint1.documentSelfLink));

        String ep2PrivateImagesScheduledTaskLink = UriUtils.buildUriPath(
                ScheduledTaskService.FACTORY_LINK,
                EndpointAdapterService.privateImagesEnumerationId(endpoint2.documentSelfLink));

        String epPublicImagesScheduledTaskLink = UriUtils.buildUriPath(
                ScheduledTaskService.FACTORY_LINK,
                EndpointAdapterService.publicImagesEnumerationId(endpoint1.endpointType,
                        endpoint1.endpointProperties.get(EndpointConfigRequest.REGION_KEY)));
        // }}

        // Assert expected ScheduledTasks are created {{
        ServiceDocumentQueryResult queryResult = getDocument(
                ServiceDocumentQueryResult.class,
                ScheduledTaskService.FACTORY_LINK);

        // 2 x (1 res enum, 1 stats collection, 1 private images enum) + 1 public images enum
        assertEquals(7, queryResult.documentLinks.size());

        assertTrue("ScheduledTask is not created for Private images-enum for EP1",
                queryResult.documentLinks.contains(ep1PrivateImagesScheduledTaskLink));
        assertTrue("ScheduledTask is not created for Private images-enum for EP2",
                queryResult.documentLinks.contains(ep2PrivateImagesScheduledTaskLink));

        assertTrue("ScheduledTask is not created for Public images-enum for EP1 and EP2",
                queryResult.documentLinks.contains(epPublicImagesScheduledTaskLink));
        // }}
    }

    @Test
    public void testDeleteEndpoint() throws Throwable {
        EndpointState endpoint = createEndpoint("ep");

        EndpointAllocationTaskState endpointAllocationTask = allocateEndpoint(endpoint);
        assertNotNull(endpointAllocationTask);
        assertNotNull(endpointAllocationTask.endpointState);
        assertNotNull(endpointAllocationTask.endpointState.documentSelfLink);
        delete(UriUtils.buildUriPath(EndpointAdapterService.SELF_LINK,
                endpointAllocationTask.endpointState.documentSelfLink));
    }

}
