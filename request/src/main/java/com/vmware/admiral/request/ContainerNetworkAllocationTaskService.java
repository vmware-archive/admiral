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
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeLists;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState.SubStage;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

/**
 * Task implementing the allocation of a container network.
 */
public class ContainerNetworkAllocationTaskService
        extends
        AbstractTaskStatefulService<ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState, ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_CONTAINER_NETWORK_ALLOCATION_TASKS;

    public static final String DISPLAY_NAME = "Container Network Allocation";

    // cached network description
    private volatile ContainerNetworkDescription networkDescription;

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        List<String> resourceLinks;
    }

    public static class ContainerNetworkAllocationTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerNetworkAllocationTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            RESOURCES_NAMED,
            COMPLETED,
            ERROR
        }

        /** (Required) The description that defines the requested resource. */
        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String resourceDescriptionLink;

        /** (Required) Number of resources to provision. */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Long resourceCount;

        /** Set by a Task with the links of the provisioned resources. */
        @Documentation(description = "Set by a Task with the links of the provisioned resources.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = PropertyUsageOption.SINGLE_ASSIGNMENT)
        public List<String> resourceLinks;

        // Service use fields:

        /** (Internal) Set by task after resource name prefixes requested. */
        @Documentation(description = "Set by task after resource name prefixes requested.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public List<String> resourceNames;

        /** (Internal) Set by task with ContainerNetworkDescription name. */
        @Documentation(description = "Set by task with ContainerNetworkDescription name.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String descName;

    }

    public ContainerNetworkAllocationTaskService() {
        super(ContainerNetworkAllocationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void validateStateOnStart(ContainerNetworkAllocationTaskState state) {
        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerNetworkAllocationTaskState patchBody,
            ContainerNetworkAllocationTaskState currentState) {

        currentState.resourceLinks = mergeLists(
                currentState.resourceLinks, patchBody.resourceLinks);

        currentState.resourceNames = mergeProperty(
                currentState.resourceNames, patchBody.resourceNames);

        currentState.resourceCount = mergeProperty(currentState.resourceCount,
                patchBody.resourceCount);

        currentState.descName = mergeProperty(currentState.descName, patchBody.descName);

        return false;
    }

    @Override
    protected void handleStartedStagePatch(ContainerNetworkAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContextAndCreateResourcePrefixNameSelectionTask(state, null);
            break;
        case RESOURCES_NAMED:
            createContainerNetworkStates(state, null, null);
            break;
        case COMPLETED:
            state.resourceLinks = buildResourceLinks(state);
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ContainerNetworkAllocationTaskState state) {
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
            ContainerNetworkAllocationTaskState state) {
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
            statusTask.name = ((ContainerNetworkAllocationTaskState) state).descName;
        }

        return statusTask;
    }

    private List<String> buildResourceLinks(ContainerNetworkAllocationTaskState state) {
        logInfo("Generate provisioned resourceLinks");
        List<String> resourceLinks = new ArrayList<>(state.resourceNames.size());
        for (String resourceName : state.resourceNames) {
            String networkLink = buildResourceLink(resourceName);
            resourceLinks.add(networkLink);
        }
        return resourceLinks;
    }

    private String buildResourceLink(String resourceName) {
        return UriUtils.buildUriPath(ContainerNetworkService.FACTORY_LINK,
                buildResourceId(resourceName));
    }

    private String buildResourceId(String resourceName) {
        return resourceName.replaceAll(" ", "-");
    }

    private void prepareContextAndCreateResourcePrefixNameSelectionTask(
            ContainerNetworkAllocationTaskState state,
            ContainerNetworkDescription networkDescription) {
        if (networkDescription == null) {
            getContainerNetworkDescription(state,
                    (netwkDesc) -> this.prepareContextAndCreateResourcePrefixNameSelectionTask(
                            state,
                            netwkDesc));
            return;
        }

        // prepare context
        prepareContext(state, networkDescription);

        // create ResourcePrefixNameSelectionTask
        if (state.resourceNames == null || state.resourceNames.isEmpty()) {
            createResourcePrefixNameSelectionTask(state, networkDescription);
        } else {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.RESOURCES_NAMED));
        }
    }

    private void prepareContext(ContainerNetworkAllocationTaskState state,
            ContainerNetworkDescription networkDescription) {
        assertNotNull(state, "state");
        assertNotNull(networkDescription, "networkDescription");

        // merge request/allocation properties over the network description properties
        state.customProperties = mergeProperty(networkDescription.customProperties,
                state.customProperties);

        if (state.getCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY) == null) {
            state.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
        }
        state.descName = networkDescription.name;
    }

    private void createResourcePrefixNameSelectionTask(ContainerNetworkAllocationTaskState state,
            ContainerNetworkDescription networkDescription) {

        assertNotNull(state, "state");
        assertNotNull(networkDescription, "networkDescription");

        // create resource prefix name selection tasks
        ResourceNamePrefixTaskState namePrefixTask = new ResourceNamePrefixTaskState();
        namePrefixTask.documentSelfLink = getSelfId();
        namePrefixTask.resourceCount = state.resourceCount;
        namePrefixTask.baseResourceNameFormat = ResourceNamePrefixService
                .getDefaultResourceNameFormat(networkDescription.name);
        namePrefixTask.tenantLinks = state.tenantLinks;

        namePrefixTask.customProperties = state.customProperties;
        namePrefixTask.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
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

    private void createContainerNetworkStates(ContainerNetworkAllocationTaskState state,
            ContainerNetworkDescription networkDesc, ServiceTaskCallback taskCallback) {

        if (networkDesc == null) {
            getContainerNetworkDescription(state,
                    (ntwkDesc) -> createContainerNetworkStates(state, ntwkDesc, taskCallback));
            return;
        }

        if (taskCallback == null) {
            createCounterSubTaskCallback(state, state.resourceCount, false,
                    SubStage.COMPLETED,
                    (serviceTask) -> createContainerNetworkStates(state,
                            networkDesc, serviceTask));
            return;
        }

        for (String resourceName : state.resourceNames) {
            createContainerNetworkState(state, networkDesc, resourceName, taskCallback);
        }
    }

    private void createContainerNetworkState(ContainerNetworkAllocationTaskState state,
            ContainerNetworkDescription networkDescription,
            String resourceName, ServiceTaskCallback taskCallback) {

        assertNotNull(state, "state");
        assertNotNull(networkDescription, "networkDescription");
        assertNotEmpty(resourceName, "resourceName");
        assertNotNull(taskCallback, "taskCallback");

        try {

            final ContainerNetworkState networkState = new ContainerNetworkState();
            networkState.documentSelfLink = buildResourceId(resourceName);
            networkState.name = resourceName;
            networkState.tenantLinks = state.tenantLinks;
            networkState.descriptionLink = state.resourceDescriptionLink;
            networkState.customProperties = state.customProperties;
            networkState.ipam = networkDescription.ipam;
            networkState.driver = networkDescription.driver;
            networkState.options = networkDescription.options;
            networkState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();

            String contextId;
            if (state.customProperties != null && (contextId = state.customProperties
                    .get(FIELD_NAME_CONTEXT_ID_KEY)) != null) {
                networkState.compositeComponentLink = UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, contextId);
            }

            sendRequest(OperationUtil
                    .createForcedPost(this, ContainerNetworkService.FACTORY_LINK)
                    .setBody(networkState)
                    .setCompletion(
                            (o, e) -> {
                                if (e == null) {
                                    ContainerNetworkState body = o
                                            .getBody(ContainerNetworkState.class);
                                    logInfo("Created ContainerNetworkState: %s ",
                                            body.documentSelfLink);
                                }
                                completeSubTasksCounter(taskCallback, e);
                            }));

        } catch (Throwable e) {
            failTask("System failure creating ContainerNetworkStates", e);
        }
    }

    private void getContainerNetworkDescription(ContainerNetworkAllocationTaskState state,
            Consumer<ContainerNetworkDescription> callbackFunction) {
        if (networkDescription != null) {
            callbackFunction.accept(networkDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving container network description state", e);
                        return;
                    }

                    ContainerNetworkDescription desc = o.getBody(ContainerNetworkDescription.class);
                    this.networkDescription = desc;
                    callbackFunction.accept(desc);
                }));
    }

}
