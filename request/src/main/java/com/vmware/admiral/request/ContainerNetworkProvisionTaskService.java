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

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.NetworkOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.request.ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState.SubStage;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter.HostSelection;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;

/**
 * Task implementing the provisioning of a container network.
 */
public class ContainerNetworkProvisionTaskService
        extends
        AbstractTaskStatefulService<ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState, ContainerNetworkProvisionTaskService.ContainerNetworkProvisionTaskState.SubStage> {

    // TODO add logic to start the service
    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_CONTAINER_NETWORK_TASKS;

    public static final String DISPLAY_NAME = "Container Network Provision";

    public static class ContainerNetworkProvisionTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerNetworkProvisionTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            PROVISIONING,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING));
        }

        /** (Required) Type of resource to create. */
        @Documentation(description = "Type of resource to create.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public String resourceType;

        /** (Required) Number of resources to provision. */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Long resourceCount;

        /** (Required) Links to already allocated resources that are going to be provisioned. */
        @Documentation(description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public List<String> resourceLinks;

        /** (Required) List of ComputeStates on which the networks will be created on. */
        @Documentation(description = "List of ComputeStates on which the networks will be created on.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public List<HostSelection> hostSelections;

        /** (Required) Reference to the adapter that will fulfill the provision request. */
        @Documentation(description = "Reference to the adapter that will fulfill the provision request.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public URI instanceAdapterReference;

        // Service use fields:

        /**
         * (Internal) Maps network links to selected hosts on which the networks will be created.
         */
        @Documentation(description = "Maps network links to selected hosts on which the networks will be created.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.SERVICE_USE, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public Map<String, HostSelection> resourceLinkToHostSelection;

    }

    public ContainerNetworkProvisionTaskService() {
        super(ContainerNetworkProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ContainerNetworkProvisionTaskState state) {
        assertNotEmpty(state.resourceType, "resourceType");
        assertNotNull(state.instanceAdapterReference, "instanceAdapterReference");
        assertNotNull(state.resourceLinks, "resourceLinks");
        assertNotNull(state.hostSelections, "hostSelections");

        if (state.resourceCount < 1) {
            throw new IllegalArgumentException("'resourceCount' must be greater than 0.");
        }

        if (state.resourceCount != state.resourceLinks.size()) {
            throw new IllegalArgumentException(
                    "size of 'resourceLinks' must be equal to 'resourcesCount'");
        }

        if (state.hostSelections.size() != state.resourceLinks.size()) {
            throw new IllegalArgumentException(
                    "size of 'hostSelections' must be equal to size of 'resourceLinks'");
        }
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerNetworkProvisionTaskState patchBody,
            ContainerNetworkProvisionTaskState currentState) {

        currentState.hostSelections = mergeProperty(
                currentState.hostSelections, patchBody.hostSelections);

        currentState.resourceLinkToHostSelection = mergeProperty(
                currentState.resourceLinkToHostSelection, patchBody.resourceLinkToHostSelection);

        currentState.instanceAdapterReference = mergeProperty(
                currentState.instanceAdapterReference, patchBody.instanceAdapterReference);

        currentState.resourceLinks = mergeLists(
                currentState.resourceLinks, patchBody.resourceLinks);

        currentState.resourceCount = mergeProperty(currentState.resourceCount,
                patchBody.resourceCount);

        return false;
    }

    @Override
    protected void handleStartedStagePatch(ContainerNetworkProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            state = prepareResourceLinksToHostSelectionMap(state);
            provisionNetworks(state, null);
            break;
        case PROVISIONING:
            break;
        case COMPLETED:
            complete(state, SubStage.COMPLETED);
            break;
        case ERROR:
            completeWithError(state, SubStage.ERROR);
            break;
        default:
            break;
        }
    }

    private ContainerNetworkProvisionTaskState prepareResourceLinksToHostSelectionMap(
            ContainerNetworkProvisionTaskState state) {
        assertNotNull(state.hostSelections, "hostSelections");

        final Map<String, HostSelection> resourceNameToHostSelection = selectHostPerResourceLink(
                state.resourceLinks, state.hostSelections);
        state.resourceLinkToHostSelection = resourceNameToHostSelection;
        return state;
    }

    private Map<String, HostSelection> selectHostPerResourceLink(Collection<String> resourceLinks,
            Collection<HostSelection> hostSelections) {
        AssertUtil.assertTrue(resourceLinks.size() <= hostSelections.size(),
                "There should be a selected host for each resource");

        Map<String, HostSelection> resourceLinkToHostSelection = new HashMap<String, HostSelection>();
        Iterator<String> rlIterator = resourceLinks.iterator();
        Iterator<HostSelection> hsIterator = hostSelections.iterator();
        while (rlIterator.hasNext() && hsIterator.hasNext()) {
            resourceLinkToHostSelection.put(rlIterator.next(), hsIterator.next());
        }

        return resourceLinkToHostSelection;
    }

    private void provisionNetworks(ContainerNetworkProvisionTaskState state,
            ServiceTaskCallback taskCallback) {

        if (taskCallback == null) {
            // create a counter subtask link first
            createCounterSubTaskCallback(state, state.resourceCount, false,
                    SubStage.COMPLETED,
                    (serviceTask) -> provisionNetworks(state, serviceTask));
            return;
        }

        logInfo("Provision request for %s networks", state.resourceCount);

        for (String networkLink : state.resourceLinks) {
            URI hostReference = getContainerHostReferenceForNetwork(state, networkLink);
            updateContainerNetworkStateWithContainerHostLink(networkLink, hostReference,
                    taskCallback,
                    () -> createAndSendContainerNetworkRequest(state, taskCallback, networkLink));
        }

        sendSelfPatch(createUpdateSubStageTask(state, SubStage.PROVISIONING));
    }

    private void updateContainerNetworkStateWithContainerHostLink(String networkSelfLink,
            URI originatingHostReference, ServiceTaskCallback taskCallback,
            Runnable callbackFunction) {

        ContainerNetworkState patch = new ContainerNetworkState();
        patch.originatingHostReference = originatingHostReference;

        sendRequest(Operation.createPatch(this, networkSelfLink)
                .setBody(patch)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logWarning("Error while updating network: %s", networkSelfLink);
                        completeSubTasksCounter(taskCallback, e);
                    } else {
                        callbackFunction.run();
                    }
                }));
    }

    private void createAndSendContainerNetworkRequest(ContainerNetworkProvisionTaskState state,
            ServiceTaskCallback taskCallback, String networkSelfLink) {

        AdapterRequest networkRequest = new AdapterRequest();
        networkRequest.resourceReference = UriUtils.buildUri(getHost(), networkSelfLink);
        networkRequest.serviceTaskCallback = taskCallback;
        networkRequest.operationTypeId = NetworkOperationType.CREATE.id;
        networkRequest.customProperties = state.customProperties;

        sendRequest(Operation.createPatch(state.instanceAdapterReference)
                .setBody(networkRequest)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("AdapterRequest failed for network: " + networkSelfLink, e);
                        return;
                    }
                    logInfo("Network provisioning started  for: " + networkSelfLink);
                }));
    }

    private URI getContainerHostReferenceForNetwork(ContainerNetworkProvisionTaskState state,
            String networkSelfLink) {
        String hostLink = state.resourceLinkToHostSelection.get(networkSelfLink).hostLink;
        return UriUtils.buildUri(getHost(), hostLink);
    }

}
