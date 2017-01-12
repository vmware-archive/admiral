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
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.request.ReservationAllocationTaskService.CONTAINER_HOST_ID_CUSTOM_PROPERTY;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.compute.container.network.ContainerNetworkService;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState.PowerState;
import com.vmware.admiral.compute.container.network.Ipam;
import com.vmware.admiral.compute.container.network.NetworkUtils;
import com.vmware.admiral.request.ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState.SubStage;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ResourceNamePrefixService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

/**
 * Task implementing the allocation of a container network.
 */
public class ContainerNetworkAllocationTaskService extends
        AbstractTaskStatefulService<ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState, ContainerNetworkAllocationTaskService.ContainerNetworkAllocationTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_CONTAINER_NETWORK_ALLOCATION_TASKS;

    public static final String DISPLAY_NAME = "Container Network Allocation";

    // cached network description
    private volatile ContainerNetworkDescription networkDescription;

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    public static class ContainerNetworkAllocationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerNetworkAllocationTaskState.SubStage> {

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

        /** (Internal) Set by task with ContainerNetworkDescription name. */
        @Documentation(description = "Set by task with ContainerNetworkDescription name.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
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
        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.",
                    "request.resource-count.zero");
        }

        List<String> providedHostIds = getProvidedHostIds(state);

        if (providedHostIds != null) {
            state.resourceCount = state.resourceCount * providedHostIds.size();
        }
    }

    @Override
    protected void handleStartedStagePatch(ContainerNetworkAllocationTaskState state) {
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
            createContainerNetworkStates(state, null, null);
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

    private Set<String> buildResourceLinks(Set<String> resourceNames) {
        logInfo("Generate provisioned resourceLinks");
        Set<String> resourceLinks = new HashSet<>(resourceNames.size());
        for (String resourceName : resourceNames) {
            String networkLink = NetworkUtils.buildNetworkLink(resourceName);
            resourceLinks.add(networkLink);
        }
        return resourceLinks;
    }

    private void prepareContext(ContainerNetworkAllocationTaskState state,
            ContainerNetworkDescription networkDescription) {
        assertNotNull(state, "state");

        if (networkDescription == null) {
            getContainerNetworkDescription(state,
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

    private void createResourcePrefixNameSelectionTask(ContainerNetworkAllocationTaskState state,
            ContainerNetworkDescription networkDescription) {

        assertNotNull(state, "state");

        if (networkDescription == null) {
            getContainerNetworkDescription(state,
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

        if (Boolean.TRUE.equals(networkDescription.external)) {
            completeSubTasksCounter(taskCallback, null);
            return;
        }

        try {
            final ContainerNetworkState networkState = new ContainerNetworkState();
            networkState.documentSelfLink = NetworkUtils.buildNetworkId(resourceName);
            networkState.name = resourceName;
            networkState.tenantLinks = state.tenantLinks;
            networkState.descriptionLink = state.resourceDescriptionLink;
            networkState.customProperties = state.customProperties;
            networkState.ipam = networkDescription.ipam;
            networkState.driver = networkDescription.driver;

            if (state.customProperties != null) {
                String networkDriver = state.customProperties
                        .remove(ContainerNetworkDescription.CUSTOM_PROPERTY_NETWORK_DRIVER);
                if (networkDriver != null) {
                    networkState.driver = networkDriver;
                }

                String ipamDriver = state.customProperties
                        .remove(ContainerNetworkDescription.CUSTOM_PROPERTY_IPAM_DRIVER);
                if (ipamDriver != null) {
                    if (networkState.ipam == null) {
                        networkState.ipam = new Ipam();
                    }
                    networkState.ipam.driver = ipamDriver;
                }
            }

            networkState.external = (getProvidedHostIds(state) != null);

            networkState.powerState = PowerState.PROVISIONING;

            networkState.options = networkDescription.options;
            networkState.documentExpirationTimeMicros = ServiceUtils
                    .getDefaultTaskExpirationTimeInMicros();
            networkState.adapterManagementReference = networkDescription.instanceAdapterReference;

            String contextId;
            if (!networkState.external && state.customProperties != null
                    && (contextId = state.customProperties
                            .get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY)) != null) {
                networkState.compositeComponentLinks = new ArrayList<>();
                networkState.compositeComponentLinks.add(UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, contextId));
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
            failTask("System failure creating ContainerNetworkState", e);
        }
    }

    private void updateResourcesAndComplete(ContainerNetworkAllocationTaskState state) {
        getContainerNetworkDescription(state,
                (networkDescription) -> {
                    complete(SubStage.COMPLETED, s -> {
                        s.resourceLinks = buildResourceLinks(
                                Boolean.TRUE.equals(networkDescription.external)
                                        ? Collections.singleton(networkDescription.name)
                                        : state.resourceNames);
                    });
                });
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

    public static List<String> getProvidedHostIds(TaskServiceDocument<?> state) {
        String hostId = state.getCustomProperty(CONTAINER_HOST_ID_CUSTOM_PROPERTY);
        if (hostId == null || hostId.isEmpty()) {
            return null;
        }
        // Can be a a single id or comma separated values of ids (last part of the selfLink)
        return Arrays.asList(hostId.split(","));
    }

    public static List<String> getProvidedHostIdsAsSelfLinks(TaskServiceDocument<?> state) {
        List<String> ids = getProvidedHostIds(state);
        if (ids == null) {
            return null;
        }
        return ids.stream()
                .map((id) -> UriUtils.buildUriPath(ComputeService.FACTORY_LINK, id))
                .collect(Collectors.toList());
    }

}
