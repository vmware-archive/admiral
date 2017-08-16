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

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption
        .AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.LINK;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption
        .SINGLE_ASSIGNMENT;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerDescriptionService
        .ContainerLoadBalancerDescription;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService;
import com.vmware.admiral.compute.container.loadbalancer.ContainerLoadBalancerService
        .ContainerLoadBalancerState;
import com.vmware.admiral.request.ContainerLoadBalancerAllocationTaskService
        .ContainerLoadBalancerAllocationTaskState;
import com.vmware.admiral.request.ContainerLoadBalancerAllocationTaskService
        .ContainerLoadBalancerAllocationTaskState.SubStage;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

/**
 * Task implementing the allocation of a container load balancer.
 */
public class ContainerLoadBalancerAllocationTaskService extends
        AbstractTaskStatefulService<ContainerLoadBalancerAllocationTaskState,
                ContainerLoadBalancerAllocationTaskService
                        .ContainerLoadBalancerAllocationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts
            .REQUEST_CONTAINER_LOAD_BALANCER_ALLOCATION_TASKS;

    public static final String DISPLAY_NAME = "Container Load Balancer Allocation";

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public static class ContainerLoadBalancerAllocationTaskState extends
            com.vmware.admiral.service.common
                    .TaskServiceDocument<ContainerLoadBalancerAllocationTaskState.SubStage> {

        @Documentation(description = "The description that defines the container load balancer "
                + "description.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, LINK,
                AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;
        /**
         * (Required) Number of resources to provision.
         */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long resourceCount;
        /**
         * Set by a Task with the links of the allocated resources.
         */
        @Documentation(description = "Set by a Task with the links of the allocated resources.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> resourceLinks;
        /**
         * (Internal) Set by task after resource name prefixes requested.
         */
        @Documentation(description = "Set by task after resource name prefixes requested.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> resourceNames;

        // Service use fields:
        /**
         * (Internal) Set by task with ContainerLoadBalancerDescription name.
         */
        @Documentation(description = "Set by task with ContainerLoadBalancerDescription name.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String descName;

        public static enum SubStage {
            CREATED,
            CONTEXT_PREPARED,
            RESOURCES_NAMED,
            COMPLETED,
            ERROR
        }
    }

    // cached container load balancer description
    private volatile ContainerLoadBalancerDescription loadBalancerDescription;

    public ContainerLoadBalancerAllocationTaskService() {
        super(ContainerLoadBalancerAllocationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(ContainerLoadBalancerAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, null);
            break;
        case CONTEXT_PREPARED:
            if (state.resourceNames == null || state.resourceNames.isEmpty()) {
                createResourcePrefixNameSelectionTask(state, loadBalancerDescription);
            } else {
                proceedTo(SubStage.RESOURCES_NAMED);
            }
            break;
        case RESOURCES_NAMED:
            createContainerLoadBalancerStates(state, null, null);
            break;
        case COMPLETED:
            updateResourcesAndComplete(state);
            break;
        case ERROR:
            completeWithError();
            break;
        default:
            break;
        }
    }

    @Override
    protected void validateStateOnStart(ContainerLoadBalancerAllocationTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.",
                    "request.resource-count.zero");
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ContainerLoadBalancerAllocationTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return finishedResponse;
    }

    @Override
    protected ServiceTaskCallbackResponse getFailedCallbackResponse(
            ContainerLoadBalancerAllocationTaskState state) {
        CallbackCompleteResponse failedResponse = new CallbackCompleteResponse();
        failedResponse.copy(state.serviceTaskCallback.getFailedResponse(state.taskInfo.failure));
        failedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated resources.");
        }
        return failedResponse;
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        final TaskStatusState statusTask = super.fromTask(state);
        if (SubStage.RESOURCES_NAMED == state.taskSubStage) {
            statusTask.name = ((ContainerLoadBalancerAllocationTaskState) state).descName;
        }

        return statusTask;
    }

    private Set<String> buildResourceLinks(Set<String> resourceNames) {
        logInfo("Generate provisioned resourceLinks");
        Set<String> resourceLinks = new HashSet<>(resourceNames.size());
        for (String resourceName : resourceNames) {
            String lbLink = UriUtils.buildUriPath(ContainerLoadBalancerService.FACTORY_LINK,
                    resourceName.replaceAll(" ", "-"));
            resourceLinks.add(lbLink);
        }
        return resourceLinks;
    }

    private void prepareContext(ContainerLoadBalancerAllocationTaskState state,
            ContainerLoadBalancerDescription description) {
        assertNotNull(state, "state");

        if (description == null) {
            getContainerLoadBalancerDescription(state,
                    (desc) -> prepareContext(state, desc));
            return;
        }

        proceedTo(SubStage.CONTEXT_PREPARED, s -> {
            // merge request/allocation properties over the lb description properties
            s.customProperties = mergeCustomProperties(description.customProperties,
                    state.customProperties);

            if (s.getCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY) == null) {
                s.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
            }
            s.descName = description.name;
        });
    }

    private void createResourcePrefixNameSelectionTask(
            ContainerLoadBalancerAllocationTaskState state,
            ContainerLoadBalancerDescription description) {

        assertNotNull(state, "state");

        if (description == null) {
            getContainerLoadBalancerDescription(state,
                    (desc) -> this.createResourcePrefixNameSelectionTask(
                            state, desc));
            return;
        }

        // create resource prefix name selection tasks
        ResourceNamePrefixTaskState namePrefixTask = new ResourceNamePrefixTaskState();
        namePrefixTask.documentSelfLink = getSelfId();
        namePrefixTask.resourceCount = state.resourceCount;
        namePrefixTask.baseResourceNameFormat = ResourceNamePrefixService
                .getDefaultResourceNameFormat(description.name);
        namePrefixTask.tenantLinks = state.tenantLinks;

        namePrefixTask.customProperties = state.customProperties;
        namePrefixTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.RESOURCES_NAMED,
                TaskStage.STARTED, SubStage.ERROR);
        namePrefixTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, ResourceNamePrefixTaskService.FACTORY_LINK)
                .setBody(namePrefixTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating resource name prefix task", e);
                        return;
                    }
                }));
    }

    private void createContainerLoadBalancerStates(ContainerLoadBalancerAllocationTaskState state,
            ContainerLoadBalancerDescription lbDesc, ServiceTaskCallback taskCallback) {

        if (lbDesc == null) {
            getContainerLoadBalancerDescription(state,
                    (desc) -> createContainerLoadBalancerStates(state, desc, taskCallback));
            return;
        }

        if (taskCallback == null) {
            createCounterSubTaskCallback(state, state.resourceCount, false,
                    SubStage.COMPLETED,
                    (serviceTask) -> createContainerLoadBalancerStates(state,
                            lbDesc, serviceTask));
            return;
        }

        for (String resourceName : state.resourceNames) {
            createContainerLoadBalancerState(state, lbDesc, resourceName, taskCallback);
        }
    }

    private void createContainerLoadBalancerState(ContainerLoadBalancerAllocationTaskState state,
            ContainerLoadBalancerDescription loadBalancerDescription,
            String resourceName, ServiceTaskCallback taskCallback) {

        assertNotNull(state, "state");
        assertNotNull(loadBalancerDescription, "loadBalancerDescription");
        assertNotEmpty(resourceName, "resourceName");
        assertNotNull(taskCallback, "taskCallback");

        try {
            final ContainerLoadBalancerState loadBalancerState = new ContainerLoadBalancerState();
            loadBalancerState.documentSelfLink = resourceName.replaceAll(" ", "-");
            loadBalancerState.name = resourceName;
            loadBalancerState.tenantLinks = state.tenantLinks;
            loadBalancerState.descriptionLink = state.resourceDescriptionLink;
            loadBalancerState.frontends = loadBalancerDescription.frontends;
            loadBalancerState.links = loadBalancerDescription.links;
            loadBalancerState.networks = loadBalancerDescription.networks;

            loadBalancerState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();

            // set the component link if container is created or scaled from a template
            String contextId = state.getCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY);
            if (contextId != null) {
                loadBalancerState.compositeComponentLink = UriUtils
                        .buildUriPath(CompositeComponentFactoryService.SELF_LINK, contextId);
            }
            loadBalancerState.customProperties = new HashMap<>();
            loadBalancerState.customProperties.put(ComputeConstants
                    .FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY, contextId);

            sendRequest(OperationUtil
                    .createForcedPost(this, ContainerLoadBalancerService.FACTORY_LINK)
                    .setBody(loadBalancerState)
                    .setCompletion(
                            (o, e) -> {
                                if (e == null) {
                                    ContainerLoadBalancerState body = o
                                            .getBody(ContainerLoadBalancerState.class);
                                    logInfo("Created ContainerLoadBalancerState: %s",
                                            body.documentSelfLink);
                                }
                                completeSubTasksCounter(taskCallback, e);
                            }));
        } catch (Throwable e) {
            failTask("System failure creating ContainerLoadBalancerState", e);
        }
    }

    private void updateResourcesAndComplete(ContainerLoadBalancerAllocationTaskState state) {
        complete(SubStage.COMPLETED, s -> {
            s.resourceLinks = buildResourceLinks(state.resourceNames);
        });
    }

    private void getContainerLoadBalancerDescription(ContainerLoadBalancerAllocationTaskState state,
            Consumer<ContainerLoadBalancerDescription> callbackFunction) {
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

