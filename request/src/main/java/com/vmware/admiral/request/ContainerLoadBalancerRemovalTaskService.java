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

package com.vmware.admiral.request;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService
        .ContainerLoadBalancerState;
import com.vmware.admiral.request.ContainerLoadBalancerRemovalTaskService
        .ContainerLoadBalancerRemovalTaskState;
import com.vmware.admiral.request.ContainerLoadBalancerRemovalTaskService
        .ContainerLoadBalancerRemovalTaskState.SubStage;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription
        .ComputeType;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing the removal of a container load balancer.
 */
public class ContainerLoadBalancerRemovalTaskService extends
        AbstractTaskStatefulService<ContainerLoadBalancerRemovalTaskState, SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts
            .REQUEST_CONTAINER_LOAD_BALANCER_REMOVAL_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Removal";

    public static class ContainerLoadBalancerRemovalTaskState extends
            com.vmware.admiral.service.common
                    .TaskServiceDocument<ContainerLoadBalancerRemovalTaskState.SubStage> {

        /**
         * (Required) The resources on which the given operation will be applied
         */
        @PropertyOptions(usage = {REQUIRED}, indexing = STORE_ONLY)
        public Set<String> resourceLinks;
        /**
         * whether to actually go and destroy the container or just remove the
         * ContainerLoadBalancerState
         */
        public boolean removeOnly;

        public static enum SubStage {
            CREATED,
            INSTANCES_REMOVING,
            INSTANCES_REMOVED,
            REMOVING_RESOURCE_STATES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(INSTANCES_REMOVING, REMOVING_RESOURCE_STATES));
        }
    }

    public ContainerLoadBalancerRemovalTaskService() {
        super(ContainerLoadBalancerRemovalTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ContainerLoadBalancerRemovalTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryContainerLoadBalancerResources(state);
            break;
        case INSTANCES_REMOVING:
            break;
        case INSTANCES_REMOVED:
            removeStates(state, null);
            break;
        case REMOVING_RESOURCE_STATES:
            break;
        case COMPLETED:
            complete(SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    private void queryContainerLoadBalancerResources(ContainerLoadBalancerRemovalTaskState state) {
        QueryTask lbQuery = createResourcesQuery(ContainerLoadBalancerState.class,
                state.resourceLinks);
        ServiceDocumentQuery<ContainerLoadBalancerState> query = new ServiceDocumentQuery<>(
                getHost(),
                ContainerLoadBalancerState.class);
        Set<String> lbLinks = new HashSet<>();
        QueryUtil.addBroadcastOption(lbQuery);
        QueryUtil.addExpandOption(lbQuery);
        query.query(lbQuery, (r) -> {
            if (r.hasException()) {
                failTask("Failure retrieving query results", r.getException());
            } else if (r.hasResult()) {
                if (r.getResult().containerLink != null) {
                    lbLinks.add(r.getResult().containerLink);
                }
            } else {
                if (lbLinks.isEmpty()) {
                    logWarning("No available resources found to be removed with links: %s",
                            state.resourceLinks);
                    proceedTo(SubStage.INSTANCES_REMOVED);
                } else {
                    proceedTo(SubStage.INSTANCES_REMOVING);
                    deleteResourceInstances(state, lbLinks);
                }
            }
        });
    }

    private QueryTask createResourcesQuery(Class<? extends ServiceDocument> type,
                                           Collection<String> resourceLinks) {
        QueryTask query = QueryUtil.buildQuery(type, false);
        QueryUtil.addListValueClause(query, ServiceDocument.FIELD_NAME_SELF_LINK, resourceLinks);

        return query;
    }

    private void deleteResourceInstances(ContainerLoadBalancerRemovalTaskState state,
                                         Set<String> resourceLinks) {
        if (state.removeOnly) {
            logFine("Skipping actual container removal by the adapter since the removeOnly flag "
                    + "was set: %s", state.documentSelfLink);

            // skip the actual removal of containers through the adapter
            proceedTo(SubStage.INSTANCES_REMOVED);
            return;
        }

        RequestBrokerState requestBrokerState = new RequestBrokerState();
        requestBrokerState.resourceType = ComputeType.DOCKER_CONTAINER.name().toString();
        requestBrokerState.operation = RequestBrokerState.REMOVE_RESOURCE_OPERATION;
        requestBrokerState.tenantLinks = state.tenantLinks;
        requestBrokerState.resourceLinks = resourceLinks;
        requestBrokerState.requestTrackerLink = state.requestTrackerLink;
        requestBrokerState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.INSTANCES_REMOVED, TaskStage.FAILED,
                SubStage.ERROR);

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(requestBrokerState).setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failed removing container load balancer instances", e);
                        return;
                    }
                }));
    }

    private void removeStates(ContainerLoadBalancerRemovalTaskState state, String subTaskLink) {
        if (subTaskLink == null) {
            createCounterSubTask(state, state.resourceLinks.size(),
                    (link) -> removeStates(state, link));
            return;
        }
        try {
            proceedTo(SubStage.REMOVING_RESOURCE_STATES);
            for (String resourceLink : state.resourceLinks) {
                sendRequest(Operation
                        .createGet(this, resourceLink)
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask("Failed retrieving Container Load Balancer State: "
                                        + resourceLink, e);
                                return;
                            }

                            ContainerLoadBalancerState loadBalancerState = o.getBody
                                    (ContainerLoadBalancerState.class);

                            deleteLoadBalancer(loadBalancerState).setCompletion((o2, e2) -> {
                                if (e2 != null) {
                                    failTask("Failed removing ContainerLoadBalancerState: "
                                            + resourceLink, e2);
                                    return;
                                }
                                completeSubTasksCounter(subTaskLink, null);
                            }).sendWith(this);
                        }));
            }
        } catch (Throwable e) {
            failTask("Unexpected exception while deleting resources", e);
        }
    }

    private Operation deleteLoadBalancer(ContainerLoadBalancerState containerLoadBalancerState) {
        return Operation
                .createDelete(this, containerLoadBalancerState.documentSelfLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failed deleting ContainerLoadBalancerState: %s. " +
                                                "Error: %s",
                                        containerLoadBalancerState.documentSelfLink, Utils
                                                .toString(e));
                                return;
                            }
                            logInfo("Deleted ContainerLoadBalancerState: %s",
                                    containerLoadBalancerState.documentSelfLink);
                        });
    }

}
