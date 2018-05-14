/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.kubernetes;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeCustomProperties;
import static com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent.CUSTOM_PROPERTY_HOST_LINK;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SERVICE_USE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.ApplicationOperationType;
import com.vmware.admiral.adapter.common.ApplicationRequest;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ResourceType;
import com.vmware.admiral.compute.container.CompositeComponentRegistry;
import com.vmware.admiral.compute.container.CompositeComponentRegistry.ComponentMeta;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.ReservationTaskService.ReservationTaskState;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.request.kubernetes.CompositeKubernetesProvisioningTaskService.KubernetesProvisioningTaskState.SubStage;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
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
 * Task implementing the provisioning of a kubernetes composite component.
 */
public class CompositeKubernetesProvisioningTaskService extends
        AbstractTaskStatefulService<CompositeKubernetesProvisioningTaskService.KubernetesProvisioningTaskState, CompositeKubernetesProvisioningTaskService.KubernetesProvisioningTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_COMPOSITE_KUBERNETES_TASKS;

    public static final String DISPLAY_NAME = "Kubernetes Composite Provision";

    public static final String APP_NAME = "__APP_NAME";

    // cached
    private volatile CompositeComponent compositeComponent;

    // cached
    private volatile CompositeDescription compositeDescription;

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
    }

    public static class KubernetesProvisioningTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<KubernetesProvisioningTaskState.SubStage> {

        public static enum SubStage {
            CREATED, CONTEXT_PREPARED, RESERVING, RESERVED, PLACEMENT_HOST_SELECTED, COMPLETED, ERROR
        }

        /**
         * (Required) The description that defines the requested resource.
         */
        @PropertyOptions(usage = { PropertyUsageOption.REQUIRED,
                PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = STORE_ONLY)
        public String resourceDescriptionLink;

        /**
         * (Required) The link of the composite component that will be provisioned.
         */
        @Documentation(
                description = "The link of the composite component that will be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED,
                PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String compositeComponentLink;

        // Service use fields:
        /**
         * (Internal) Set by task with KuberneteskDescription name.
         */
        @Documentation(description = "Set by task with KuberneteskDescription name.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE,
                PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public String descName;

        /**
         * (Internal) Set by task after the ComputeState is found to host the entities
         */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public List<HostSelection> hostSelections;

        /**
         * (Internal) the groupResourcePlacementState that links to ResourcePool
         */
        @PropertyOptions(usage = { SERVICE_USE, AUTO_MERGE_IF_NOT_NULL }, indexing = STORE_ONLY)
        public String groupResourcePlacementLink;

    }

    public CompositeKubernetesProvisioningTaskService() {
        super(KubernetesProvisioningTaskState.class, KubernetesProvisioningTaskState.SubStage.class,
                DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(KubernetesProvisioningTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            prepareContext(state, null);
            break;
        case CONTEXT_PREPARED:
            if (state.groupResourcePlacementLink == null
                    || state.groupResourcePlacementLink.isEmpty()) {
                createReservationTasks(state, null);
            } else {
                proceedTo(SubStage.RESERVED);
            }
            break;
        case RESERVING:
            break;
        case RESERVED:
            if (state.hostSelections == null || state.hostSelections.isEmpty()) {
                selectPlacementComputeHost(state, null);
            } else {
                // in specific cases when the host is pre-selected
                // (ex: installing agents directly to a host, this step is not needed)
                proceedTo(SubStage.PLACEMENT_HOST_SELECTED);
            }
            break;
        case PLACEMENT_HOST_SELECTED:
            patchCompositeComponentWithHostLink(state, null);
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
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            KubernetesProvisioningTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        return finishedResponse;
    }

    @Override
    protected ServiceTaskCallbackResponse getFailedCallbackResponse(
            KubernetesProvisioningTaskState state) {
        CallbackCompleteResponse failedResponse = new CallbackCompleteResponse();
        failedResponse.copy(state.serviceTaskCallback.getFailedResponse(state.taskInfo.failure));
        return failedResponse;
    }

    @Override
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        final TaskStatusState statusTask = super.fromTask(state);
        statusTask.name = ((KubernetesProvisioningTaskState) state).descName;

        return statusTask;
    }

    private void createReservationTasks(KubernetesProvisioningTaskState state,
            CompositeDescription desc) {

        if (desc == null) {
            getCompositeDescription(state, (cd) -> createReservationTasks(state, cd));
            return;
        }

        ReservationTaskState rsrvTask = new ReservationTaskState();
        rsrvTask.documentSelfLink = getSelfId();
        rsrvTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.RESERVED, TaskStage.STARTED, SubStage.ERROR);

        rsrvTask.resourceCount = desc.descriptionLinks.size();
        rsrvTask.tenantLinks = state.tenantLinks;
        rsrvTask.resourceType = ResourceType.CONTAINER_TYPE.getName();
        rsrvTask.resourceDescriptionLink = state.resourceDescriptionLink;
        rsrvTask.customProperties = mergeCustomProperties(
                state.customProperties, desc.customProperties);
        rsrvTask.requestTrackerLink = state.requestTrackerLink;

        if (state.groupResourcePlacementLink != null) {
            rsrvTask.groupResourcePlacementLink = state.groupResourcePlacementLink;
            rsrvTask.taskSubStage = ReservationTaskState.SubStage.RESERVATION_SELECTED;
            rsrvTask.resourcePoolsPerGroupPlacementLinks = new LinkedHashMap<>(0);
        }

        sendRequest(Operation.createPost(this, ReservationTaskFactoryService.SELF_LINK)
                .setBody(rsrvTask)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating reservation task", e);
                        return;
                    }
                    proceedTo(SubStage.RESERVING);
                }));
    }

    private void selectPlacementComputeHost(KubernetesProvisioningTaskState state,
            String resourcePoolLink) {
        if (resourcePoolLink == null) {
            getResourcePool(state,
                    (rsPoolLink) -> selectPlacementComputeHost(state, rsPoolLink));
            return;
        }

        // create placement selection tasks
        PlacementHostSelectionTaskState placementTask = new PlacementHostSelectionTaskState();
        placementTask.documentSelfLink = getSelfId();
        placementTask.resourceDescriptionLink = state.resourceDescriptionLink;
        placementTask.resourcePoolLinks = new ArrayList<>();
        placementTask.resourcePoolLinks.add(resourcePoolLink);
        placementTask.resourceCount = 1;
        placementTask.resourceType = ResourceType.CONTAINER_TYPE.getName();
        placementTask.tenantLinks = state.tenantLinks;
        placementTask.customProperties = state.customProperties;
        placementTask.contextId = getContextId(state);
        placementTask.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.PLACEMENT_HOST_SELECTED,
                TaskStage.STARTED, SubStage.ERROR);
        placementTask.requestTrackerLink = state.requestTrackerLink;

        sendRequest(Operation.createPost(this, PlacementHostSelectionTaskService.FACTORY_LINK)
                .setBody(placementTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure creating placement task", e);
                        return;
                    }
                }));
    }

    private void getResourcePool(KubernetesProvisioningTaskState state,
            Consumer<String> callbackFunction) {
        sendRequest(Operation.createGet(this, state.groupResourcePlacementLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving GroupResourcePlacement", e);
                        return;
                    }

                    GroupResourcePlacementState placementState = o
                            .getBody(GroupResourcePlacementState.class);
                    if (placementState.resourcePoolLink == null) {
                        failTask(null, new LocalizableValidationException(
                                "Placement state has no resourcePoolLink",
                                "request.container.allocation.missing.resource-pool"));
                        return;
                    }
                    callbackFunction.accept(placementState.resourcePoolLink);
                }));
    }

    private void prepareContext(KubernetesProvisioningTaskState state, CompositeComponent cc) {
        assertNotNull(state, "state");

        if (cc == null) {
            getCompositeComponent(state, (c -> prepareContext(state, c)));
            return;
        }

        proceedTo(SubStage.CONTEXT_PREPARED, s -> {
            if (s.getCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY) == null) {
                s.addCustomProperty(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY, getSelfId());
            }
            s.descName = cc.name;
        });
    }

    private void createAdapterRequest(KubernetesProvisioningTaskState state) {
        ApplicationRequest adapterRequest = new ApplicationRequest();
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(),
                state.compositeComponentLink);
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED,
                TaskStage.STARTED, SubStage.ERROR);
        adapterRequest.operationTypeId = ApplicationOperationType.CREATE.id;
        adapterRequest.customProperties = state.customProperties;

        sendRequest(
                Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_KUBERNETES_APPLICATION)
                        .setBody(adapterRequest)
                        .setContextId(getSelfId())
                        .setCompletion((o, e) -> {
                            if (e != null) {
                                failTask(
                                        "AdapterRequest failed for composite component: "
                                                + state.compositeComponentLink,
                                        e);
                                return;
                            }
                            logInfo("Kubernetes provisioning started for: %s",
                                    state.compositeComponentLink);
                        }));
    }

    private void patchCompositeComponentWithHostLink(KubernetesProvisioningTaskState state,
            CompositeComponent cc) {
        if (cc == null) {
            getCompositeComponent(state, (component) -> patchCompositeComponentWithHostLink
                    (state, component));
            return;
        }

        if (cc.customProperties == null) {
            cc.customProperties = new HashMap<>();
        }

        cc.customProperties.put(CUSTOM_PROPERTY_HOST_LINK, state.hostSelections.get(0)
                .hostLink);

        sendRequest(Operation
                .createPatch(this, state.compositeComponentLink)
                .setBody(cc)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        failTask("Failure when patching composite component", ex);
                    } else {
                        createAdapterRequest(state);
                    }
                }));
    }

    private void getCompositeComponent(KubernetesProvisioningTaskState state,
            Consumer<CompositeComponent> callbackFunction) {
        if (compositeComponent != null) {
            callbackFunction.accept(compositeComponent);
            return;
        }

        sendRequest(Operation.createGet(this, state.compositeComponentLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving composite component", e);
                        return;
                    }

                    CompositeComponent c = o.getBody(CompositeComponent.class);
                    this.compositeComponent = c;
                    callbackFunction.accept(c);
                }));
    }

    private void getCompositeDescription(KubernetesProvisioningTaskState state,
            Consumer<CompositeDescription> callbackFunction) {
        if (compositeDescription != null) {
            callbackFunction.accept(compositeDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving composite description", e);
                        return;
                    }

                    CompositeDescription cd = o.getBody(CompositeDescription.class);
                    this.compositeDescription = cd;
                    callbackFunction.accept(cd);
                }));
    }

    public static boolean supportsCompositeDescription(CompositeDescription cd) {
        long kubernetesDescriptionCounter = cd.descriptionLinks.stream()
                .filter(link -> isKubernetesType(link)).count();
        return kubernetesDescriptionCounter == cd.descriptionLinks.size();
    }

    private static boolean isKubernetesType(String descriptionLink) {
        ComponentMeta meta = CompositeComponentRegistry.metaByDescriptionLink(descriptionLink);
        ResourceType resourceType = ResourceType.fromName(meta.resourceType);

        switch (resourceType) {
        case KUBERNETES_GENERIC_TYPE:
        case KUBERNETES_DEPLOYMENT_TYPE:
        case KUBERNETES_POD_TYPE:
        case KUBERNETES_REPLICATION_CONTROLLER_TYPE:
        case KUBERNETES_SERVICE_TYPE:
            return true;
        default:
            return false;
        }
    }
}
