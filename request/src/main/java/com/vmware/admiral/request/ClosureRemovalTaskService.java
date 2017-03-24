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

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.vmware.admiral.request.ClosureRemovalTaskService.ClosureRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.Utils;

public class ClosureRemovalTaskService extends
        AbstractTaskStatefulService<ClosureRemovalTaskService.ClosureRemovalTaskState, ClosureRemovalTaskService
                .ClosureRemovalTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Closure Removal";

    public static class ClosureRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ClosureRemovalTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            REMOVING_RESOURCE_STATES,
            COMPLETED,
            ERROR;
        }

        /** (Required) The resources on which the given operation will be applied */
        @PropertyOptions(usage = { REQUIRED, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

    }

    public ClosureRemovalTaskService() {
        super(ClosureRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void validateStateOnStart(ClosureRemovalTaskState state)
            throws IllegalArgumentException {
        assertNotEmpty(state.resourceLinks, "resourceLinks");
    }

    @Override
    protected void handleStartedStagePatch(ClosureRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            removeResources(state, null);
            break;
        case REMOVING_RESOURCE_STATES:
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

    private void removeResources(ClosureRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null) {
            // count 2 * resourceLinks (to keep track of each removal operation starting and ending)
            createCounterSubTask(state, state.resourceLinks.size(),
                    (link) -> removeResources(state, link));
            return;
        }
        try {
            for (String resourceLink : state.resourceLinks) {
                sendRequest(Operation
                        .createGet(this, resourceLink)
                        .setCompletion(
                                (o, e) -> {
                                    if (e != null) {
                                        failTask("Failed retrieving closure state: " + resourceLink, e);
                                        return;
                                    }

                                    Operation delClosureOpr = deleteClosure(resourceLink);
                                    // list of operations to execute to release container resources
                                    List<Operation> operations = new ArrayList<>();
                                    operations.add(delClosureOpr);
                                    OperationJoin.create(operations).setCompletion((ops, exs) -> {
                                        // fail the task is there are exceptions in the children operations
                                        if (exs != null && !exs.isEmpty()) {
                                            failTask("Failed deleting closure resources: "
                                                    + Utils.toString(exs), null);
                                            return;
                                        }

                                        // complete the counter task after all remove operations finished
                                        // successfully
                                        completeSubTasksCounter(subTaskLink, null);
                                    }).sendWith(this);
                                }));
            }
            proceedTo(SubStage.REMOVING_RESOURCE_STATES);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private Operation deleteClosure(String resourceLink) {
        return Operation
                .createDelete(this, resourceLink)
                .setContextId(getSelfId())
                .setBody(new ServiceDocument())
                .setCompletion(
                        (op, ex) -> {
                            if (ex != null) {
                                logWarning("Failed deleting closure state: " + resourceLink, ex);
                                return;
                            }
                            logInfo("Deleted closure state: " + resourceLink);
                        });
    }
}
