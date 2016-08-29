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
import static com.vmware.admiral.common.util.AssertUtil.assertTrue;
import static com.vmware.admiral.common.util.PropertyUtils.mergeProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigFactoryService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerHostNetworkConfigState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ExposedServiceDescriptionService.ExposedServiceDescriptionFactoryService;
import com.vmware.admiral.compute.container.ExposedServiceDescriptionService.ExposedServiceDescriptionState;
import com.vmware.admiral.compute.container.ServiceAddressConfig;
import com.vmware.admiral.compute.container.util.ExposeServiceProcessor;
import com.vmware.admiral.request.ContainerExposeServiceProcessingTaskService.ContainerExposeServiceProcessingTaskState;
import com.vmware.admiral.request.ContainerExposeServiceProcessingTaskService.ContainerExposeServiceProcessingTaskState.SubStage;
import com.vmware.admiral.request.ResourceNamePrefixTaskService.ResourceNamePrefixTaskState;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.QueryTaskClientHelper;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Task processing the {@link ContainerDescription#exposeService} property of a {@link ContainerDescription} and generates service links
 * configuration for use by network agent, so that the service can be publicly accessible.
 */
public class ContainerExposeServiceProcessingTaskService extends
        AbstractTaskStatefulService<ContainerExposeServiceProcessingTaskState,
        SubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_EXPOSE_SERVICE_TASKS;
    public static final String DISPLAY_NAME = "Expose Service Processing";
    private static final String SERVICE_ALIAS_PLACEHOLDER = "%s";

    private static final String EXPOSE_SERVICE_ID_FORMAT = "%s_%s";

    public static class ContainerExposeServiceProcessingTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerExposeServiceProcessingTaskState.SubStage> {

        private static final String FIELD_NAME_CONTEXT_ID = "contextId";
        private static final String FIELD_NAME_CONTAINER_DESCRIPTION = "containerDescription";
        private static final String FIELD_NAME_CONTAINER_STATES = "containerStates";

        /** (required) the current context */
        public String contextId;

        /** (required) the ContainerDescription to process */
        public ContainerDescription containerDescription;

        /** (required) containers that are part of the service (cluster) */
        public List<ContainerState> containerStates;

        /** (Internal) Set by task after resource name prefixes requested. */
        public List<String> resourceNames;

        /** (Internal) Set by task from existing container states or from container description after resource names have been generated */
        public ExposedServiceDescriptionState exposedServiceState;

        enum SubStage {
            CREATED,
            EXPOSED_SERVICE_ALIASES_NAMED,
            EXPOSED_SERVICE_UPDATED,
            UPDATING_NETWORK_CONFIGRATION,
            UPDATING_NETWORK_CONFIGRATION_COMPLETED,
            UPDATING_CONTAINER_STATES,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(UPDATING_NETWORK_CONFIGRATION, UPDATING_CONTAINER_STATES));
        }
    }

    public ContainerExposeServiceProcessingTaskService() {
        super(ContainerExposeServiceProcessingTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ContainerExposeServiceProcessingTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            retrieveOrCreateExposedService(state);
            break;
        case EXPOSED_SERVICE_ALIASES_NAMED:
            createExposedServices(state);
            break;
        case EXPOSED_SERVICE_UPDATED:
            fetchContainerHosts(state);
            break;
        case UPDATING_NETWORK_CONFIGRATION:
            break;
        case UPDATING_NETWORK_CONFIGRATION_COMPLETED:
            updateContainerStates(state);
            break;
        case UPDATING_CONTAINER_STATES:
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

    private void createExposedServicePrefixNameSelectionTask(
            ContainerExposeServiceProcessingTaskState state) {
        // create resource prefix name selection tasks
        ResourceNamePrefixTaskState namePrefixTask = new ResourceNamePrefixTaskState();
        namePrefixTask.documentSelfLink = getSelfId() + "-exposed";
        namePrefixTask.resourceCount = state.containerDescription.exposeService.length;
        namePrefixTask.baseResourceNameFormat = SERVICE_ALIAS_PLACEHOLDER;
        namePrefixTask.tenantLinks = state.tenantLinks;
        namePrefixTask.customProperties = state.customProperties;
        namePrefixTask.serviceTaskCallback = ServiceTaskCallback.create(state.documentSelfLink,
                TaskStage.STARTED, SubStage.EXPOSED_SERVICE_ALIASES_NAMED,
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

    private void createExposedServices(ContainerExposeServiceProcessingTaskState state) {
        assertTrue(
                state.resourceNames.size() == state.containerDescription.exposeService.length,
                "Size of public service aliases and genreated resource names not same");

        Iterator<String> resourceNamesIterator = state.resourceNames.iterator();

        ExposedServiceDescriptionState exposedServiceState = new ExposedServiceDescriptionState();
        exposedServiceState.documentSelfLink = getExposedServiceLink(state);
        exposedServiceState.hostLink = selectOwnerHostLink(state);

        List<ServiceAddressConfig> addressConfigs = new ArrayList<ServiceAddressConfig>();

        for (ServiceAddressConfig config : state.containerDescription.exposeService) {
            String resourceName = resourceNamesIterator.next();

            ServiceAddressConfig newConfig = new ServiceAddressConfig();

            try {
                newConfig.address = ServiceAddressConfig
                        .formatAddress(config.address, resourceName);
            } catch (Exception e) {
                failTask("Failure formatting address.", e);
                return;
            }
            newConfig.port = config.port;

            addressConfigs.add(newConfig);
        }

        exposedServiceState.addressConfigs = addressConfigs
                .toArray(new ServiceAddressConfig[addressConfigs.size()]);

        sendRequest(Operation.createPost(this, ExposedServiceDescriptionFactoryService.SELF_LINK)
                .setBody(exposedServiceState)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        failTask("Failure creating exposed service state.", ex);
                        return;
                    }

                    ExposedServiceDescriptionState body = op
                            .getBody(ExposedServiceDescriptionState.class);
                    updateStateWithExposedService(state, body);
                }));
    }

    private String getExposedServiceLink(ContainerExposeServiceProcessingTaskState state) {
        String id = String.format(EXPOSE_SERVICE_ID_FORMAT,
                Service.getId(state.containerDescription.documentSelfLink), state.contextId);

        return UriUtilsExtended.buildUriPath(ExposedServiceDescriptionFactoryService.SELF_LINK, id);
    }

    private void retrieveOrCreateExposedService(ContainerExposeServiceProcessingTaskState state) {
        String exposedServiceLink = getExposedServiceLink(state);

        // If exposed service was already created for this group of containers, reuse when reconfiguring.
        // This will happen when scaling existing containers. Otherwise, create a new config state.

        retrieveExposedService(state, exposedServiceLink, (result) -> {
            if (result != null) {
                updateStateWithExposedService(state, result);
            } else {
                createExposedServicePrefixNameSelectionTask(state);
            }
        });
    }

    private void retrieveExposedService(ContainerExposeServiceProcessingTaskState state,
            String exposedServiceLink, Consumer<ExposedServiceDescriptionState> callback) {
        final AtomicReference<ExposedServiceDescriptionState> document = new AtomicReference<>();
        QueryTaskClientHelper.create(ExposedServiceDescriptionState.class)
                .setDocumentLink(exposedServiceLink)
                .setResultHandler((r, ex) -> {
                    if (ex != null) {
                        failTask("Failure retrieving exposed service state.", ex);
                        return;
                    } else if (r.hasResult()) {
                        document.set(r.getResult());
                    } else {
                        callback.accept(document.get());
                    }
                }).sendWith(getHost());
    }

    private void updateStateWithExposedService(ContainerExposeServiceProcessingTaskState state,
            ExposedServiceDescriptionState exposedServiceState) {

        ContainerExposeServiceProcessingTaskState newState = createUpdateSubStageTask(
                state,
                SubStage.EXPOSED_SERVICE_UPDATED);
        newState.exposedServiceState = exposedServiceState;

        sendSelfPatch(newState);
    }

    private void fetchContainerHosts(ContainerExposeServiceProcessingTaskState state) {
        ExposeServiceProcessor processor = new ExposeServiceProcessor(state.containerStates);

        Set<String> hostLinks = new HashSet<>();
        for (ContainerState container : state.containerStates) {
            hostLinks.add(container.parentLink);
        }

        if (hostLinks.isEmpty()) {
            IllegalStateException e = new IllegalStateException("Unexpected host links to be empty");
            failTask(e.getMessage(), e);
        }

        List<Operation> operations = new ArrayList<>();

        for (String hostLink : hostLinks) {
            operations.add(Operation.createGet(this, hostLink));
        }

        OperationJoin.create(operations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Failed retrieving hosts: " + Utils.toString(exs), null);
                return;
            }
            for (Operation op : ops.values()) {
                ComputeState computeState = op.getBody(ComputeState.class);
                processor.putComputeState(computeState);
            }
            updateContainerNetworkConfigurations(state, processor);
        }).sendWith(this);
    }

    private void updateContainerNetworkConfigurations(
            ContainerExposeServiceProcessingTaskState state,
            ExposeServiceProcessor processor) {
        sendSelfPatch(createUpdateSubStageTask(state, SubStage.UPDATING_NETWORK_CONFIGRATION));

        Map<String, ContainerHostNetworkConfigState> hostNetworkConfigs = null;
        try {
            hostNetworkConfigs = ContainerHostNetworkConfigService.processHostNetworkConfigs(
                    state.containerStates, state.exposedServiceState, processor);
        } catch (Exception e) {
            failTask(String.format("Failed to generate public network configs for: %s",
                    state.containerDescription.documentSelfLink), e);
            return;
        }

        List<Operation> operations = new ArrayList<>();

        for (Entry<String, ContainerHostNetworkConfigState> entry : hostNetworkConfigs
                .entrySet()) {

            String hostId = entry.getKey();
            ContainerHostNetworkConfigState patchNetworkConfig = entry.getValue();

            String hostNetworkConfigLink = UriUtils.buildUriPath(
                    ContainerHostNetworkConfigFactoryService.SELF_LINK, hostId);

            operations.add(Operation.createPatch(this, hostNetworkConfigLink)
                    .setBody(patchNetworkConfig));
        }

        OperationJoin.create(operations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Failed patching network configs: " + Utils.toString(exs),
                        null);
                return;
            }
            sendSelfPatch(createUpdateSubStageTask(state,
                    SubStage.UPDATING_NETWORK_CONFIGRATION_COMPLETED));
        }).sendWith(this);
    }

    private void updateContainerStates(ContainerExposeServiceProcessingTaskState state) {
        sendSelfPatch(createUpdateSubStageTask(state, SubStage.UPDATING_CONTAINER_STATES));

        List<Operation> operations = new ArrayList<>();

        for (ContainerState container : state.containerStates) {

            ContainerState patchContainerState = new ContainerState();
            patchContainerState.exposedServiceLink = state.exposedServiceState.documentSelfLink;

            operations.add(Operation.createPatch(this, container.documentSelfLink)
                    .setBody(patchContainerState));
        }

        OperationJoin.create(operations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Failed patching container states: " + Utils.toString(exs),
                        null);
                return;
            }
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
        }).sendWith(this);
    }

    @Override
    protected void validateStateOnStart(ContainerExposeServiceProcessingTaskState state)
            throws IllegalArgumentException {

        assertNotEmpty(state.contextId, "contextId");
        assertNotNull(state.containerDescription, "containerDescription");
        assertNotEmpty(state.containerDescription.exposeService,
                "containerDescription.exposeService");
        assertNotEmpty(state.containerStates, "containerStates");
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerExposeServiceProcessingTaskState patchBody,
            ContainerExposeServiceProcessingTaskState currentState) {

        currentState.contextId = mergeProperty(currentState.contextId, patchBody.contextId);
        currentState.containerDescription = mergeProperty(currentState.containerDescription,
                patchBody.containerDescription);
        currentState.containerStates = mergeProperty(currentState.containerStates,
                patchBody.containerStates);
        currentState.resourceNames = mergeProperty(currentState.resourceNames,
                patchBody.resourceNames);
        currentState.exposedServiceState = mergeProperty(currentState.exposedServiceState,
                patchBody.exposedServiceState);

        return false;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ContainerExposeServiceProcessingTaskState.FIELD_NAME_CONTEXT_ID,
                ContainerExposeServiceProcessingTaskState.FIELD_NAME_CONTAINER_DESCRIPTION,
                ContainerExposeServiceProcessingTaskState.FIELD_NAME_CONTAINER_STATES);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ContainerExposeServiceProcessingTaskState.FIELD_NAME_CONTEXT_ID,
                ContainerExposeServiceProcessingTaskState.FIELD_NAME_CONTAINER_DESCRIPTION,
                ContainerExposeServiceProcessingTaskState.FIELD_NAME_CONTAINER_STATES);

        return template;
    }

    // Selects one of all available hosts as an owner of the service. It will be the public
    // entry point. Since all hosts will be able to handle service requests and load balance them,
    // we want to select only one that will serve external clients' requests.
    private static String selectOwnerHostLink(ContainerExposeServiceProcessingTaskState state) {
        Map<String, Integer> hostLinksContainerCount = getHostLinksContainerCount(state);
        return getHostLinkWithMaxContainerCount(hostLinksContainerCount);
    }

    private static Map<String, Integer> getHostLinksContainerCount(
            ContainerExposeServiceProcessingTaskState state) {
        Map<String, Integer> result = new HashMap<>();

        for (ContainerState container : state.containerStates) {
            Integer count = result.get(container.parentLink);
            if (count == null) {
                count = 0;
            }

            result.put(container.parentLink, count + 1);
        }

        return result;
    }

    private static String getHostLinkWithMaxContainerCount(Map<String, Integer> hostsContainerCount) {
        String selectedHost = null;
        int maxContainerCount = 0;
        for (Entry<String, Integer> entry : hostsContainerCount.entrySet()) {
            if (entry.getValue() > maxContainerCount) {
                maxContainerCount = entry.getValue();
                selectedHost = entry.getKey();
            }
        }

        return selectedHost;
    }

}
