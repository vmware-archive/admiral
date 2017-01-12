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

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;
import static com.vmware.admiral.request.utils.RequestUtils.getContextId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
import com.vmware.admiral.compute.container.volume.VolumeBinding;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.admiral.request.ContainerVolumeProvisionTaskService.ContainerVolumeProvisionTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.PowerState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
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
        state.resourceCount = (long) state.resourceLinks.size();

        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
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

        getContainerVolumeDescription(state, (volumeDescription) -> {
            if (Boolean.TRUE.equals(volumeDescription.external)) {
                getVolumeByName(state, volumeDescription.name, (volumeState) -> {
                    updateContainerVolumeState(state, volumeState, () -> {
                        proceedTo(SubStage.COMPLETED, s -> {
                            // Small workaround to get the actual self link for discovered volumes...
                            s.customProperties = new HashMap<>();
                            s.customProperties.put("__externalVolumeSelfLink",
                                    volumeState.documentSelfLink);
                        });
                    });
                });
            } else {
                createTaskCallback(state, (taskCallback) -> {
                    state.instanceAdapterReference = volumeDescription.instanceAdapterReference;
                    selectHosts(state, volumeDescription, (hosts) -> {

                        if (hosts.size() == 1
                                || state.resourceLinks.size() == hosts.size()) {
                            Iterator<ComputeState> hostIt = hosts.iterator();
                            ComputeState host = hostIt.next();
                            for (String volumeLink : state.resourceLinks) {
                                provisionVolume(state, volumeLink, host, taskCallback);

                                if (hostIt.hasNext()) {
                                    host = hostIt.next();
                                }
                            }
                        } else {
                            String err = String.format(
                                    "Unexpected size of resource links and hosts, hosts should be one or equal to resource links! Actual resources - [%s], hosts - [%s]",
                                    state.resourceLinks.size(), hosts.size());
                            failTask(err, null);
                        }
                    });
                });
            }
        });

        proceedTo(SubStage.PROVISIONING);
    }

    private void getVolumeByName(ContainerVolumeProvisionTaskState state, String volumeName,
            Consumer<ContainerVolumeState> callback) {

        selectHost(state, (host) -> {

            List<ContainerVolumeState> volumeStates = new ArrayList<ContainerVolumeState>();

            QueryTask queryTask = VolumeUtil
                    .getVolumeByHostAndNameQueryTask(host.documentSelfLink, volumeName);

            new ServiceDocumentQuery<ContainerVolumeState>(getHost(), ContainerVolumeState.class)
                    .query(queryTask,
                            (r) -> {
                                if (r.hasException()) {
                                    failTask("Failed to query for active volume by name '"
                                            + volumeName + "' in host '" + host.documentSelfLink
                                            + "'!", r.getException());
                                } else if (r.hasResult()) {
                                    volumeStates.add(r.getResult());
                                } else {
                                    if (volumeStates.size() == 1) {
                                        callback.accept(volumeStates.get(0));
                                        return;
                                    }
                                    failTask(volumeStates.size()
                                            + " active volume(s) found by name '" + volumeName
                                            + "' in host '" + host.documentSelfLink + "'!", null);
                                }
                            });
        });
    }

    private void createTaskCallback(ContainerVolumeProvisionTaskState state,
            Consumer<ServiceTaskCallback> callbackFunction) {
        createCounterSubTaskCallback(
                state,
                state.resourceCount,
                false,
                SubStage.COMPLETED,
                (callback) -> {
                    callbackFunction.accept(callback);
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

        if ((containersByDescriptionLink == null) || (containersByDescriptionLink.isEmpty())) {
            callback.accept(Collections.emptyList());
            return;
        }

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
                Arrays.asList(cd.volumes).forEach((v) -> {
                    String volumeName = VolumeBinding.fromString(v).getHostPart();
                    if (volumeDescription.name.equalsIgnoreCase(volumeName)) {
                        result.addAll(containersByDescriptionLink.get(cd.documentSelfLink));
                    }
                });
            }
        }
        return result;
    }

    private void provisionVolume(ContainerVolumeProvisionTaskState state,
            String volumeLink, ComputeState host, ServiceTaskCallback taskCallback) {
        updateContainerVolumeStateWithContainerHostLink(volumeLink, host,
                () -> createAndSendContainerVolumeRequest(state, taskCallback, volumeLink));
    }

    private void updateContainerVolumeStateWithContainerHostLink(String volumeSelfLink,
            ComputeState host, Runnable callbackFunction) {

        ContainerVolumeState patch = new ContainerVolumeState();
        patch.originatingHostLink = host.documentSelfLink;
        patch.parentLinks = new ArrayList<>(Arrays.asList(host.documentSelfLink));

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

    private void updateContainerVolumeState(ContainerVolumeProvisionTaskState state,
            ContainerVolumeState currentVolumeState, Runnable callbackFunction) {

        ContainerVolumeState patch = new ContainerVolumeState();

        String contextId;
        if (state.customProperties != null && (contextId = state.customProperties
                .get(FIELD_NAME_CONTEXT_ID_KEY)) != null) {
            patch.compositeComponentLinks = currentVolumeState.compositeComponentLinks;
            if (patch.compositeComponentLinks == null) {
                patch.compositeComponentLinks = new ArrayList<>();
            }
            patch.compositeComponentLinks.add(UriUtils.buildUriPath(
                    CompositeComponentFactoryService.SELF_LINK, contextId));
        } else {
            callbackFunction.run();
            return;
        }

        sendRequest(Operation
                .createPatch(this, currentVolumeState.documentSelfLink)
                .setBody(patch)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                String errMsg = String.format("Error while updating volume: %s",
                                        currentVolumeState.documentSelfLink);
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
        if (Boolean.TRUE.equals(volumeDescription.external)) {
            // The volume is defined as external, just validate that it exists actually.
            volumeRequest.operationTypeId = VolumeOperationType.INSPECT.id;
        } else {
            volumeRequest.operationTypeId = VolumeOperationType.CREATE.id;
        }
        volumeRequest.customProperties = state.customProperties;

        sendRequest(Operation.createPatch(getHost(), state.instanceAdapterReference.toString())
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

    private void selectHosts(ContainerVolumeProvisionTaskState state,
            ContainerVolumeDescription volumeDescription, Consumer<List<ComputeState>> callback) {

        // If hosts are provided use them directly to try to provision the volume
        // (e.g. when External volume CRUD operations)
        List<String> providedHostLinks = ContainerVolumeAllocationTaskService
                .getProvidedHostIdsAsSelfLinks(state);

        if (providedHostLinks != null) {
            retrieveContainerHostsByLinks(state, providedHostLinks, (hosts) -> {
                List<String> disabledHosts = hosts.stream().filter((host) -> {
                    return host.powerState != PowerState.ON;
                }).map(host -> host.address).collect(Collectors.toList());

                if (disabledHosts.isEmpty()) {
                    callback.accept(hosts);
                } else {
                    String err = String.format(
                            "Requested volume provisioning for disabled hosts: [%s].",
                            disabledHosts);
                    failTask(err, null);
                }
            });
            return;
        }

        selectHost(state, (host) -> {
            callback.accept(Collections.singletonList(host));
        });
    }

    private void selectHost(ContainerVolumeProvisionTaskState state,
            Consumer<ComputeState> callback) {
        getContextContainerStates(state, (states) -> {
            getContextContainerDescriptions(states, (descriptions) -> {
                List<ContainerState> containerStatesForVolume = getDependantContainerStates(
                        descriptions, states, volumeDescription);
                if (containerStatesForVolume.isEmpty()) {
                    String err = String.format(
                            "No container states depending on volume description [%s] found.",
                            volumeDescription.name);
                    failTask(err, null);
                } else {
                    String hostLink = containerStatesForVolume.get(0).parentLink;
                    getHost(hostLink, (host) -> {
                        callback.accept(host);
                    });
                }
            });
        });
    }

    private void getHost(String hostLink, Consumer<ComputeState> callback) {
        Operation.createGet(this, hostLink).setCompletion((op, ex) -> {
            if (ex != null) {
                failTask("Failed retrieving host: " + Utils.toString(ex), null);
                return;
            }

            ComputeState host = op.getBody(ComputeState.class);
            callback.accept(host);
        }).sendWith(this);
    }

    private void retrieveContainerHostsByLinks(ContainerVolumeProvisionTaskState state,
            List<String> hostLinks,
            Consumer<List<ComputeState>> callbackFunction) {

        final List<ComputeState> result = new ArrayList<>();

        List<String> remainingHostLinks = new ArrayList<>(hostLinks);

        final QueryTask queryTask = QueryUtil.buildQuery(ComputeState.class, true);
        QueryUtil.addListValueClause(queryTask, ComputeState.FIELD_NAME_SELF_LINK, hostLinks);
        QueryUtil.addExpandOption(queryTask);

        new ServiceDocumentQuery<ComputeState>(getHost(), ComputeState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        failTask(String.format(
                                "Exception during retrieving hosts with links [%s]. Error: [%s]",
                                hostLinks,
                                Utils.toString(r.getException())), r.getException());
                    } else if (r.hasResult()) {
                        ComputeState cs = r.getResult();
                        result.add(cs);
                        remainingHostLinks.remove(cs.documentSelfLink);
                    } else {
                        if (!remainingHostLinks.isEmpty()) {
                            failTask(String.format(
                                    "Not all hosts were found! Remaining hosts: [%s]!",
                                    remainingHostLinks),
                                    r.getException());
                            return;
                        }

                        callbackFunction.accept(result);
                    }
                });
    }
}
