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

import static com.vmware.admiral.request.utils.RequestUtils.getContextId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.admiral.request.ContainerVolumeProvisionTaskService.ContainerVolumeProvisionTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task implementing the provisioning of a container volume.
 */
public class ContainerVolumeProvisionTaskService
        extends
        AbstractTaskStatefulService<ContainerVolumeProvisionTaskService.ContainerVolumeProvisionTaskState, ContainerVolumeProvisionTaskService.ContainerVolumeProvisionTaskState.SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_CONTAINER_VOLUME_TASKS;

    public static final String DISPLAY_NAME = "Container Volume Provision";

    // cached volume description
    private volatile ContainerVolumeDescription volumeDescription;

    public static class ContainerVolumeProvisionTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerVolumeProvisionTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            PROVISIONING,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING));
        }

        /** (Required) The description that defines the requested resource. */
        @Documentation(description = "Type of resource to create.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String resourceDescriptionLink;

        /** (Required) Type of resource to create. */
        @Documentation(description = "Type of resource to create.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String resourceType;

        /** (Required) Number of resources to provision. */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public Long resourceCount;

        /** (Required) Links to already allocated resources that are going to be provisioned. */
        @Documentation(description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;

        /** (Internal) Reference to the adapter that will fulfill the provision request. */
        @Documentation(description = "Reference to the adapter that will fulfill the provision request.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL })
        public URI instanceAdapterReference;

    }

    public ContainerVolumeProvisionTaskService() {
        super(ContainerVolumeProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ContainerVolumeProvisionTaskState state)
            throws IllegalArgumentException {
        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }

        if (state.resourceCount != state.resourceLinks.size()) {
            throw new IllegalArgumentException(
                    "Size of 'resourceLinks' must be equal to 'resourcesCount'");
        }

    }

    @Override
    protected void handleStartedStagePatch(ContainerVolumeProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            provisionVolumes(state);
            break;
        case PROVISIONING:
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

    private void provisionVolumes(ContainerVolumeProvisionTaskState state) {

        logInfo("Provision request for %s volumes", state.resourceCount);

        createTaskCallbackAndGetVolumeDescription(state, (taskCallback, volumeDescription) -> {
            state.instanceAdapterReference = volumeDescription.instanceAdapterReference;
            selectHostLink(state, volumeDescription, (hostLink) -> {
                for (String volumeLink : state.resourceLinks) {
                    provisionVolume(state, volumeLink, hostLink, taskCallback);
                }
            });
        });

        proceedTo(SubStage.PROVISIONING);
    }

    private void createTaskCallbackAndGetVolumeDescription(
            ContainerVolumeProvisionTaskState state,
            BiConsumer<ServiceTaskCallback, ContainerVolumeDescription> callbackFunction) {
        AtomicReference<ServiceTaskCallback> taskCallback = new AtomicReference<>();
        AtomicReference<ContainerVolumeDescription> volumeDescription = new AtomicReference<>();

        createCounterSubTaskCallback(
                state,
                state.resourceCount,
                false,
                SubStage.COMPLETED,
                (callback) -> {
                    taskCallback.set(callback);
                    ContainerVolumeDescription nd = volumeDescription.get();
                    if (nd != null) {
                        callbackFunction.accept(callback, nd);
                    }
                });

        getContainerVolumeDescription(state, (nd) -> {
            volumeDescription.set(nd);
            ServiceTaskCallback callback = taskCallback.get();
            if (callback != null) {
                callbackFunction.accept(callback, nd);
            }
        });
    }

    private void getContainerVolumeDescription(ContainerVolumeProvisionTaskState state,
            Consumer<ContainerVolumeDescription> callbackFunction) {
        if (volumeDescription != null) {
            callbackFunction.accept(volumeDescription);
            return;
        }

        sendRequest(Operation.createGet(this, state.resourceDescriptionLink)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                failTask("Failure retrieving container volume description.",
                                        e);
                                return;
                            }

                            ContainerVolumeDescription desc = o
                                    .getBody(ContainerVolumeDescription.class);
                            this.volumeDescription = desc;
                            callbackFunction.accept(desc);
                        }));
    }

    private void selectHostLink(ContainerVolumeProvisionTaskState state,
            ContainerVolumeDescription volumeDescription, Consumer<String> callback) {
        getContextContainerStates(
                state,
                (states) -> {
                    getContextContainerDescriptions(
                            states,
                            (descriptions) -> {
                                List<ContainerState> containerStatesForVolume = getDependantContainerStates(
                                        descriptions, states, volumeDescription);
                                if (containerStatesForVolume.isEmpty()) {
                                    String err = String
                                            .format("No container states depending on volume description [%s] found.",
                                                    volumeDescription.name);
                                    failTask(err, null);
                                } else {
                                    String hostLink = containerStatesForVolume.get(0).parentLink;
                                    callback.accept(hostLink);
                                }
                            });
                });
    }

    private void getContextContainerStates(ContainerVolumeProvisionTaskState state,
            Consumer<Map<String, List<ContainerState>>> callback) {
        String contextId = getContextId(state);

        QueryTask q = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK, UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, contextId));
        q.taskInfo.isDirect = false;
        QueryUtil.addExpandOption(q);

        Map<String, List<ContainerState>> containersByDescriptionLink = new HashMap<>();

        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(q,
                        (r) -> {
                            if (r.hasException()) {
                                failTask(
                                        "Exception while selecting containers with contextId ["
                                                + contextId + "]",
                                        r.getException());
                            } else if (r.hasResult()) {

                                ContainerState result = r.getResult();

                                List<ContainerState> containers = containersByDescriptionLink
                                        .get(result.descriptionLink);
                                if (containers == null) {
                                    containers = new ArrayList<>();
                                    containersByDescriptionLink.put(result.descriptionLink,
                                            containers);
                                }

                                containers.add(result);
                            } else {
                                callback.accept(containersByDescriptionLink);
                            }
                        });

    }

    private void getContextContainerDescriptions(
            Map<String, List<ContainerState>> containersByDescriptionLink,
            Consumer<List<ContainerDescription>> callback) {
        QueryTask q = QueryUtil.buildQuery(ContainerDescription.class, true);

        QueryUtil.addExpandOption(q);
        QueryUtil.addListValueClause(q, ContainerDescription.FIELD_NAME_SELF_LINK,
                containersByDescriptionLink.keySet());

        q.taskInfo.isDirect = false;

        List<ContainerDescription> result = new ArrayList<>();

        new ServiceDocumentQuery<ContainerDescription>(getHost(), ContainerDescription.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        failTask("Exception while selecting container descriptions",
                                r.getException());
                    } else if (r.hasResult()) {
                        result.add(r.getResult());
                    } else {
                        callback.accept(result);
                    }
                });

    }

    private List<ContainerState> getDependantContainerStates(
            List<ContainerDescription> containerDescriptions,
            Map<String, List<ContainerState>> containersByDescriptionLink,
            ContainerVolumeDescription volumeDescription) {
        List<ContainerState> result = new ArrayList<>();
        for (ContainerDescription cd : containerDescriptions) {
            if (cd.volumes != null && cd.volumes.length > 0) {
                Arrays.asList(cd.volumes).forEach((vn) -> {
                    if (VolumeUtil.parseVolumeHostDirectory(vn)
                            .equalsIgnoreCase(
                                    VolumeUtil.parseVolumeHostDirectory(volumeDescription.name))) {
                        result.addAll(containersByDescriptionLink.get(cd.documentSelfLink));
                    }
                });
            }
        }
        return result;
    }

    private void provisionVolume(ContainerVolumeProvisionTaskState state,
            String volumeLink, String hostLink, ServiceTaskCallback taskCallback) {
        updateContainerVolumeStateWithContainerHostLink(volumeLink, hostLink,
                () -> createAndSendContainerVolumeRequest(state, taskCallback, volumeLink));
    }

    private void updateContainerVolumeStateWithContainerHostLink(String volumeSelfLink,
            String originatingHostLink, Runnable callbackFunction) {

        ContainerVolumeState patch = new ContainerVolumeState();
        patch.originatingHostLink = originatingHostLink;

        sendRequest(Operation
                .createPatch(this, volumeSelfLink)
                .setBody(patch)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                String errMsg = String.format("Error while updating volume: %s",
                                        volumeSelfLink);
                                logWarning(errMsg);
                                failTask(errMsg, e);
                            } else {
                                callbackFunction.run();
                            }
                        }));
    }

    private void createAndSendContainerVolumeRequest(ContainerVolumeProvisionTaskState state,
            ServiceTaskCallback taskCallback, String volumeSelfLink) {

        AdapterRequest volumeRequest = new AdapterRequest();
        volumeRequest.resourceReference = UriUtils.buildUri(getHost(), volumeSelfLink);
        volumeRequest.serviceTaskCallback = taskCallback;
        volumeRequest.operationTypeId = VolumeOperationType.CREATE.id;
        volumeRequest.customProperties = state.customProperties;

        sendRequest(Operation.createPatch(state.instanceAdapterReference)
                .setBody(volumeRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("AdapterRequest failed for volume: " + volumeSelfLink, e);
                        return;
                    }
                    logInfo("Volume provisioning started  for: " + volumeSelfLink);
                }));
    }

}
