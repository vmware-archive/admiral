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
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.PlacementZoneConstants;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
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
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService;
import com.vmware.photon.controller.model.tasks.ImageEnumerationTaskService.ImageEnumerationTaskState;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService;
import com.vmware.photon.controller.model.tasks.ScheduledTaskService.ScheduledTaskState;
import com.vmware.photon.controller.model.tasks.TaskOption;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService;
import com.vmware.photon.controller.model.tasks.monitoring.StatsCollectionTaskService.StatsCollectionTaskState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.ServiceHost.ServiceAlreadyStartedException;
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

    private static final Long DEFAULT_SCHEDULED_STATS_COLLECTION_INTERVAL = Long.valueOf(
            System.getProperty(
                    "default.scheduled.stats.collection.interval.micros",
                    String.valueOf(TimeUnit.MINUTES.toMicros(5))));

    private static final Long DEFAULT_SCHEDULED_IMAGE_ENUM_INTERVAL = Long.valueOf(
            System.getProperty(
                    "default.scheduled.image.enumeration.interval.micros",
                    String.valueOf(TimeUnit.DAYS.toMicros(7))));

    private static final long DEFAULT_SCHEDULED_TASK_DELAY = TimeUnit.SECONDS.toMicros(2);

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
        EndpointState endpointState = validateState(post);
        String query = post.getUri().getQuery();

        // If 'enumerate' flag is set triggers the following end-point specific enumerations:
        // - Resource Enumeration (as part of EndpointAllocationTaskService request)
        // - Stats Collection (as part of this request)
        // - Public Image Enumeration (as part of this request)
        // - Private Image Enumeration (as part of this request)
        final boolean enumerate = query != null
                && query.contains(ManagementUriParts.REQUEST_PARAM_ENUMERATE_OPERATION_NAME);

        final EndpointAllocationTaskState eats = new EndpointAllocationTaskState();
        eats.endpointState = endpointState;
        eats.tenantLinks = endpointState.tenantLinks;
        eats.taskInfo = new TaskState();
        eats.taskInfo.isDirect = true;
        eats.options = EnumSet.of(TaskOption.PRESERVE_MISSING_RESOUCES);
        if (DeploymentProfileConfig.getInstance().isTest()) {
            eats.options.add(TaskOption.IS_MOCK);
        }

        if (enumerate) {
            eats.enumerationRequest = new EndpointAllocationTaskService.ResourceEnumerationRequest();
            eats.enumerationRequest.resourcePoolLink = UriUtils.getODataParamValueAsString(
                    post.getUri(), ManagementUriParts.REQUEST_PARAM_TARGET_RESOURCE_POOL_LINK);
            eats.enumerationRequest.delayMicros = DEFAULT_SCHEDULED_TASK_DELAY;
        }

        Operation.createPost(this, EndpointAllocationTaskService.FACTORY_LINK)
                .setBody(eats)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleException(post, "creating", endpointState.name, o.getStatusCode(), e);
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
                                    handleException(post, "creating", endpointState.name,
                                            op.getStatusCode(), ex);
                                    return;
                                }

                                // Continue with EndpointState processing
                                DeferredResult<Void> additionalDrs = enumerate
                                        ?  DeferredResult.allOf(
                                                triggerStatsCollection(body),
                                                triggerPublicImageEnumeration(body),
                                                triggerPrivateImageEnumeration(body))
                                        : DeferredResult.completed(null);

                                additionalDrs.whenComplete((ignore, err) -> {
                                    post.setBody(body.endpointState).complete();
                                });
                            }));
                })
                .sendWith(this);
    }

    @Override
    public void handlePut(Operation put) {
        EndpointState endpointState = validateState(put);

        String query = put.getUri().getQuery();
        boolean validateConnection = query != null
                && query.contains(ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);

        final EndpointAllocationTaskState eats = new EndpointAllocationTaskState();
        eats.endpointState = endpointState;
        eats.tenantLinks = endpointState.tenantLinks;
        eats.taskInfo = new TaskState();
        eats.taskInfo.isDirect = true;
        eats.options = EnumSet.noneOf(TaskOption.class);
        if (DeploymentProfileConfig.getInstance().isTest()) {
            eats.options.add(TaskOption.IS_MOCK);
        }

        if (validateConnection) {
            eats.options.add(TaskOption.VALIDATE_ONLY);
        } else if (endpointState.documentSelfLink == null) {
            put.fail(new LocalizableValidationException("Invalid state passed for update",
                    "compute.endpoint.adapter.invalid.state"));
        }

        Operation.createPost(this, EndpointAllocationTaskService.FACTORY_LINK)
                .setBody(eats)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleException(put, "updating", endpointState.name, o.getStatusCode(), e);
                        return;
                    }
                    EndpointAllocationTaskState body = o.getBody(EndpointAllocationTaskState.class);
                    if (body.taskInfo.stage == TaskStage.FAILED) {
                        if (validateConnection && body.certificateInfo != null) {
                            int statusCode;
                            int errorCode;
                            String message;
                            if (body.taskInfo.failure != null) {
                                statusCode = body.taskInfo.failure.statusCode;
                                errorCode = body.taskInfo.failure.getErrorCode();
                                message = body.taskInfo.failure.message;
                            } else {
                                statusCode = HttpURLConnection.HTTP_UNAVAILABLE;
                                errorCode = CertificateInfoServiceErrorResponse.ERROR_CODE_CERTIFICATE_MASK;
                                message = "Unknown issue with certificate validation.";
                            }
                            CertificateInfoServiceErrorResponse errorResponse = CertificateInfoServiceErrorResponse
                                    .create(
                                            body.certificateInfo, statusCode, errorCode, message);
                            put.setBody(errorResponse);
                            put.setStatusCode(HttpURLConnection.HTTP_OK);
                            put.complete();
                        } else {
                            handleServiceErrorResponse(put, o.getStatusCode(), e,
                                    body.taskInfo.failure);
                        }
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

        final EndpointRemovalTaskState endpointRemovalTask = new EndpointRemovalTaskState();
        endpointRemovalTask.endpointLink = endpointLink;
        endpointRemovalTask.taskInfo = new TaskState();
        endpointRemovalTask.taskInfo.isDirect = true;
        endpointRemovalTask.options = EnumSet.noneOf(TaskOption.class);
        if (DeploymentProfileConfig.getInstance().isTest()) {
            endpointRemovalTask.options.add(TaskOption.IS_MOCK);
        }

        Operation.createPost(this, EndpointRemovalTaskService.FACTORY_LINK)
                .setBody(endpointRemovalTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        handleException(delete, "deleting", endpointLink, o.getStatusCode(), e);
                        return;
                    }
                    EndpointRemovalTaskState body = o.getBody(EndpointRemovalTaskState.class);
                    if (body.taskInfo.stage == TaskStage.FAILED) {
                        handleServiceErrorResponse(delete, o.getStatusCode(), e,
                                body.taskInfo.failure);
                        return;
                    }
                    delete.complete();
                })
                .sendWith(this);

        deleteStatsCollectionScheduledTask(endpointLink);
        deletePrivateImageEnumerationScheduledTask(endpointLink);
    }

    private EndpointState validateState(Operation op) {
        if (!op.hasBody()) {
            throw new LocalizableValidationException("Body is required",
                    "compute.endpoint.adapter.body.required");
        }

        EndpointState state = op.getBody(EndpointState.class);

        if (state.endpointType == null) {
            throw new LocalizableValidationException("Endpoint type is required",
                    "compute.endpoint.adapter.enpoint.type.required");
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
        if (e != null) {
            logWarning(Utils.toString(e));
        }
        if (rsp != null) {
            logWarning("kind: %s, message: %s, errorCode: %s, statusCode: %s.",
                    rsp.documentKind, rsp.message, rsp.getErrorCode(), rsp.statusCode);
        }
        op.setStatusCode(statusCode);
        op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
        op.fail(e, rsp);
    }

    private DeferredResult<Operation> triggerStatsCollection(EndpointAllocationTaskState currentState) {

        EndpointState endpoint = currentState.endpointState;


        long intervalMicros = currentState.enumerationRequest.refreshIntervalMicros != null
                ? currentState.enumerationRequest.refreshIntervalMicros
                : DEFAULT_SCHEDULED_STATS_COLLECTION_INTERVAL;

        // The stats-collection task to be scheduled
        final StatsCollectionTaskState statsCollectionTask = new StatsCollectionTaskState();
        statsCollectionTask.resourcePoolLink = endpoint.resourcePoolLink;
        statsCollectionTask.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        if (DeploymentProfileConfig.getInstance().isTest()) {
            statsCollectionTask.options.add(TaskOption.IS_MOCK);
        }

        // Create additional Query clause
        List<Query> queries = Arrays.asList(
                Query.Builder.create(Occurance.MUST_OCCUR)
                        .setTerm(ComputeState.FIELD_NAME_TYPE, ComputeType.VM_HOST.name()).build());

        statsCollectionTask.customizationClauses = queries;

        // The scheduled task that should trigger the stats-collection task
        final ScheduledTaskState scheduledTaskState = new ScheduledTaskState();

        // Use stable id
        scheduledTaskState.documentSelfLink = statsCollectionId(endpoint.documentSelfLink);

        scheduledTaskState.factoryLink = StatsCollectionTaskService.FACTORY_LINK;
        scheduledTaskState.initialStateJson = Utils.toJson(statsCollectionTask);
        scheduledTaskState.intervalMicros = intervalMicros;
        scheduledTaskState.delayMicros = currentState.enumerationRequest.delayMicros;

        scheduledTaskState.tenantLinks = endpoint.tenantLinks;
        scheduledTaskState.customProperties = Collections.singletonMap(
                ComputeProperties.ENDPOINT_LINK_PROP_NAME,
                endpoint.documentSelfLink);

        return sendWithDeferredResult(
                Operation.createPost(this, ScheduledTaskService.FACTORY_LINK)
                        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                        .setBody(scheduledTaskState))
                .whenComplete((o, e) -> {
                    if (e != null) {
                        logWarning(
                                "Error triggering stats collection task for endpoint [%s], reason: %s",
                                endpoint.documentSelfLink, Utils.toString(e));
                    } else {
                        logInfo("Stats collection has been scheduled for endpoint [%s]",
                                endpoint.documentSelfLink);
                    }
                });
    }

    /**
     * Delete scheduled stats-collection task.
     */
    private void deleteStatsCollectionScheduledTask(String endpointLink) {
        String uri = UriUtils.buildUriPath(
                ScheduledTaskService.FACTORY_LINK,
                statsCollectionId(endpointLink));

        Operation.createDelete(this, uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logInfo("Unable to delete scheduled stats collection task for endpoint [%s], reason: %s",
                                endpointLink, Utils.toString(e));
                    }
                })
                .sendWith(this);
    }

    /**
     * Create end-point specific stats-collection id, by analogy with {#code
     * EndpointAllocationTaskService}.
     */
    static String statsCollectionId(String endpointLink) {

        final String statsCollectionSuffix = "-stats-collection";

        // Append suffix to avoid duplication with enumeration scheduler.
        return UriUtils.getLastPathSegment(endpointLink).concat(statsCollectionSuffix);
    }

    private DeferredResult<Operation> triggerPublicImageEnumeration(EndpointAllocationTaskState endpointAllocationTask) {

        final EndpointState endpoint = endpointAllocationTask.endpointState;

        if (endpoint.endpointProperties == null) {
            // Assume Public image enum is not supported by the end-point
            return DeferredResult.completed(null);
        }

        final String supportPublicImages = endpoint.endpointProperties.getOrDefault(
                EndpointConfigRequest.SUPPORT_PUBLIC_IMAGES,
                Boolean.FALSE.toString());

        if (!Boolean.valueOf(supportPublicImages)) {
            // Public image enum is not supported by the end-point
            return DeferredResult.completed(null);
        }

        final String regionId = endpoint.endpointProperties.get(EndpointConfigRequest.REGION_KEY);

        // Use endpointType-regionId pair as stable id for scheduled task!
        final String scheduledTaskId = publicImagesEnumerationId(
                endpoint.endpointType, regionId);

        // The image-enum task to be scheduled
        final ImageEnumerationTaskState imageEnumTask = new ImageEnumerationTaskState();
        // Setting 'endpointType' and optionally 'regionId' implies PUBLIC images enumeration {{
        imageEnumTask.endpointType = endpoint.endpointType;
        imageEnumTask.regionId = regionId;
        // }}
        // All images are considered global so NO tenants are set
        imageEnumTask.tenantLinks = null;
        imageEnumTask.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        if (DeploymentProfileConfig.getInstance().isTest()) {
            imageEnumTask.options.add(TaskOption.IS_MOCK);
        }
        /*
        // Store a link to the scheduled task that created this task
        imageEnumTask.customProperties = Collections.singletonMap(
                ComputeProperties.CREATE_CONTEXT_PROP_NAME,
                UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, scheduledTaskId));
         */

        // The scheduled task that should trigger the image-enum task
        final ScheduledTaskState scheduledTask = new ScheduledTaskState();
        scheduledTask.factoryLink = ImageEnumerationTaskService.FACTORY_LINK;
        scheduledTask.initialStateJson = Utils.toJson(imageEnumTask);
        scheduledTask.intervalMicros = DEFAULT_SCHEDULED_IMAGE_ENUM_INTERVAL;
        scheduledTask.delayMicros = DEFAULT_SCHEDULED_TASK_DELAY;

        // Use stable id
        scheduledTask.documentSelfLink = scheduledTaskId;
        // All images are considered global so NO tenants are set
        scheduledTask.tenantLinks = null;

        return sendWithDeferredResult(
                Operation.createPost(this, ScheduledTaskService.FACTORY_LINK)
                        .setBody(scheduledTask)
                        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE))
                .whenComplete((o, e) -> {
                    String msg = "Scheduling Public image-enumeration for '%s' endpoint type";
                    if (e != null) {
                        if (e instanceof ServiceAlreadyStartedException) {
                            logInfo(() -> String.format(msg + ": SUCCESS - already started",
                                    scheduledTaskId));
                        } else {
                            logWarning(() -> String.format(msg + ": ERROR - %s",
                                    scheduledTaskId, Utils.toString(e)));
                        }
                    } else {
                        logInfo(() -> String.format(msg + ": SUCCESS", scheduledTaskId));
                    }
                });
    }

    private DeferredResult<Operation> triggerPrivateImageEnumeration(
            EndpointAllocationTaskState endpointAllocationTask) {

        final EndpointState endpoint = endpointAllocationTask.endpointState;

        // Use end-point id as stable id for scheduled task!
        final String scheduledTaskId = privateImagesEnumerationId(endpoint.documentSelfLink);

        // The image-enum task to be scheduled
        final ImageEnumerationTaskState imageEnumTask = new ImageEnumerationTaskState();
        // Setting 'endpointLink' implies PRIVATE images enumeration
        imageEnumTask.endpointLink = endpoint.documentSelfLink;
        imageEnumTask.tenantLinks = endpoint.tenantLinks;
        imageEnumTask.options = EnumSet.of(TaskOption.SELF_DELETE_ON_COMPLETION);
        if (DeploymentProfileConfig.getInstance().isTest()) {
            imageEnumTask.options.add(TaskOption.IS_MOCK);
        }
        /*
        // Store a link to the scheduled task that created this task
        imageEnumTask.customProperties = Collections.singletonMap(
                ComputeProperties.CREATE_CONTEXT_PROP_NAME,
                UriUtils.buildUriPath(ScheduledTaskService.FACTORY_LINK, scheduledTaskId));
         */

        // The scheduled task that should trigger the image-enum task
        final ScheduledTaskState scheduledTask = new ScheduledTaskState();
        scheduledTask.factoryLink = ImageEnumerationTaskService.FACTORY_LINK;
        scheduledTask.initialStateJson = Utils.toJson(imageEnumTask);
        scheduledTask.intervalMicros = DEFAULT_SCHEDULED_IMAGE_ENUM_INTERVAL;
        scheduledTask.delayMicros = DEFAULT_SCHEDULED_TASK_DELAY;

        // Use stable id
        scheduledTask.documentSelfLink = scheduledTaskId;
        scheduledTask.tenantLinks = endpoint.tenantLinks;

        return sendWithDeferredResult(
                Operation.createPost(this, ScheduledTaskService.FACTORY_LINK)
                    .setBody(scheduledTask)
                    .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE))
                .whenComplete((o, e) -> {
                    String msg = "Scheduling Private image-enumeration for '%s' endpoint";
                    if (e != null) {
                        logWarning(() -> String.format(msg + ": ERROR - %s",
                                endpoint.name, Utils.toString(e)));
                    } else {
                        logInfo(() -> String.format(msg + ": SUCCESS", endpoint.name));
                    }
                });
    }

    /**
     * Delete scheduled private image-enumeration task.
     */
    private void deletePrivateImageEnumerationScheduledTask(String endpointLink) {
        String uri = UriUtils.buildUriPath(
                ScheduledTaskService.FACTORY_LINK,
                privateImagesEnumerationId(endpointLink));

        Operation.createDelete(this, uri)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning(
                                "Unable to delete scheduled private image-enumeration task for endpoint [%s], reason: %s",
                                endpointLink, Utils.toString(e));
                    }
                })
                .sendWith(this);
    }

    /**
     * Create end-point specific PRIVATE images enumeration id, by analogy with {#code
     * EndpointAllocationTaskService}.
     */
    static String privateImagesEnumerationId(String endpointLink) {

        final String imageEnumerationSuffix = "-image-enumeration";

        // Append suffix to avoid duplication with enumeration scheduler.
        return UriUtils.getLastPathSegment(endpointLink).concat(imageEnumerationSuffix);
    }

    /**
     * Create end-point type specific PUBLIC images enumeration id, by analogy with {#code
     * EndpointAllocationTaskService}.
     */
    static String publicImagesEnumerationId(String endpointType, String regionId) {

        // Use endpointType-regionId pair as stable id.

        String stableId = endpointType;

        if (regionId != null) {
            stableId += "-" + regionId;
        }

        return stableId;
    }

}
