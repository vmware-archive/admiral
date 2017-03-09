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

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.support.CertificateInfoServiceErrorResponse;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService;
import com.vmware.photon.controller.model.tasks.EndpointAllocationTaskService.EndpointAllocationTaskState;
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService;
import com.vmware.photon.controller.model.tasks.EndpointRemovalTaskService.EndpointRemovalTaskState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

/**
 * Stateless service that simplifies(adapts) CRUD operations with endpoints.
 *
 * <p>
 * Here are the supported actions and their behavior:
 * <ul>
 *
 * <li>{@code GET}: Use the {@link EndpointAdapterService#SELF_LINK} URL to get all endpoints.
 * Append the endpoint link to {@link EndpointAdapterService#SELF_LINK} to retrieve the
 * {@link EndpointState} for an existing endpoint. For example:
 * {@code http://host:port/config/endpoints/resources/endpoints/endpoint-1}.
 *
 * <li>{@code POST}: Post {@link EndpointAdapterService#SELF_LINK}, a valid {@link EndpointState} to
 * create an endpoint. The returned body contains the created {@link EndpointState}. Adding a
 * {@code enumerate} query parameter to the URI, will also trigger resource enumeration.
 *
 * <li>{@code PUT}: Put to {@link EndpointAdapterService#SELF_LINK}, a valid {@link EndpointState}
 * to update the endpoint.
 *
 * <li>{@code PUT}: Put to {@link EndpointAdapterService#SELF_LINK} specifying a {@code validate}
 * query parameter will only validate the endpoint data.
 *
 * <li>{@code DELETE}: Issue DELETE to {@link EndpointAdapterService#SELF_LINK} with appended
 * {@link EndpointState} document link to delete the endpoint.
 * </ul>
 */
