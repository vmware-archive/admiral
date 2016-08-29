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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigFactoryService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerHostNetworkConfigState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorState;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.container.util.ServiceLinkProcessor;
import com.vmware.admiral.request.ContainerServiceLinkProcessingTaskService.ContainerServiceLinkProcessingTaskState;
import com.vmware.admiral.request.ContainerServiceLinkProcessingTaskService.ContainerServiceLinkProcessingTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.admiral.service.test.MockSystemContainerConfig;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Task processing the serviceLinks property of a container and generates environment variables for
 * the links pointing to the named containers within the same context
 */
public class ContainerServiceLinkProcessingTaskService extends
        AbstractTaskStatefulService<ContainerServiceLinkProcessingTaskState,
        ContainerServiceLinkProcessingTaskState.SubStage> {
    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_SERVICE_LINKS_TASKS;
    public static final String DISPLAY_NAME = "Service Link Processing";

    public static class ContainerServiceLinkProcessingTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ContainerServiceLinkProcessingTaskState.SubStage> {

        private static final String FIELD_NAME_CONTEXT_ID = "contextId";
        private static final String FIELD_NAME_CONTAINER_DESCRIPTION = "containerDescription";
        private static final String FIELD_NAME_CONTAINER_STATES = "containerStates";

        /** (required) the current context */
        public String contextId;

        /** (required) the ContainerDescription to process */
        public ContainerDescription containerDescription;

        /** (required) List of the container state that need service links processing */
        public List<ContainerState> containerStates;

        /** (Set by the task.) The result of this task is a map of container service links configurations */
        public Map<String, ContainerServiceLinksConfig> containerServiceLinksConfigs;

        enum SubStage {
            CREATED,
            UPDATE_NETWORK_CONFIGRATION,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(UPDATE_NETWORK_CONFIGRATION));
        }
    }

    public ContainerServiceLinkProcessingTaskService() {
        super(ContainerServiceLinkProcessingTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ContainerServiceLinkProcessingTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            fetchDepContainerDescriptions(state);
            break;
        case UPDATE_NETWORK_CONFIGRATION:
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

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ContainerServiceLinkProcessingTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.containerServiceLinksConfigs = state.containerServiceLinksConfigs;
        return finishedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        public Map<String, ContainerServiceLinksConfig> containerServiceLinksConfigs;
    }

    private void fetchDepContainerDescriptions(ContainerServiceLinkProcessingTaskState state) {
        if (state.containerDescription.links == null
                || state.containerDescription.links.length == 0) {
            logInfo("No dependencies needed for [%s]", state.containerDescription.name);
            sendSelfPatch(createUpdateSubStageTask(state, SubStage.COMPLETED));
            return;
        }

        ServiceLinkProcessor processor;
        try {
            processor = new ServiceLinkProcessor(state.containerDescription.links);

        } catch (Exception x) {
            failTask(String.format("Failed to process service links for: %s",
                    state.containerDescription.documentSelfLink), x);

            return;
        }

        QueryTask contDescQuery = QueryUtil.buildQuery(ContainerDescription.class, false);

        QueryUtil.addExpandOption(contDescQuery);
        QueryUtil.addListValueClause(contDescQuery, ContainerDescription.FIELD_NAME_NAME,
                processor.getDependencyNames());

        new ServiceDocumentQuery<ContainerDescription>(getHost(), ContainerDescription.class)
                .query(contDescQuery, (r) -> {
                    if (r.hasException()) {
                        failTask(String.format("Failed fetching dependencies %s for %s",
                                processor.getDependencyNames(),
                                state.containerDescription.documentSelfLink), r.getException());

                    } else if (r.hasResult()) {
                        ContainerDescription depDesc = r.getResult();
                        processor.putDepContainerDescriptions(depDesc);

                    } else {
                        // make sure that all needed descriptions were found
                        try {
                            processor.validateDepContainerDescriptions();

                        } catch (Exception x) {
                            failTask(String.format("Missing dependencies for "
                                    + "ContainerDescription: %s",
                                    state.containerDescription.documentSelfLink), x);

                            return;
                        }

                        fetchDependencyStates(state, processor);
                    }
                });
    }

    private void fetchDependencyStates(ContainerServiceLinkProcessingTaskState state,
            ServiceLinkProcessor processor) {

        QueryTask contStateQuery = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK, UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, state.contextId),
                ContainerState.FIELD_NAME_POWER_STATE, PowerState.RUNNING.name());

        QueryUtil.addExpandOption(contStateQuery);
        QueryUtil.addListValueClause(contStateQuery, ContainerState.FIELD_NAME_DESCRIPTION_LINK,
                processor.getDepContainerDescriptionLinks());

        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(
                        contStateQuery,
                        (r) -> {
                            try {
                                if (r.hasException()) {
                                    String errMsg = String
                                            .format(
                                                    "Exception while querying containers with contextId [%s]",
                                                    state.contextId);
                                    failTask(errMsg, r.getException());

                                } else if (r.hasResult()) {
                                    ContainerState depContainerState = r.getResult();
                                    processor.putDepContainerState(depContainerState);

                                } else {
                                    try {
                                        // make sure that all needed ContainerStates were found
                                        processor.validateDepContainerStates();

                                    } catch (Exception x) {
                                        failTask(String.format("Missing dependencies for "
                                                + "ContainerDescription: %s",
                                                state.containerDescription.documentSelfLink), x);

                                        return;
                                    }

                                    fetchDependencyHosts(state, processor);
                                }
                            } catch (Exception e) {
                                String errMsg = String
                                        .format(
                                                "Exception while processing service links for containers with contextId [%s]",
                                                state.contextId);
                                failTask(errMsg, e);
                            }
                        });
    }

    private void fetchDependencyHosts(ContainerServiceLinkProcessingTaskState state,
            ServiceLinkProcessor processor) {

        QueryTask depComputeQuery = QueryUtil.buildQuery(ComputeState.class, false);
        QueryUtil.addExpandOption(depComputeQuery);
        QueryUtil.addListValueClause(depComputeQuery, ComputeState.FIELD_NAME_SELF_LINK,
                processor.getDepParentComputeLinks());

        new ServiceDocumentQuery<ComputeState>(getHost(), ComputeState.class)
                .query(depComputeQuery,
                        (r) -> {
                            try {
                                if (r.hasException()) {
                                    failTask(
                                            "Failed fetching the parent computes of dependenices for: "
                                                    + processor.getDepParentComputeLinks(),
                                            r.getException());

                                } else if (r.hasResult()) {
                                    ComputeState depCompute = r.getResult();
                                    processor.putDepParentCompute(depCompute);

                                } else {
                                    try {
                                        processor.validateDepParentComputes();

                                    } catch (Exception x) {
                                        failTask(String.format(
                                                "Missing parent computes for depdencies of: %s",
                                                state.containerDescription.documentSelfLink), x);
                                        return;
                                    }

                                    fetchNetworkAgentContainers(state, processor);
                                }
                            } catch (Exception e) {
                                String errMsg = String
                                        .format(
                                                "Exception while processing service links fetch dependency hosts with contextId [%s]",
                                                state.contextId);
                                failTask(errMsg, e);
                            }
                        });
    }

    private void fetchNetworkAgentContainers(ContainerServiceLinkProcessingTaskState state,
            ServiceLinkProcessor processor) {
        Set<String> hostLinks = new HashSet<>();
        for (ContainerState container : state.containerStates) {
            hostLinks.add(container.parentLink);
        }

        if (DeploymentProfileConfig.getInstance().isTest()) {
            for (String hostLink : hostLinks) {
                processor.putHostNetworkAgentAddress(hostLink,
                        MockSystemContainerConfig.NETWORK_ADDRESS);
            }
            updateHostNetworkConfigurations(state, processor);

            return;
        }

        List<Operation> operations = new ArrayList<>();

        for (String hostLink : hostLinks) {
            String hostId = Service.getId(hostLink);

            String coreAgentContainerDocumentLink = SystemContainerDescriptions
                    .getSystemContainerSelfLink(
                            SystemContainerDescriptions.AGENT_CONTAINER_NAME, hostId);

            List<String> command = new ArrayList<>();
            command.add("/docker-ip.sh");
            ShellContainerExecutorState executorState = new ShellContainerExecutorState();
            executorState.command = command.toArray(new String[command.size()]);

            operations.add(Operation
                    .createPost(UriUtils.extendUriWithQuery(UriUtils.buildUri(getHost(),
                            ShellContainerExecutorService.SELF_LINK),
                            ShellContainerExecutorService.CONTAINER_LINK_URI_PARAM,
                            coreAgentContainerDocumentLink))
                    .setBody(executorState)
                    .setCompletion((op, ex) -> {
                        if (ex == null) {
                            String commandOutput = op.getBody(String.class);
                            if (commandOutput == null
                                    || commandOutput.trim().length() == 0) {
                                failTask("Agent address empty", new IllegalStateException(
                                        "Agent address empty"));
                            }
                            commandOutput = commandOutput.trim();
                            processor.putHostNetworkAgentAddress(hostLink, commandOutput);
                        }
                    }));

        }

        OperationJoin.create(operations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Failed retrieving agent address: " + Utils.toString(exs), null);
                return;
            }
            updateHostNetworkConfigurations(state, processor);
        }).sendWith(this);

    }

    private void updateHostNetworkConfigurations(ContainerServiceLinkProcessingTaskState state,
            ServiceLinkProcessor processor) {
        Map<String, ContainerHostNetworkConfigState> hostNetworkConfigs = ContainerHostNetworkConfigService
                .processHostNetworkConfigs(state.containerStates, processor);

        List<Operation> operations = new ArrayList<>();

        for (Entry<String, ContainerHostNetworkConfigState> entry : hostNetworkConfigs
                .entrySet()) {
            String hostId = entry.getKey();
            ContainerHostNetworkConfigState patchNetworkConfig = entry.getValue();

            if (patchNetworkConfig.containerNetworkConfigs.isEmpty()) {
                continue;
            }

            String hostNetworkConfigLink = UriUtils.buildUriPath(
                    ContainerHostNetworkConfigFactoryService.SELF_LINK, hostId);

            operations.add(Operation
                    .createPatch(this, hostNetworkConfigLink)
                    .setBody(patchNetworkConfig));
        }

        if (operations.isEmpty()) {
            processDependencies(state, processor);
            return;
        }
        OperationJoin.create(operations).setCompletion((ops, exs) -> {
            if (exs != null) {
                failTask("Failure patching host network config: " + Utils.toString(exs), null);
                return;
            }
            processDependencies(state, processor);
        }).sendWith(this);

        sendSelfPatch(createUpdateSubStageTask(state, SubStage.UPDATE_NETWORK_CONFIGRATION));
    }

    private void processDependencies(ContainerServiceLinkProcessingTaskState state,
            ServiceLinkProcessor processor) {

        ContainerServiceLinkProcessingTaskState body =
                createUpdateSubStageTask(state, SubStage.COMPLETED);

        Map<String, ContainerServiceLinksConfig> containerServiceLinksConfigs = new HashMap<>();
        for (ContainerState container : state.containerStates) {
            ContainerServiceLinksConfig containerServiceLinkConfig = new ContainerServiceLinksConfig();
            containerServiceLinkConfig.extraHosts = mergeExtraHosts(container,
                    state.containerDescription,
                    processor);

            containerServiceLinksConfigs
                    .put(container.documentSelfLink, containerServiceLinkConfig);

            logFine("Added %s new extra hosts for container %s while processing service links.",
                    Arrays.toString(containerServiceLinkConfig.extraHosts),
                    container.documentSelfLink);
        }

        body.containerServiceLinksConfigs = containerServiceLinksConfigs;

        sendSelfPatch(body);
    }

    private String[] mergeExtraHosts(ContainerState container,
            ContainerDescription containerDescription, ServiceLinkProcessor processor) {

        Map<String, String> newExtraHosts = processor.generateExtraHosts(container.parentLink);
        if (newExtraHosts.isEmpty()) {
            // no mappings added
            logInfo("No extra hosts added for %s", container.documentSelfLink);
            return containerDescription.extraHosts;
        }

        // convert the existing extraHosts to a map to avoid duplicating hosts
        Map<String, String> existingExtraHosts = new HashMap<>();
        if (containerDescription.extraHosts != null) {
            existingExtraHosts.putAll(Arrays.stream(containerDescription.extraHosts)
                    .map((extraHost) -> extraHost.split(":", 2))
                    .collect(Collectors.toMap((parts) -> parts[0], (parts) -> parts[1])));
        }

        Map<String, String> mergedExtraHosts = new HashMap<>(existingExtraHosts);
        mergedExtraHosts.putAll(newExtraHosts);

        if (mergedExtraHosts.equals(existingExtraHosts)) {
            logFine("No changes needed for extra hosts: %s",
                    container.documentSelfLink);

            return containerDescription.extraHosts;
        }

        String[] extraHosts = mergedExtraHosts.entrySet().stream()
                .map((e) -> String.format("%s:%s", e.getKey(), e.getValue()))
                .toArray(String[]::new);

        return extraHosts;
    }

    @Override
    protected void validateStateOnStart(ContainerServiceLinkProcessingTaskState state)
            throws IllegalArgumentException {

        assertNotNull(state.containerDescription, "containerDescription");
        assertNotEmpty(state.contextId, "contextId");
        assertNotEmpty(state.containerStates, "containerStates");
    }

    @Override
    protected boolean validateStageTransition(Operation patch,
            ContainerServiceLinkProcessingTaskState patchBody,
            ContainerServiceLinkProcessingTaskState currentState) {

        currentState.contextId = mergeProperty(currentState.contextId, patchBody.contextId);
        currentState.containerDescription = mergeProperty(currentState.containerDescription,
                patchBody.containerDescription);
        currentState.containerStates = mergeProperty(currentState.containerStates,
                patchBody.containerStates);
        currentState.containerServiceLinksConfigs = mergeProperty(
                currentState.containerServiceLinksConfigs, patchBody.containerServiceLinksConfigs);

        return false;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();

        setDocumentTemplateIndexingOptions(template, EnumSet.of(PropertyIndexingOption.STORE_ONLY),
                ContainerServiceLinkProcessingTaskState.FIELD_NAME_CONTEXT_ID,
                ContainerServiceLinkProcessingTaskState.FIELD_NAME_CONTAINER_DESCRIPTION,
                ContainerServiceLinkProcessingTaskState.FIELD_NAME_CONTAINER_STATES);

        setDocumentTemplateUsageOptions(template,
                EnumSet.of(PropertyUsageOption.SINGLE_ASSIGNMENT),
                ContainerServiceLinkProcessingTaskState.FIELD_NAME_CONTEXT_ID,
                ContainerServiceLinkProcessingTaskState.FIELD_NAME_CONTAINER_DESCRIPTION,
                ContainerServiceLinkProcessingTaskState.FIELD_NAME_CONTAINER_STATES);

        return template;
    }

    /**
     * Configuration for linking container services per container.
     */
    public static class ContainerServiceLinksConfig {
        public String[] extraHosts;
    }
}
