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

package com.vmware.photon.controller.model.tasks;

import static com.vmware.photon.controller.model.util.AssertUtil.assertTrue;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.EXPAND;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.FIXED_ITEM_NAME;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINKS;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;
import static com.vmware.xenon.common.UriUtils.buildUri;
import static com.vmware.xenon.common.UriUtils.buildUriPath;
import static io.netty.util.internal.StringUtil.isNullOrEmpty;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer.Since;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.UriPaths.AdapterTypePath;
import com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest;
import com.vmware.photon.controller.model.adapterapi.EnumerationAction;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest;
import com.vmware.photon.controller.model.adapterapi.ImageEnumerateRequest.ImageEnumerateRequestType;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService;
import com.vmware.photon.controller.model.adapters.registry.PhotonModelAdaptersRegistryService.PhotonModelAdapterConfig;
import com.vmware.photon.controller.model.constants.PhotonModelConstants;
import com.vmware.photon.controller.model.constants.ReleaseConstants;
import com.vmware.photon.controller.model.query.QueryStrategy;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ImageService.ImageState;
import com.vmware.photon.controller.model.resources.util.PhotonModelUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;
import com.vmware.xenon.services.common.ServiceUriPaths;
import com.vmware.xenon.services.common.TaskFactoryService;
import com.vmware.xenon.services.common.TaskService;

/**
 * Task used to enumerate images on a given end-point.
 */
