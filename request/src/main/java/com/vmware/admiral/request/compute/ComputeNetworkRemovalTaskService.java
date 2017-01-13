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

package com.vmware.admiral.request.compute;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.http.HttpStatus;

import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.compute.ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.CounterSubTaskService.CounterSubTaskState;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;

/**
 * Task implementing removal of Compute Networks.
 */
public class ComputeNetworkRemovalTaskService extends
        AbstractTaskStatefulService<ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState, ComputeNetworkRemovalTaskService.ComputeNetworkRemovalTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_NETWORK_REMOVAL_TASKS;

    public static final String DISPLAY_NAME = "Compute Network Removal";

    public static class ComputeNetworkRemovalTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeNetworkRemovalTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            REMOVING_RESOURCE_STATES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(REMOVING_RESOURCE_STATES));
        }

        /** (Required) The resources on which the given operation will be applied */
        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;

        /**
         * whether to actually go and destroy the compute network using the adapter or just remove
         * the ComputeNetworkState
         */
        public boolean removeOnly;
    }

    public ComputeNetworkRemovalTaskService() {
        super(ComputeNetworkRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ComputeNetworkRemovalTaskState state) {
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

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        if (SubStage.COMPLETED == state.taskSubStage) {
            statusTask.name = NetworkOperationType
                    .extractDisplayName(NetworkOperationType.DELETE.id);
        }
        return statusTask;
    }

    private void completeSubTasksCounter(String subTaskLink, Throwable ex) {
        CounterSubTaskState body = new CounterSubTaskState();
        body.taskInfo = new TaskState();
        if (ex == null) {
            body.taskInfo.stage = TaskStage.FINISHED;
        } else {
            body.taskInfo.stage = TaskStage.FAILED;
            body.taskInfo.failure = Utils.toServiceErrorResponse(ex);
        }

        sendRequest(Operation.createPatch(this, subTaskLink)
                .setBody(body)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Notifying counting task failed: %s", e);
                    }
                }));
    }

    private void removeResources(ComputeNetworkRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null) {
            createCounterSubTask(state, state.resourceLinks.size(),
                    (link) -> removeResources(state, link));
            return;
        }

        try {
            for (String resourceLink : state.resourceLinks) {
                sendRequest(Operation
                        .createGet(this, resourceLink)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                if (e instanceof ServiceHost.ServiceNotFoundException
                                        && o.getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                                    logWarning("Compute Network is not found at link: %s ",
                                            resourceLink);
                                    completeSubTasksCounter(subTaskLink, null);
                                } else {
                                    failTask("Failed retrieving Compute Network State: "
                                            + resourceLink, e);
                                }
                                return;
                            }

                            ComputeNetwork cns = o.getBody(ComputeNetwork.class);

                            deleteComputeNetwork(cns).setCompletion((o2, e2) -> {
                                if (e2 != null) {
                                    failTask("Failed removing Compute Network State: "
                                            + resourceLink, e2);
                                    return;
                                }
                                completeSubTasksCounter(subTaskLink, null);
                            }).sendWith(this);
                        }));
            }
            proceedTo(SubStage.REMOVING_RESOURCE_STATES);
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private Operation deleteComputeNetwork(ComputeNetwork cns) {
        return Operation
                .createDelete(this, cns.documentSelfLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failed deleting ComputeNetworkState: "
                                        + cns.documentSelfLink, e);
                                return;
                            }
                            logInfo("Deleted ComputeNetworkState: " + cns.documentSelfLink);
                        });
    }
}
