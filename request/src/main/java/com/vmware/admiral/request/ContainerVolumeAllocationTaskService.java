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
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.ContainerVolumeAllocationTaskService.ContainerVolumeAllocationTaskState.SubStage;
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

public class ContainerVolumeAllocationTaskService extends
        AbstractTaskStatefulService<ContainerVolumeAllocationTaskService.ContainerVolumeAllocationTaskState, ContainerVolumeAllocationTaskService.ContainerVolumeAllocationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_CONTAINER_VOLUME_ALLOCATION_TASKS;

    public static final String DISPLAY_NAME = "Container Volume Allocation";

    // cached volume description
    private volatile ContainerVolumeDescription volumeDescription;

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public static class ContainerVolumeAllocationTaskState
            extends com.vmware.admiral.service.common.TaskServiceDocument<ContainerVolumeAllocationTaskState.SubStage> {

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
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long resourceCount;

        /** Set by a Task with the links of the provisioned resources. */
        @Documentation(description = "Set by a Task with the links of the provisioned resources.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Set<String> resourceLinks;

        /** (Internal) Set by task after resource name prefixes requested. */
        @Documentation(description = "Set by task after resource name prefixes requested.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public List<String> resourceNames;

        /** (Internal) Set by task with ContainerVolumeDescription name. */
        @Documentation(description = "Set by task with ContainerVolumeDescription name.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String descName;
    }

    public ContainerVolumeAllocationTaskService() {
        super(ContainerVolumeAllocationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void validateStateOnStart(ContainerVolumeAllocationTaskState state)
            throws IllegalArgumentException {

        assertNotEmpty(state.resourceDescriptionLink, "resourceDescriptionLink");

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }

    }

    @Override
    protected void handleStartedStagePatch(ContainerVolumeAllocationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContextAndCreateResourcePrefixNameSelectionTask(state, null);
            break;
        case RESOURCES_NAMED:
            createContainerVolumeStates(state, null, null);
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
            ContainerVolumeAllocationTaskState state) {
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
            ContainerVolumeAllocationTaskState state) {
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
            statusTask.name = ((ContainerVolumeAllocationTaskState) state).descName;
        }

        return statusTask;
    }

    private void prepareContextAndCreateResourcePrefixNameSelectionTask(
            ContainerVolumeAllocationTaskState state,
            ContainerVolumeDescription volumeDescription) {
        if (volumeDescription == null) {
            getContainerVolumeDescription(state,
                    (volumeDesc) -> this.prepareContextAndCreateResourcePrefixNameSelectionTask(
                            state,
                            volumeDesc));
            return;
        }

        // prepare context
        prepareContext(state, volumeDescription);

        // create ResourcePrefixNameSelectionTask in order to populate resourceNames.
        if (state.resourceNames == null || state.resourceNames.isEmpty()) {
            createResourcePrefixNameSelectionTask(state, volumeDescription);
        } else {
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.RESOURCES_NAMED));
        }
    }

    private void getContainerVolumeDescription(ContainerVolumeAllocationTaskState state,
            Consumer<ContainerVolumeDescription> callbackFunction) {
        if (volumeDescription != null) {
            callbackFunction.accept(volumeDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving container volume description.", e);
                        return;
                    }

                    ContainerVolumeDescription desc = o.getBody(ContainerVolumeDescription.class);
                    this.volumeDescription = desc;
                    callbackFunction.accept(desc);
                }));
    }

    private void prepareContext(ContainerVolumeAllocationTaskState state,
            ContainerVolumeDescription volumeDescription) {
        assertNotNull(state, "state");
        assertNotNull(volumeDescription, "volumeDescription");

        // merge request/allocation properties over the volume description properties
        state.customProperties = mergeProperty(volumeDescription.customProperties,
                state.customProperties);

        if (state.getCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY) == null) {
            state.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
        }
        state.descName = volumeDescription.name;
    }

    private void createResourcePrefixNameSelectionTask(ContainerVolumeAllocationTaskState state,
            ContainerVolumeDescription volumeDescription) {

        assertNotNull(state, "state");
        assertNotNull(volumeDescription, "volumeDescription");

        // create resource prefix name selection tasks
        ResourceNamePrefixTaskState namePrefixTask = new ResourceNamePrefixTaskState();
        namePrefixTask.documentSelfLink = getSelfId();
        namePrefixTask.resourceCount = state.resourceCount;
        namePrefixTask.baseResourceNameFormat = ResourceNamePrefixService
                .getDefaultResourceNameFormat(volumeDescription.name);
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

    private void createContainerVolumeStates(ContainerVolumeAllocationTaskState state,
            ContainerVolumeDescription volumeDesc, ServiceTaskCallback taskCallback) {

        if (volumeDesc == null) {
            getContainerVolumeDescription(state,
                    (ntwkDesc) -> createContainerVolumeStates(state, ntwkDesc, taskCallback));
            return;
        }

        if (taskCallback == null) {
            createCounterSubTaskCallback(state, state.resourceCount, false,
                    SubStage.COMPLETED,
                    (serviceTask) -> createContainerVolumeStates(state,
                            volumeDesc, serviceTask));
            return;
        }

        for (String resourceName : state.resourceNames) {
            createContainerVolumeState(state, volumeDesc, resourceName, taskCallback);
        }
    }

    private void createContainerVolumeState(ContainerVolumeAllocationTaskState state,
            ContainerVolumeDescription volumeDescription,
            String resourceName, ServiceTaskCallback taskCallback) {

        assertNotNull(state, "state");
        assertNotNull(volumeDescription, "volumeDescription");
        assertNotEmpty(resourceName, "resourceName");
        assertNotNull(taskCallback, "taskCallback");

        final ContainerVolumeState volumeState = new ContainerVolumeState();
        volumeState.documentSelfLink = buildResourceId(resourceName);
        volumeState.name = resourceName;
        volumeState.tenantLinks = state.tenantLinks;
        volumeState.descriptionLink = state.resourceDescriptionLink;
        volumeState.customProperties = state.customProperties;

        volumeState.driver = volumeDescription.driver;
        // volumeState.options = volumeDescription.options;
        volumeState.documentExpirationTimeMicros = ServiceUtils
                .getDefaultTaskExpirationTimeInMicros();

        String contextId;
        if (state.customProperties != null && (contextId = state.customProperties
                .get(FIELD_NAME_CONTEXT_ID_KEY)) != null) {
            volumeState.compositeComponentLink = UriUtils.buildUriPath(
                    CompositeComponentFactoryService.SELF_LINK, contextId);
        }

        sendRequest(OperationUtil
                .createForcedPost(this, ContainerVolumeService.FACTORY_LINK)
                .setBody(volumeState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask("System failure creating ContainerVolumeStates", e);
                                return;
                            }

                            ContainerVolumeState body = o
                                    .getBody(ContainerVolumeState.class);
                            logInfo("Created ContainerVolumeState: %s ",
                                    body.documentSelfLink);

                            completeSubTasksCounter(taskCallback, e);
                        }));
    }

    private Set<String> buildResourceLinks(ContainerVolumeAllocationTaskState state) {
        logInfo("Generate provisioned resourceLinks");
        Set<String> resourceLinks = new HashSet<>(state.resourceNames.size());
        for (String resourceName : state.resourceNames) {
            String volumeLink = buildResourceLink(resourceName);
            resourceLinks.add(volumeLink);
        }
        return resourceLinks;
    }

    private String buildResourceLink(String resourceName) {
        return UriUtils.buildUriPath(ContainerVolumeService.FACTORY_LINK,
                buildResourceId(resourceName));
    }

    private String buildResourceId(String resourceName) {
        return resourceName.replaceAll(" ", "-");
    }

}
