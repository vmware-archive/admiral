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

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor
        .DOCKER_CONTAINER_CREATE_USE_BUNDLED_IMAGE;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor
        .DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY;
import static com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancers
        .CONTAINER_LOAD_BALANCER_DESCRIPTION_LINK;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.ContainerDescriptionService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService
        .ContainerLoadBalancerDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService
        .ContainerLoadBalancerState;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancers;
import com.vmware.admiral.request.ContainerLoadBalancerProvisionTaskService
        .ContainerLoadBalancerProvisionTaskState;
import com.vmware.admiral.request.ContainerLoadBalancerProvisionTaskService
        .ContainerLoadBalancerProvisionTaskState.SubStage;
import com.vmware.admiral.request.ContainerLoadBalancerReconfigureTaskService
        .ContainerLoadBalancerReconfigureTaskState;
import com.vmware.admiral.request.RequestBrokerService.RequestBrokerState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.CounterSubTaskService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription
        .ComputeType;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;

/**
 * Task implementing the provision of a container load balancer.
 */
public class ContainerLoadBalancerProvisionTaskService extends
        AbstractTaskStatefulService<ContainerLoadBalancerProvisionTaskState, SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts
            .REQUEST_CONTAINER_LOAD_BALANCER_PROVISION_TASKS;

    public static final String DISPLAY_NAME = "Load Balancer Provision";

    // cached container load balancer description
    private volatile ContainerLoadBalancerDescription loadBalancerDescription;

    public static class ContainerLoadBalancerProvisionTaskState extends
            com.vmware.admiral.service.common
                    .TaskServiceDocument<ContainerLoadBalancerProvisionTaskState.SubStage> {

        /**
         * (Required) The description that defines the requested resource.
         */
        @Documentation(description = "Type of resource to create.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT})
        public String resourceDescriptionLink;
        /**
         * (Required) Number of resources to provision.
         */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY,
                usage = {PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL})
        public Long resourceCount;
        /**
         * (Required) Links to already allocated resources that are going to be provisioned.
         */
        @Documentation(
                description = "Links to already allocated resources that are going to be " +
                        "provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT})
        public Set<String> resourceLinks;

        // Service use fields:

        /**
         * (Internal) Reference to the request broker state used to provision containers.
         */
        @Documentation(description = "Reference to the request broker state used to provision " +
                "containers")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL})
        public String rbStateLink;

        public static enum SubStage {
            CREATED,
            PREPARE_PROVISIONING,
            PROVISIONING,
            UPDATE_LINKS,
            RECONFIGURE,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PREPARE_PROVISIONING, PROVISIONING));
        }
    }

    public ContainerLoadBalancerProvisionTaskService() {
        super(ContainerLoadBalancerProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ContainerLoadBalancerProvisionTaskState state) {
        state.resourceCount = (long) state.resourceLinks.size();

        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.",
                    "request.resource-count.zero");
        }
    }

    @Override
    protected void handleStartedStagePatch(ContainerLoadBalancerProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionContainers(state);
            break;
        case PREPARE_PROVISIONING:
            break;
        case PROVISIONING:
            break;
        case UPDATE_LINKS:
            updateLoadBalancerStates(state, null, null);
            break;
        case RECONFIGURE:
            ContainerLoadBalancerReconfigureTaskState cState = new
                    ContainerLoadBalancerReconfigureTaskState();
            cState.customProperties = new HashMap<>(state.customProperties);
            cState.tenantLinks = state.tenantLinks;
            cState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                    TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);

            sendRequest(Operation
                    .createPost(this, ContainerLoadBalancerReconfigureTaskService.FACTORY_LINK)
                    .setBody(cState)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure creating container load balancer", e);
                            return;
                        }
                    }));
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

    private void provisionContainers(ContainerLoadBalancerProvisionTaskState state) {
        logInfo("Provision request for %s load balancers", state.resourceCount);
        getContainerLoadBalancerDescription(state, (loadBalancerDescription) -> {
            createContainerDescription(loadBalancerDescription, (containerDescription) -> {
                sendProvisionRequest(state, containerDescription);
            });
        });
        proceedTo(SubStage.PREPARE_PROVISIONING);
    }

    private void sendProvisionRequest(ContainerLoadBalancerProvisionTaskState state,
                                      ContainerDescription containerDescription) {
        String cdLink = containerDescription.documentSelfLink;
        String contextId = RequestUtils.getContextId(state);
        long clusterSize = state.resourceCount;

        logFine("Cluster container with %s description link, from %s context_id with cluster " +
                        "size: %s",
                cdLink, contextId, clusterSize);

        RequestBrokerState rbState = new RequestBrokerState();
        rbState.resourceDescriptionLink = cdLink;
        rbState.resourceCount = clusterSize;
        rbState.customProperties = new HashMap<>();
        rbState.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
        rbState.resourceType = ComputeType.DOCKER_CONTAINER.name().toString();
        rbState.operation = RequestBrokerState.PROVISION_RESOURCE_OPERATION;
        rbState.tenantLinks = state.tenantLinks;
        rbState.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.UPDATE_LINKS, TaskStage.FAILED,
                SubStage.ERROR);

        sendRequest(Operation.createPost(this, RequestBrokerFactoryService.SELF_LINK)
                .setBody(rbState)
                .setCompletion((o, e) -> {
                    o.getBody(RequestBrokerState.class);
                    if (e != null) {
                        failTask("Failure creating container load balancer", e);
                        return;
                    } else {
                        proceedTo(SubStage.PROVISIONING, (patchedState) ->
                                patchedState.rbStateLink = o.getBody(RequestBrokerState.class)
                                        .documentSelfLink
                        );
                    }
                }));
    }

    private void updateLoadBalancerStates(ContainerLoadBalancerProvisionTaskState state,
                                          Set<String> provisionedContainerLinks, String
                                                  subTaskLink) {
        if (provisionedContainerLinks == null) {
            sendRequest(Operation.createGet(this, state.rbStateLink).setCompletion((o, e) -> {
                        if (e != null) {
                            failTask("Failure retrieving created containers", e);
                            return;
                        }
                        RequestBrokerState requestBrokerState = o.getBody(RequestBrokerState.class);
                        updateLoadBalancerStates(state, requestBrokerState.resourceLinks,
                                subTaskLink);
                    }
            ));
            return;
        }

        if (state.resourceLinks.size() != provisionedContainerLinks.size()) {
            failTask("Mismatch between number of load balancer states and containers " +
                    "provisioned", null);
        }
        if (subTaskLink == null) {
            CounterSubTaskService.CounterSubTaskState subTaskInitState = new
                    CounterSubTaskService.CounterSubTaskState();
            subTaskInitState.completionsRemaining = state.resourceLinks.size();
            subTaskInitState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();
            subTaskInitState.serviceTaskCallback = ServiceTaskCallback.create(
                    getSelfLink(),
                    TaskStage.STARTED, SubStage.RECONFIGURE,
                    TaskStage.STARTED, SubStage.ERROR);

            CounterSubTaskService.createSubTask(this, subTaskInitState,
                    (link) -> updateLoadBalancerStates(state, provisionedContainerLinks, link));
            return;
        }
        try {
            Iterator<String> lbStatesIterator = state.resourceLinks.iterator();
            for (String provisionedContainerLink : provisionedContainerLinks) {
                String lbStateLink = lbStatesIterator.next();
                sendRequest(Operation.createGet(this, lbStateLink).setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Unable to retrieve load balancer state", e);
                    }
                    ContainerLoadBalancerState lbState = o.getBody(ContainerLoadBalancerState
                            .class);
                    lbState.containerLink = provisionedContainerLink;
                    sendRequest(Operation.createPatch(this, lbState.documentSelfLink)
                            .setBody(lbState)
                            .setCompletion((o1, e1) -> {
                                completeSubTasksCounter(subTaskLink, e1);
                            }));
                }));

            }
        } catch (Throwable e) {
            failTask("Unexpected exception while updating load balancer instances", e);
        }
    }

    private void createContainerDescription(ContainerLoadBalancerDescription description,
                                            Consumer<ContainerDescription> callbackFunction) {
        ContainerDescription containerDescription = new ContainerDescription();
        containerDescription.name = ContainerLoadBalancers
                .LOAD_BALANCER_CONTAINER_NAME_PREFIX + "_" + description.name;
        containerDescription.image = ContainerLoadBalancers.LOAD_BALANCER_IMAGE_NAME;
        containerDescription.links = description.links;
        if (description.networks != null) {
            containerDescription.networks = description.networks.stream()
                    .collect(Collectors.toMap(n -> n.name, n -> n));
            containerDescription.networks.entrySet().stream()
                    .forEach(e -> e.getValue().name = null);
        }
        containerDescription.portBindings = description.portBindings;
        containerDescription.customProperties = new HashMap<>();
        containerDescription.customProperties
                .put(DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY,
                        Boolean.TRUE.toString());
        containerDescription.customProperties.put(DOCKER_CONTAINER_CREATE_USE_BUNDLED_IMAGE,
                ContainerLoadBalancers.LOAD_BALANCER_IMAGE_REFERENCE);
        containerDescription.customProperties
                .put(CONTAINER_LOAD_BALANCER_DESCRIPTION_LINK, description.documentSelfLink);
        containerDescription.tenantLinks = description.tenantLinks;

        sendRequest(Operation.createPost(this, ContainerDescriptionService.FACTORY_LINK)
                .setBody(containerDescription)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating container description", e);
                        return;
                    }
                    callbackFunction.accept(o.getBody(ContainerDescription.class));
                }));
    }

    private void getContainerLoadBalancerDescription(ContainerLoadBalancerProvisionTaskState state,
                                                     Consumer<ContainerLoadBalancerDescription>
                                                             callbackFunction) {
        if (loadBalancerDescription != null) {
            callbackFunction.accept(loadBalancerDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving container load balacner description state",
                                e);
                        return;
                    }

                    ContainerLoadBalancerDescription desc = o
                            .getBody(ContainerLoadBalancerDescription.class);
                    this.loadBalancerDescription = desc;
                    callbackFunction.accept(desc);
                }));
    }
}
