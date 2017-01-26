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

package com.vmware.admiral.request;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.OPTIONAL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.closures.services.closure.ClosureFactoryService;
import com.vmware.admiral.closures.services.closuredescription.ClosureDescription;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.request.ClosureAllocationTaskService.ClosureAllocationTaskState.SubStage;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ClosureAllocationTaskService extends
        AbstractTaskStatefulService<ClosureAllocationTaskService.ClosureAllocationTaskState, ClosureAllocationTaskService.ClosureAllocationTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Closure Execution";

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_CLOSURE_ALLOCATION_TASKS;

    // cached closure description
    private volatile ClosureDescription closureDescription;

    protected static class CallbackCompleteResponse
            extends ServiceTaskCallback.ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public static class ClosureAllocationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ClosureAllocationTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            CONTEXT_PREPARED,
            COMPLETED,
            ERROR;
        }

        @Documentation(description = "The description that defines the closure description.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        @Documentation(description = "The description that defines the closure.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String closureLink;

        /** Set by a Task with the links of the provisioned resources. */
        @Documentation(description = "Set by a Task with the links of the provisioned resources.")
        @PropertyOptions(usage = { OPTIONAL, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /** (Internal) Set by task with ClosureDescription name. */
        @Documentation(description = "Set by task with ClosureDescription name.")
        @PropertyOptions(indexing = ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY, usage = {
                ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE,
                ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT,
                ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String descName;

    }

    public ClosureAllocationTaskService() {
        super(ClosureAllocationTaskState.class, ClosureAllocationTaskState.SubStage.class,
                DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void validateStateOnStart(ClosureAllocationTaskState state)
            throws IllegalArgumentException {
    }

    @Override
    protected TaskStatusState fromTask(
            TaskServiceDocument<ClosureAllocationTaskState.SubStage> state) {
        final TaskStatusState statusTask = super.fromTask(state);
        statusTask.name = ((ClosureAllocationTaskState) state).descName;

        return statusTask;
    }

    @Override
    protected ServiceTaskCallback.ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ClosureAllocationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return finishedResponse;
    }

    private void prepareContext(ClosureAllocationTaskState state, ClosureDescription
            closureDescription) {
        assertNotNull(state, "state");

        if (closureDescription == null) {
            getClosureDescription(state,
                    (closureDesc) -> this.prepareContext(state, closureDesc));
            return;
        }

        assertNotNull(closureDescription, "closureDescription");
        proceedTo(ClosureAllocationTaskState.SubStage.CONTEXT_PREPARED,
                s -> {
                    // merge request/allocation properties over the custom description properties
                    state.customProperties = mergeProperty(closureDescription.customProperties,
                            state.customProperties);

                    if (state.getCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY) == null) {
                        state.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY,
                                getSelfId());
                    }
                    state.descName = closureDescription.name;
                });
    }

    @Override
    protected void handleStartedStagePatch(ClosureAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, null);
            break;
        case CONTEXT_PREPARED:
            createClosure(state);
            break;
        case COMPLETED:
            complete((s) -> {
                s.resourceLinks = buildResourceLinks(state);
            });
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    private boolean createRequestTrackerIfNoneProvided(ClosureAllocationTaskState state,
            Operation op) {
        if (state.requestTrackerLink != null && !state.requestTrackerLink.isEmpty()) {
            logFine("Request tracker link provided: %s", state.requestTrackerLink);
            return false;
        }
        RequestStatusService.RequestStatus requestStatus = fromTask(
                new RequestStatusService.RequestStatus(), state);
        requestStatus.addTrackedTasks(DISPLAY_NAME);

        sendRequest(Operation.createPost(this, RequestStatusFactoryService.SELF_LINK)
                .setBody(requestStatus)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failed to create request tracker for: "
                                + state.documentSelfLink, e);
                        op.fail(e);
                        return;
                    }
                    logFine("Created request tracker: %s", requestStatus.documentSelfLink);
                    state.requestTrackerLink = o
                            .getBody(RequestStatusService.RequestStatus.class).documentSelfLink;
                    op.complete(); /* complete the original start operation */
                }));
        return true;// don't complete the start operation
    }

    private void createClosure(ClosureAllocationTaskState state) {
        Closure closureState = new Closure();
        closureState.descriptionLink = state.resourceDescriptionLink;
        String contextId = state.getCustomProperty(FIELD_NAME_CONTEXT_ID_KEY);
        closureState.customProperties = new HashMap<>(2);
        closureState.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        closureState.customProperties.put(ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY,
                UriUtils.buildUriPath(CompositeComponentFactoryService.SELF_LINK, contextId));
        closureState.tenantLinks = state.tenantLinks;

        sendRequest(Operation.createPost(this, ClosureFactoryService.FACTORY_LINK)
                .setBody(closureState)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failed to create closure: %s", Utils.toString(ex));
                        proceedTo(SubStage.ERROR);
                        return;
                    }

                    Closure createdClosure = o.getBody(Closure.class);
                    proceedTo(SubStage.COMPLETED, (s) -> {
                        s.closureLink = createdClosure.documentSelfLink;
                    });
                }));

    }

    private void getClosureDescription(ClosureAllocationTaskState state,
            Consumer<ClosureDescription> callbackFunction) {
        if (closureDescription != null) {
            callbackFunction.accept(closureDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving closure description.", e);
                        return;
                    }

                    ClosureDescription desc = o.getBody(ClosureDescription.class);
                    this.closureDescription = desc;
                    callbackFunction.accept(desc);
                }));
    }

    private Set<String> buildResourceLinks(ClosureAllocationTaskState state) {
        Set<String> resourceLinks = new HashSet<>(1);
        resourceLinks.add(state.closureLink);
        logInfo("Generate provisioned resourceLinks: %S", state.closureLink);

        return resourceLinks;
    }

}
