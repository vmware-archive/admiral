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

import static com.vmware.admiral.common.util.AssertUtil.assertNotEmpty;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.ResourceNamePrefixTaskService;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.compute.ComputeNetworkAllocationTaskService.ComputeNetworkAllocationTaskState.SubStage;
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
 * Task implementing the allocation of a compute network.
 */
public class ComputeNetworkAllocationTaskService extends
        AbstractTaskStatefulService<ComputeNetworkAllocationTaskService.ComputeNetworkAllocationTaskState, ComputeNetworkAllocationTaskService.ComputeNetworkAllocationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_NETWORK_ALLOCATION_TASKS;

    public static final String DISPLAY_NAME = "Compute Network Allocation";

    // cached network description
    private volatile ComputeNetworkDescription networkDescription;

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public static class ComputeNetworkAllocationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeNetworkAllocationTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            CONTEXT_PREPARED,
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
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long resourceCount;

        /** Set by a Task with the links of the provisioned resources. */
        @Documentation(description = "Set by a Task with the links of the provisioned resources.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> resourceLinks;

        // Service use fields:

        /** (Internal) Set by task after resource name prefixes requested. */
        @Documentation(description = "Set by task after resource name prefixes requested.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> resourceNames;

        /** (Internal) Set by task with ComputeNetworkDescription name. */
        @Documentation(description = "Set by task with ComputeNetworkDescription name.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String descName;

    }

    public ComputeNetworkAllocationTaskService() {
        super(ComputeNetworkAllocationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void validateStateOnStart(ComputeNetworkAllocationTaskState state) {
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
        }
    }

    @Override
    protected void handleStartedStagePatch(ComputeNetworkAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, null);
            break;
        case CONTEXT_PREPARED:
            if (state.resourceNames == null || state.resourceNames.isEmpty()) {
                createResourcePrefixNameSelectionTask(state, networkDescription);
            } else {
                proceedTo(SubStage.RESOURCES_NAMED);
            }
            break;
        case RESOURCES_NAMED:
            createComputeNetworkStates(state, networkDescription, null);
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
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ComputeNetworkAllocationTaskState state) {
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
            ComputeNetworkAllocationTaskState state) {
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
            statusTask.name = ((ComputeNetworkAllocationTaskState) state).descName;
        }

        return statusTask;
    }

    private Set<String> buildResourceLinks(Set<String> resourceNames) {
        logInfo("Generate provisioned resourceLinks");
        Set<String> resourceLinks = new HashSet<>(resourceNames.size());
        for (String resourceName : resourceNames) {
            String networkLink = buildNetworkLink(resourceName);
            resourceLinks.add(networkLink);
        }
        return resourceLinks;
    }

    private void prepareContext(ComputeNetworkAllocationTaskState state,
            ComputeNetworkDescription networkDescription) {
        assertNotNull(state, "state");

        if (networkDescription == null) {
            getComputeNetworkDescription(state,
                    (netwkDesc) -> prepareContext(state, netwkDesc));
            return;
        }

        proceedTo(SubStage.CONTEXT_PREPARED, s -> {
            // merge request/allocation properties over the network description properties
            s.customProperties = mergeCustomProperties(networkDescription.customProperties,
                    state.customProperties);

            if (s.getCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY) == null) {
                s.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
            }
            s.descName = networkDescription.name;
        });
    }

    private void createResourcePrefixNameSelectionTask(ComputeNetworkAllocationTaskState state,
            ComputeNetworkDescription networkDescription) {

        assertNotNull(state, "state");

        if (networkDescription == null) {
            getComputeNetworkDescription(state,
                    (netwkDesc) -> this.createResourcePrefixNameSelectionTask(
                            state, netwkDesc));
            return;
        }

        // create resource prefix name selection tasks
        ResourceNamePrefixTaskState namePrefixTask = new ResourceNamePrefixTaskState();
        namePrefixTask.documentSelfLink = getSelfId();
        namePrefixTask.resourceCount = state.resourceCount;
        namePrefixTask.baseResourceNameFormat = ResourceNamePrefixService
                .getDefaultResourceNameFormat(networkDescription.name);
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

    private void createComputeNetworkStates(ComputeNetworkAllocationTaskState state,
            ComputeNetworkDescription networkDesc, ServiceTaskCallback taskCallback) {

        if (networkDesc == null) {
            getComputeNetworkDescription(state,
                    (ntwkDesc) -> createComputeNetworkStates(state, ntwkDesc, taskCallback));
            return;
        }

        if (taskCallback == null) {
            createCounterSubTaskCallback(state, state.resourceCount, false,
                    SubStage.COMPLETED,
                    (serviceTask) -> createComputeNetworkStates(state,
                            networkDesc, serviceTask));
            return;
        }

        for (String resourceName : state.resourceNames) {
            createComputeNetworkState(state, networkDesc, resourceName, taskCallback);
        }
    }

    private void createComputeNetworkState(ComputeNetworkAllocationTaskState state,
            ComputeNetworkDescription networkDescription,
            String resourceName, ServiceTaskCallback taskCallback) {

        assertNotNull(state, "state");
        assertNotNull(networkDescription, "networkDescription");
        assertNotEmpty(resourceName, "resourceName");
        assertNotNull(taskCallback, "taskCallback");

        try {
            final ComputeNetwork networkState = new ComputeNetwork();
            networkState.documentSelfLink = buildNetworkLink(resourceName);
            networkState.name = resourceName;
            networkState.tenantLinks = state.tenantLinks;
            networkState.descriptionLink = networkDescription.documentSelfLink;
            networkState.customProperties = state.customProperties;

            String contextId;
            if (state.customProperties != null
                    && (contextId = state.customProperties
                            .get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY)) != null) {
                networkState.customProperties.put(FIELD_NAME_CONTEXT_ID_KEY, contextId);
                networkState.customProperties.put(
                        ComputeConstants.FIELD_NAME_COMPOSITE_COMPONENT_LINK_KEY,
                        UriUtils.buildUriPath(
                                CompositeComponentFactoryService.SELF_LINK, contextId));
            }

            sendRequest(OperationUtil
                    .createForcedPost(this, ComputeNetworkService.FACTORY_LINK)
                    .setBody(networkState)
                    .setCompletion(
                            (o, e) -> {
                                if (e == null) {
                                    ComputeNetwork body = o.getBody(ComputeNetwork.class);
                                    logInfo("Created ComputeNetworkState: %s ",
                                            body.documentSelfLink);
                                }
                                completeSubTasksCounter(taskCallback, e);
                            }));

        } catch (Throwable e) {
            failTask("System failure creating ComputeNetworkState", e);
        }
    }

    private void updateResourcesAndComplete(ComputeNetworkAllocationTaskState state) {
        getComputeNetworkDescription(state,
                (networkDescription) -> {
                    complete(SubStage.COMPLETED, s -> {
                        s.resourceLinks = buildResourceLinks(state.resourceNames);
                    });
                });
    }

    private void getComputeNetworkDescription(ComputeNetworkAllocationTaskState state,
            Consumer<ComputeNetworkDescription> callbackFunction) {
        if (networkDescription != null) {
            callbackFunction.accept(networkDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving compute network description state", e);
                        return;
                    }

                    ComputeNetworkDescription desc = o.getBody(ComputeNetworkDescription.class);
                    this.networkDescription = desc;
                    callbackFunction.accept(desc);
                }));
    }

    private static String buildNetworkLink(String name) {
        return UriUtils.buildUriPath(ComputeNetworkService.FACTORY_LINK, buildNetworkId(name));
    }

    private static String buildNetworkId(String name) {
        return name.replaceAll(" ", "-");
    }

}