public class EndpointAdapterService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.ENDPOINTS;

    private static final String DEFAULT_SCHEDULED_TASK_INTERVAL_MICROS = System.getProperty(
            "default.scheduled.stats.collection.interval.micros",
            String.valueOf(TimeUnit.MINUTES.toMicros(5)));

    public EndpointAdapterService() {
        super.toggleOption(ServiceOption.URI_NAMESPACE_OWNER, true);
    }

    @Override
    public void handleGet(Operation get) {
        String endpointLink = extractEndpointLink(get);

        if (endpointLink == null || endpointLink.isEmpty()) {
            // return all endpoints
            doGetAll(get, UriUtils.hasODataExpandParamValue(get.getUri()));
        } else {
            // return the requested endpoint
            doGet(get, endpointLink);
        }
    }

    private void doGetAll(Operation get, boolean expand) {
        URI endpointUri = UriUtils.buildUri(getHost(), EndpointService.FACTORY_LINK,
                get.getUri().getQuery());

        Operation.createGet(endpointUri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        get.fail(e);
                        return;
                    }

                    get.setBody(o.getBodyRaw());
                    get.complete();
                }).sendWith(this);
    }

    private void doGet(Operation get, String endpointLink) {
        Operation.createGet(this, endpointLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        get.fail(o.getStatusCode(), e, o.getBodyRaw());
                        return;
                    }
                    get.setBody(o.getBodyRaw());
                    get.complete();
                }).sendWith(this);
    }

    @Override
    public void handlePost(Operation post) {
        EndpointState state = validateState(post);
        String query = post.getUri().getQuery();
        boolean enumerate = query != null
                && query.contains(ManagementUriParts.REQUEST_PARAM_ENUMERATE_OPERATION_NAME);

        EndpointAllocationTaskState eats = new EndpointAllocationTaskState();
        eats.endpointState = state;
        eats.tenantLinks = state.tenantLinks;
        eats.taskInfo = new TaskState();
        eats.taskInfo.isDirect = true;
        eats.options = EnumSet.noneOf(TaskOption.class);

        eats.options.add(TaskOption.PRESERVE_MISSING_RESOUCES);
        if (DeploymentProfileConfig.getInstance().isTest()) {
            eats.options.add(TaskOption.IS_MOCK);
        }

        if (enumerate) {
            eats.enumerationRequest = new EndpointAllocationTaskService.ResourceEnumerationRequest();
            eats.enumerationRequest.resourcePoolLink = UriUtils.getODataParamValueAsString(
                    post.getUri(), ManagementUriParts.REQUEST_PARAM_TARGET_RESOURCE_POOL_LINK);
            eats.enumerationRequest.delayMicros = TimeUnit.SECONDS.toMicros(2);
        }

        Operation.createPost(this, EndpointAllocationTaskService.FACTORY_LINK)
                .setBody(eats)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleException(post, "creating", state.name, o.getStatusCode(), e);
                        return;
                    }
                    EndpointAllocationTaskState body = o.getBody(EndpointAllocationTaskState.class);
                    if (body.taskInfo.stage == TaskStage.FAILED) {
                        handleServiceErrorResponse(post, o.getStatusCode(), e,
                                body.taskInfo.failure);
                        return;
                    }

                    // Patch ResourcePoolState created by EndpointAllocationTaskService
                    ResourcePoolState patchRPWithComputeType = new ResourcePoolState();
                    patchRPWithComputeType.customProperties = Collections.singletonMap(
                            PlacementZoneConstants.RESOURCE_TYPE_CUSTOM_PROP_NAME,
                            ResourceType.COMPUTE_TYPE.getName());

                    sendRequest(Operation.createPatch(this, body.endpointState.resourcePoolLink)
                            .setBody(patchRPWithComputeType)
                            .setCompletion((op, ex) -> {
                                if (ex != null) {
                                    handleException(post, "creating", state.name, op.getStatusCode(), ex);
                                    return;
                                }

                                // Once patch ResourcePoolState is done continue with EndpointState processing
                                if (enumerate) {
                                    triggerStatsCollection(body);
                                }
                                post.setBody(body.endpointState);
                                post.complete();
                            }));
                })
                .sendWith(this);
    }

    @Override
    public void handlePut(Operation put) {
        EndpointState state = validateState(put);
        String query = put.getUri().getQuery();
        boolean validateConnection = query != null
                && query.contains(ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);

        EndpointAllocationTaskState eats = new EndpointAllocationTaskState();
        eats.endpointState = state;
        eats.tenantLinks = state.tenantLinks;
        eats.taskInfo = new TaskState();
        eats.taskInfo.isDirect = true;
        eats.options = EnumSet.noneOf(TaskOption.class);

        if (DeploymentProfileConfig.getInstance().isTest()) {
            eats.options.add(TaskOption.IS_MOCK);
        }

        if (validateConnection) {
            eats.options.add(TaskOption.VALIDATE_ONLY);
        } else if (state.documentSelfLink == null) {
            put.fail(new LocalizableValidationException("Invalid state passed for update", "compute.endpoint.adapter.invalid.state"));
        }

        Operation.createPost(this, EndpointAllocationTaskService.FACTORY_LINK)
                .setBody(eats)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleException(put, "updating", state.name, o.getStatusCode(), e);
                        return;
                    }
                    EndpointAllocationTaskState body = o.getBody(EndpointAllocationTaskState.class);
                    if (body.taskInfo.stage == TaskStage.FAILED) {
                        if (validateConnection && CertificateInfoServiceErrorResponse.KIND
                                .equals(body.taskInfo.failure.documentKind)) {
                            put.setBody(body.taskInfo.failure);
                            put.setStatusCode(HttpURLConnection.HTTP_OK);
                            put.complete();
                            return;
                        }
                        handleServiceErrorResponse(put, o.getStatusCode(), e,
                                body.taskInfo.failure);
                        return;
                    }
                    put.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
                    put.setBody(null);
                    put.complete();
                })
                .sendWith(this);
    }

    @Override
    public void handleDelete(Operation delete) {
        String endpointLink = extractEndpointLink(delete);

        if (endpointLink == null || endpointLink.isEmpty()) {
            throw new LocalizableValidationException(
                    "No endpoint link given in the DELETE path: " + delete.getUri().getPath(),
                    "compute.endpoint.adapter.delete.endpoint.missing", delete.getUri().getPath());
        }

        String id = statsCollectionId(endpointLink);

        EndpointRemovalTaskState state = new EndpointRemovalTaskState();
        state.endpointLink = endpointLink;
        state.taskInfo = new TaskState();
        state.taskInfo.isDirect = true;

        if (DeploymentProfileConfig.getInstance().isTest()) {
            state.options = EnumSet.of(TaskOption.IS_MOCK);
        }

        Operation.createPost(this, EndpointRemovalTaskService.FACTORY_LINK)
                .setBody(state)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleException(delete, "deleting", endpointLink, o.getStatusCode(), e);
                        return;
                    }
                    delete.complete();
                })
                .sendWith(this);

        // Delete scheduled stats collection
        Operation.createDelete(this, UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, id))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logInfo("Uneble to delete scheduled stats collection task, reason: %s",
                                e.getMessage());
                    }
                })
                .sendWith(this);
    }

    private EndpointState validateState(Operation op) {
        if (!op.hasBody()) {
            throw new LocalizableValidationException("Body is required", "compute.endpoint.adapter.body.required");
        }

        EndpointState state = op.getBody(EndpointState.class);

        if (state.endpointType == null) {
            throw new LocalizableValidationException("Endpoint type is required", "compute.endpoint.adapter.enpoint.type.required");
        }
        return state;
    }

    private String extractEndpointLink(Operation op) {
        String currentPath = UriUtils.normalizeUriPath(op.getUri().getPath());
        // resolve the link to the requested endpoint
        String endpointLink = null;
        if (currentPath.startsWith(SELF_LINK)) {
            endpointLink = currentPath.substring(SELF_LINK.length());
        }
        return endpointLink;
    }

    private void handleException(Operation op, String opName, String endpoint, int statusCode,
            Throwable e) {
        ServiceErrorResponse rsp = Utils.toValidationErrorResponse(e, op);
        String message = String.format("Error %s endpoint %s : %s",
                opName, endpoint, rsp.message);
        LocalizableValidationException outerEx = new LocalizableValidationException(
                message, "compute.endpoint.operation.error." + opName,
                endpoint, rsp.message);
        rsp.message = Utils.toValidationErrorResponse(outerEx, op).message;

        handleServiceErrorResponse(op, statusCode, e, rsp);
    }

    private void handleServiceErrorResponse(Operation op, int statusCode, Throwable e,
            ServiceErrorResponse rsp) {
        logWarning(e.getMessage());
        op.setStatusCode(statusCode);
        op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
        op.fail(e, rsp);
    }

    private void triggerStatsCollection(EndpointAllocationTaskState currentState) {

        EndpointState endpoint = currentState.endpointState;

        String id = statsCollectionId(endpoint.documentSelfLink);

        long intervalMicros = currentState.enumerationRequest.refreshIntervalMicros != null
                ? currentState.enumerationRequest.refreshIntervalMicros
                : Long.valueOf(DEFAULT_SCHEDULED_TASK_INTERVAL_MICROS);

        StatsCollectionTaskState statsCollectionTask = new StatsCollectionTaskState();
        statsCollectionTask.resourcePoolLink = endpoint.resourcePoolLink;
        statsCollectionTask.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);

        // Create additional Query clause
        List<Query> queries = Arrays.asList(
                Query.Builder.create(Occurance.MUST_OCCUR)
                        .setTerm(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST.name()).build());

        statsCollectionTask.customizationClauses = queries;

        ScheduledTaskState scheduledTaskState = new ScheduledTaskState();
        scheduledTaskState.documentSelfLink = id;
        scheduledTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        scheduledTaskState.initialStateJson = Utils.toJson(statsCollectionTask);
        scheduledTaskState.intervalMicros = intervalMicros;
        scheduledTaskState.delayMicros = currentState.enumerationRequest.delayMicros;
        scheduledTaskState.tenantLinks = endpoint.tenantLinks;
        scheduledTaskState.customProperties = new HashMap<>();
        scheduledTaskState.customProperties.put(ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                endpoint.documentSelfLink);

        Operation.createPost(this, ScheduledTaskService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBody(scheduledTaskState)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error triggering stats collection task, reason: %s",
                                e.getMessage());
                    } else {
                        logInfo("Stats collection has been scheduled for endpoint: %s",
                                currentState.documentSelfLink);
                    }

                })
                .sendWith(this);
    }

    private String statsCollectionId(String endpointLink) {
        String statsCollectionSuffix = "-stats-collection";

        // Append suffix to avoid duplication with enumeration scheduler.
        String id = UriUtils.getLastPathSegment(endpointLink)
                .concat(statsCollectionSuffix);
        return id;
    }

}