public class ImageEnumerationTaskService
        extends TaskService<ImageEnumerationTaskService.ImageEnumerationTaskState> {

    public static final String FACTORY_LINK = UriPaths.PROVISIONING + "/image-enumeration-tasks";

    public static FactoryService createFactory() {
        TaskFactoryService fs = new TaskFactoryService(ImageEnumerationTaskState.class) {
            @Override
            public Service createServiceInstance() throws Throwable {
                return new ImageEnumerationTaskService();
            }
        };
        fs.setPeerNodeSelectorPath(ServiceUriPaths.DEFAULT_1X_NODE_SELECTOR);
        return fs;
    }

    /**
     * This class defines the task state associated with a single
     * {@link ImageEnumerationTaskService} instance.
     */
    public static class ImageEnumerationTaskState extends TaskService.TaskServiceState {

        public static final String FIELD_NAME_ENDPOINT_LINK = PhotonModelConstants.FIELD_NAME_ENDPOINT_LINK;
        public static final String FIELD_NAME_ENDPOINT_TYPE = "endpointType";
        public static final String FIELD_NAME_REGION_ID = "regionId";

        @Documentation(description = "Optional type of the end-points for which public"
                + " (global for all endpoints of this type) images enumeration"
                + " should be triggered. Set either this property or endpointLink.")
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public String endpointType;

        @Documentation(description = "Optional identifier of the region for which public images"
                + " enumeration should be triggered. Only applicable with endpointType property.")
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public String regionId;

        @Documentation(description = "Optional link of the end-point for which private"
                + " (specific just for this endpoint) images enumeration"
                + " should be triggered. Set either this property or endpointType.")
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT, LINK }, indexing = STORE_ONLY)
        public String endpointLink;

        @Documentation(description = "Optional type of image enumeration: start, stop, refresh.")
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public EnumerationAction enumerationAction;

        @Documentation(description = "Optional list of tenants that can access this task.")
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT, LINKS }, indexing = STORE_ONLY)
        public List<String> tenantLinks;

        @Documentation(description = "Options used to configure specific aspects of"
                + " this task execution.")
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public EnumSet<TaskOption> options;

        @Documentation(description = "Custom properties associated with the task.")
        @PropertyOptions(usage = { OPTIONAL }, indexing = { EXPAND, FIXED_ITEM_NAME })
        @Since(ReleaseConstants.RELEASE_VERSION_0_6_13)
        public Map<String, String> customProperties;

        /**
         * Optional end-point specific filter that might be used by image enumeration adapter to
         * limit the number of images to enumerate.
         *
         * <p>
         * Note: for internal use only, by tests for example.
         */
        @PropertyOptions(usage = { OPTIONAL, SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public String filter;

        /**
         * Setting {@code #endpointType} indicates Public images enumeration.
         */
        public final boolean enumeratePublicImages() {
            return !isNullOrEmpty(this.endpointType);
        }

        /**
         * Setting {@code #endpointLink} indicates Private images enumeration.
         */
        public final boolean enumeratePrivateImages() {
            return !isNullOrEmpty(this.endpointLink);
        }
    }

    /**
     * Defaults to expire a task instance if not completed in 30 mins.
     */
    public static final long DEFAULT_EXPIRATION_MINUTES = 30;

    public ImageEnumerationTaskService() {
        super(ImageEnumerationTaskState.class);
        super.toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation startOp) {
        try {
            ImageEnumerationTaskState taskState = validateStartPost(startOp);
            if (taskState == null) {
                return;
            }

            initializeState(taskState, startOp);

            // Send completion to the caller (with a CREATED state)
            startOp.setBody(taskState).complete();

            // And then start internal state machine
            sendSelfPatch(taskState, TaskStage.STARTED, null);

        } catch (Throwable e) {
            startOp.fail(e);
        }
    }

    /**
     * Customize the validation logic that's part of initial {@code POST} creating the task service.
     *
     * @see #handleStart(Operation)
     */
    @Override
    protected ImageEnumerationTaskState validateStartPost(Operation startOp) {

        // NOTE: This MUST go first!
        // Delegate to default/parent validation
        ImageEnumerationTaskState taskState = super.validateStartPost(startOp);

        // Do annotation based validation
        if (taskState != null) {
            try {
                // Default validation
                Utils.validateState(getStateDescription(), taskState);

                if (!taskState.enumeratePrivateImages() && !taskState.enumeratePublicImages()) {
                    throw new IllegalArgumentException(
                            "Either " + ImageEnumerationTaskState.class.getSimpleName()
                                    + "." + ImageEnumerationTaskState.FIELD_NAME_ENDPOINT_TYPE +
                                    " or " + ImageEnumerationTaskState.class.getSimpleName()
                                    + "." + ImageEnumerationTaskState.FIELD_NAME_ENDPOINT_LINK +
                                    " must be set.");
                }
                if (taskState.enumeratePrivateImages() && taskState.enumeratePublicImages()) {
                    throw new IllegalArgumentException(
                            "Both " + ImageEnumerationTaskState.class.getSimpleName()
                                    + "." + ImageEnumerationTaskState.FIELD_NAME_ENDPOINT_TYPE +
                                    " and " + ImageEnumerationTaskState.class.getSimpleName()
                                    + "." + ImageEnumerationTaskState.FIELD_NAME_ENDPOINT_LINK +
                                    " cannot be set.");
                }

                if (!isNullOrEmpty(taskState.regionId)) {
                    assertTrue(
                            taskState.enumeratePublicImages(),
                            ImageEnumerationTaskState.class.getSimpleName()
                                    + "." + ImageEnumerationTaskState.FIELD_NAME_REGION_ID +
                                    " must be used in conjunction with "
                                    + ImageEnumerationTaskState.class.getSimpleName()
                                    + "." + ImageEnumerationTaskState.FIELD_NAME_ENDPOINT_TYPE
                                    + ".");
                }
            } catch (Throwable t) {
                startOp.fail(t);
                taskState = null;
            }
        }
        return taskState;
    }

    /**
     * Customize the initialization logic (set the task with default values) that's part of initial
     * {@code POST} creating the task service.
     *
     * @see #handleStart(Operation)
     */
    @Override
    protected void initializeState(ImageEnumerationTaskState startState, Operation startOp) {

        if (startState.options == null) {
            startState.options = EnumSet.noneOf(TaskOption.class);
        }

        if (startState.taskInfo == null || startState.taskInfo.stage == null) {
            startState.taskInfo = new TaskState();
            startState.taskInfo.stage = TaskState.TaskStage.CREATED;
        }

        if (startState.documentExpirationTimeMicros <= 0) {
            setExpiration(startState, DEFAULT_EXPIRATION_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Override
    public void handlePut(Operation putOp) {
        PhotonModelUtils.handleIdempotentPut(this, putOp);
    }

    @Override
    public void handlePatch(Operation patchOp) {

        ImageEnumerationTaskState currentState = getState(patchOp);
        ImageEnumerationTaskState patchState = getBody(patchOp);

        try {
            if (!validateTransition(patchOp, currentState, patchState)) {
                return;
            }

            logFine(() -> String.format("Moving from %s to %s (referrer: %s)",
                    currentState.taskInfo.stage,
                    patchState.taskInfo.stage,
                    patchOp.getReferer().getPath()));

            updateState(currentState, patchState);
            patchOp.complete();

            switch (patchState.taskInfo.stage) {
            case STARTED:
                sendImageEnumerationAdapterRequest(currentState);
                break;
            case FINISHED:
                handleTaskCompleted(currentState);
                break;
            case FAILED:
            case CANCELLED:
                logSevere(() -> String.format("%s with [%s]",
                        currentState.taskInfo.stage,
                        getFailureMessage(currentState)));
                handleTaskCompleted(currentState);
                break;
            default:
                logWarning(() -> String.format("unknown stage: %s", currentState.taskInfo.stage));
                break;
            }
        } catch (Throwable e) {
            if (TaskState.isInProgress(currentState.taskInfo)) {
                sendSelfFailurePatch(currentState, e.getMessage());
            } else {
                logSevere(() -> String.format("An error occurred on stage %s: %s",
                        currentState.taskInfo.stage, Utils.toString(e)));
            }
        }
    }

    private static String getFailureMessage(ImageEnumerationTaskState taskState) {
        if (taskState.failureMessage != null && !taskState.failureMessage.isEmpty()) {
            return taskState.failureMessage;
        }
        if (taskState.taskInfo.failure != null
                && taskState.taskInfo.failure.message != null
                && !taskState.taskInfo.failure.message.isEmpty()) {
            return taskState.taskInfo.failure.message;
        }
        return taskState.taskInfo.stage.name();
    }

    /**
     * Customize the validation transition logic that's part of {@code PATCH} request.
     */
    @Override
    protected boolean validateTransition(
            Operation patchOp,
            ImageEnumerationTaskState currentState,
            ImageEnumerationTaskState patchState) {

        boolean ok = super.validateTransition(patchOp, currentState, patchState);

        if (ok) {
            if (currentState.taskInfo.stage == TaskStage.STARTED
                    && patchState.taskInfo.stage == TaskStage.STARTED) {
                patchOp.fail(new IllegalArgumentException("Cannot start task again"));
                return false;
            }
        }

        return ok;
    }

    /**
     * Self delete this task if specified through {@link TaskOption#SELF_DELETE_ON_COMPLETION}
     * option.
     */
    private void handleTaskCompleted(ImageEnumerationTaskState taskState) {
        if (taskState.options.contains(TaskOption.SELF_DELETE_ON_COMPLETION)) {
            sendWithDeferredResult(Operation.createDelete(getUri()))
                    .whenComplete((o, e) -> {
                        if (e != null) {
                            logSevere(
                                    () -> String.format("Self-delete upon %s stage: FAILED with %s",
                                            taskState.taskInfo.stage, Utils.toString(e)));
                        } else {
                            logFine(() -> String.format("Self-delete upon %s stage: SUCCESS",
                                    taskState.taskInfo.stage));
                        }
                    });
        }
    }

    /**
     * Delegate image enumeration to the adapter.
     */
    private void sendImageEnumerationAdapterRequest(ImageEnumerationTaskState taskState) {

        SendImageEnumerationAdapterContext context = new SendImageEnumerationAdapterContext();
        context.taskState = taskState;

        DeferredResult.completed(context)
                // get the EndpointState from ImageEnumerationTaskState#endpointLink
                .thenCompose(this::getEndpointState)

                .thenCompose(ctx -> {
                    if (ctx.endpointState == null) {
                        // If no end-points are found then delete all public images of this EP type
                        logFine(() -> String.format(
                                "NO '%s' endpoints found so skip the enumeration and delete Public images",
                                context.taskState.endpointType));

                        return DeferredResult.completed(ctx)
                                .thenCompose(this::deletePublicImagesByEndpointType)
                                .thenCompose(this::sendSelfFinishedPatchDR);
                    }

                    return DeferredResult.completed(ctx)
                            // get the 'image-enumeration' URI for passed end-point
                            .thenCompose(this::getImageEnumerationAdapterReference)
                            // call 'image-enumeration' adapter
                            .thenCompose(this::callImageEnumerationAdapter);
                })

                .whenComplete((op, exc) -> {
                    if (exc != null) {
                        // If any of above steps failed sendSelfFailurePatch
                        logFine(() -> String.format(
                                "FAILED: sendImageEnumerationAdapterRequest - %s",
                                Utils.toString(exc.getCause())));

                        sendSelfFailurePatch(taskState, exc.getCause().getMessage());
                    } else {
                        logFine("SUCCESS: sendImageEnumerationAdapterRequest");
                    }
                });
    }

    /**
     * Get the {@link EndpointState} either by type or by link.
     */
    private DeferredResult<SendImageEnumerationAdapterContext> getEndpointState(
            SendImageEnumerationAdapterContext ctx) {

        if (ctx.taskState.enumeratePrivateImages()) {

            return getEndpointStateByLink(ctx);
        }

        if (ctx.taskState.enumeratePublicImages()) {

            return getEndpointStateByType(ctx);
        }

        // This MUST not happen due to task-state validation.
        return null;
    }

    /**
     * Resolve specific End-point by link.
     */
    private DeferredResult<SendImageEnumerationAdapterContext> getEndpointStateByLink(
            SendImageEnumerationAdapterContext ctx) {

        Operation op = Operation.createGet(this, ctx.taskState.endpointLink);

        return sendWithDeferredResult(op, EndpointState.class).thenApply(epState -> {

            ctx.endpointState = epState;

            logFine(() -> String.format(
                    "SUCCESS: '%s' EndpointState of '%s' type",
                    ctx.endpointState.name,
                    ctx.endpointState.endpointType));

            return ctx;
        });
    }

    /**
     * Pick arbitrary End-point of passed end-point type and optionally region.
     */
    private DeferredResult<SendImageEnumerationAdapterContext> getEndpointStateByType(
            SendImageEnumerationAdapterContext ctx) {

        QueryStrategy<EndpointState> queryEndpointsByType = new QueryTop<>(
                getHost(),
                endpointsByTypeQuery(ctx).build(),
                EndpointState.class,
                null).setMaxResultsLimit(1);

        return queryEndpointsByType.collectDocuments(Collectors.toList()).thenApply(epStates -> {

            final String regionStr = isNullOrEmpty(ctx.taskState.regionId)
                    ? "n/a"
                    : ctx.taskState.regionId;

            Optional<EndpointState> epState = epStates.stream().findFirst();

            if (epState.isPresent()) {
                ctx.endpointState = epState.get();

                logFine(() -> String.format(
                        "SUCCESS: '%s' EndpointState for (type=%s,region=%s)",
                        ctx.endpointState.name,
                        ctx.taskState.endpointType,
                        regionStr));
            } else {
                logInfo(() -> String.format(
                        "SUCCESS: NO EndpointState for (type=%s,region=%s)",
                        ctx.taskState.endpointType,
                        regionStr));
            }

            return ctx;
        });
    }

    /**
     * Go to {@link PhotonModelAdaptersRegistryService Service Registry} and get the
     * 'image-enumeration' URI for passed end-point.
     *
     * @return <code>null</code> is returned if 'image-enumeration' adapter is not registered by
     *         passed end-point.
     *
     * @see PhotonModelAdapterConfig
     * @see AdapterTypePath#IMAGE_ENUMERATION_ADAPTER
     */
    private DeferredResult<SendImageEnumerationAdapterContext> getImageEnumerationAdapterReference(
            SendImageEnumerationAdapterContext ctx) {

        // We use 'endpointType' (such as aws, azure) as AdapterConfig id/selfLink!
        String uri = buildUriPath(
                PhotonModelAdaptersRegistryService.FACTORY_LINK, ctx.endpointState.endpointType);

        Operation getEndpointConfigOp = Operation.createGet(this, uri);

        return sendWithDeferredResult(getEndpointConfigOp, PhotonModelAdapterConfig.class)
                .thenApply(endpointConfig -> {
                    // Lookup the 'image-enumeration' URI for passed end-point
                    if (endpointConfig.adapterEndpoints != null) {

                        String uriStr = endpointConfig.adapterEndpoints.get(
                                AdapterTypePath.IMAGE_ENUMERATION_ADAPTER.key);

                        if (uriStr != null && !uriStr.isEmpty()) {
                            ctx.adapterRef = URI.create(uriStr);
                        }
                    }

                    logFine(() -> String.format("SUCCESS: getImageEnumerationAdapterReference [%s]",
                            ctx.adapterRef));

                    return ctx;
                });
    }

    /**
     * Call 'image-enumeration' adapter if registered by the end-point OR fail if not registered.
     */
    private DeferredResult<SendImageEnumerationAdapterContext> callImageEnumerationAdapter(
            SendImageEnumerationAdapterContext ctx) {

        if (ctx.adapterRef == null) {
            // No 'image-enumeration' URI registered for passed end-point
            return DeferredResult.failed(new IllegalStateException(
                    String.format("No '%s' URI registered by '%s' end-point.",
                            AdapterTypePath.IMAGE_ENUMERATION_ADAPTER.key,
                            ctx.taskState.endpointType)));
        }

        // Create 'image-enumeration' adapter request
        final ImageEnumerateRequest adapterReq = new ImageEnumerateRequest();

        // Set ImageEnumerateRequest specific params
        adapterReq.enumerationAction = ctx.taskState.enumerationAction;
        if (ctx.taskState.enumeratePrivateImages()) {
            adapterReq.requestType = ImageEnumerateRequestType.PRIVATE;
        } else if (ctx.taskState.enumeratePublicImages()) {
            adapterReq.requestType = ImageEnumerateRequestType.PUBLIC;
        }

        // Set generic ResourceRequest params

        // The end-point is ALWAYS set regardless of Private/Public enum type
        // In case of Public, end-point credentials are used to run the enumeration
        adapterReq.resourceReference = buildUri(getHost(), ctx.endpointState.documentSelfLink);
        adapterReq.taskReference = buildUri(getHost(), ctx.taskState.documentSelfLink);
        adapterReq.isMockRequest = ctx.taskState.options.contains(TaskOption.IS_MOCK);

        Operation callAdapterOp = Operation.createPatch(ctx.adapterRef).setBody(adapterReq);

        return sendWithDeferredResult(callAdapterOp).thenApply(op -> {

            logFine(() -> String.format("SUCCESS: callImageEnumerationAdapter [%s]",
                    op.getUri()));

            return ctx;
        });
    }

    private Query.Builder endpointsByTypeQuery(SendImageEnumerationAdapterContext ctx) {

        Query.Builder endpointsByTypeQuery = Query.Builder.create()
                .addKindFieldClause(EndpointState.class)
                .addCaseInsensitiveFieldClause(
                        EndpointState.FIELD_NAME_ENDPOINT_TYPE,
                        ctx.taskState.endpointType,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR);

        if (!isNullOrEmpty(ctx.taskState.regionId)) {
            endpointsByTypeQuery.addCompositeFieldClause(
                    EndpointState.FIELD_NAME_ENDPOINT_PROPERTIES,
                    EndpointConfigRequest.REGION_KEY,
                    ctx.taskState.regionId);
        }

        return endpointsByTypeQuery;
    }

    private Query.Builder publicImagesByEndpointTypeQuery(SendImageEnumerationAdapterContext ctx) {

        Query.Builder imagesByEndpointsTypeQuery = Query.Builder.create()
                .addKindFieldClause(ImageState.class)
                .addCaseInsensitiveFieldClause(
                        ImageState.FIELD_NAME_ENDPOINT_TYPE,
                        ctx.taskState.endpointType,
                        MatchType.TERM,
                        Occurance.MUST_OCCUR);

        if (!isNullOrEmpty(ctx.taskState.regionId)) {
            imagesByEndpointsTypeQuery.addFieldClause(
                    ImageState.FIELD_NAME_REGION_ID,
                    ctx.taskState.regionId);
        }

        return imagesByEndpointsTypeQuery;
    }

    private DeferredResult<SendImageEnumerationAdapterContext> deletePublicImagesByEndpointType(
            SendImageEnumerationAdapterContext ctx) {

        QueryByPages<ImageState> queryAll = new QueryByPages<ImageState>(
                getHost(),
                publicImagesByEndpointTypeQuery(ctx).build(),
                ImageState.class,
                null /* tenants */);
        queryAll.setMaxPageSize(QueryUtils.DEFAULT_MAX_RESULT_LIMIT);

        final List<DeferredResult<Operation>> deleteDRs = new ArrayList<>();

        queryAll.queryLinks(imageLink -> {
            Operation delOp = Operation.createDelete(UriUtils.buildUri(getHost(), imageLink));

            DeferredResult<Operation> delDR = sendWithDeferredResult(delOp)
                    .whenComplete((op, e) -> {
                        final String msg = "Deleting '%s' public image state [%s]";
                        if (e != null) {
                            // Be tolerant on individual images delete
                            log(Level.WARNING, () -> String.format(msg + ": FAILED with %s",
                                    ctx.taskState.endpointType, imageLink, Utils.toString(e)));
                        } else {
                            log(Level.FINEST, () -> String.format(msg + ": SUCCESS",
                                    ctx.taskState.endpointType, imageLink));
                        }
                    });

            deleteDRs.add(delDR);
        });

        return DeferredResult.allOf(deleteDRs).thenApply(ignore -> ctx);
    }

    private DeferredResult<SendImageEnumerationAdapterContext> sendSelfFinishedPatchDR(
            SendImageEnumerationAdapterContext ctx) {

        sendSelfFinishedPatch(ctx.taskState);
        return DeferredResult.completed(ctx);
    }

    /**
     * @see ImageEnumerationTaskService#sendImageEnumerationAdapterRequest(ImageEnumerationTaskState)
     */
    private static class SendImageEnumerationAdapterContext {

        ImageEnumerationTaskState taskState;

        EndpointState endpointState;

        URI adapterRef;

    }
}
