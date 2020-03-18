/*
 * Copyright (c) 2016-2020 VMware, Inc. All Rights Reserved.
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
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.closures.services.closure.Closure;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.ClosureProvisionTaskService.ClosureProvisionTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

public class ClosureProvisionTaskService extends
        AbstractTaskStatefulService<ClosureProvisionTaskService.ClosureProvisionTaskState, ClosureProvisionTaskService.ClosureProvisionTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_CLOSURE_PROVISION_TASKS;

    public static final String DISPLAY_NAME = "Closure Execution";

    public static class ClosureProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ClosureProvisionTaskState.SubStage> {

        public static enum SubStage {
            CREATED, CLOSURE_EXECUTING, COMPLETED, ERROR;
        }

        @Documentation(description = "The description that defines the closure description.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /** Set by a Task with the links of the provisioned resources. */
        @Documentation(description = "Set by a Task with the links of the provisioned resources.")
        @PropertyOptions(usage = { AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

    }

    public ClosureProvisionTaskService() {
        super(ClosureProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(Service.ServiceOption.PERSISTENCE, true);
        super.toggleOption(Service.ServiceOption.REPLICATION, true);
        super.toggleOption(Service.ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(Service.ServiceOption.IDEMPOTENT_POST, true);
    }

    @Override
    protected void validateStateOnStart(ClosureProvisionTaskState state)
            throws IllegalArgumentException {
        assertNotNull(state.resourceDescriptionLink, "resourceDescriptionLink");
        assertNotNull(state.resourceLinks, "resourceLinks");
    }

    @Override
    protected void handleStartedStagePatch(ClosureProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            startClosureExecutions(state);
            break;
        case CLOSURE_EXECUTING:
            break;
        case COMPLETED:
            complete();
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    @Override
    protected ServiceTaskCallback.ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ClosureProvisionTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for provisioned closure resources.");
        }
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse
            extends ServiceTaskCallback.ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    private void startClosureExecutions(ClosureProvisionTaskState state) {
        for (String closureLink : state.resourceLinks) {
            startClosureExecution(state, closureLink);
        }
    }

    private void startClosureExecution(ClosureProvisionTaskState state, String closureLink) {
        getClosureDescription(closureLink, (closure) -> {
            closure.serviceTaskCallback = ServiceTaskCallback
                    .create(state.documentSelfLink, TaskState.TaskStage.STARTED, SubStage.COMPLETED,
                            TaskState.TaskStage.STARTED, SubStage.ERROR);

            sendRequest(Operation.createPost(this.getHost(), closureLink)
                    .setBody(closure)
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            logWarning("Failed to execute closure: %s", Utils.toString(ex));
                            proceedTo(SubStage.ERROR);
                        }
                    }));

        });

        proceedTo(SubStage.CLOSURE_EXECUTING);
    }

    private void getClosureDescription(String closureLink, Consumer<Closure> callbackFunction) {
        sendRequest(Operation.createGet(this, closureLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask("Failure retrieving closure description.",
                                        e);
                                return;
                            }

                            Closure desc = o.getBody(Closure.class);
                            callbackFunction.accept(desc);
                        }));
    }

}
