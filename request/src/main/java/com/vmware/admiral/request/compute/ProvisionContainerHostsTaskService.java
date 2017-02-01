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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState;
import com.vmware.admiral.request.compute.ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState.SubStage;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.ComputeProperties;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState.TaskStage;

/** Task for provisioning a number of Docker Host VMs on AWS */
public class ProvisionContainerHostsTaskService
        extends
        AbstractTaskStatefulService<ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState, ProvisionContainerHostsTaskService.ProvisionContainerHostsTaskState.SubStage> {
    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_PROVISION_COMPUTE_CONTAINER_HOSTS;
    public static final String DISPLAY_NAME = "AWS Container Hosts";
    public static final String PROVISION_CONTAINER_HOSTS_OPERATION = "PROVISION_CONTAINER_HOSTS";

    private volatile EndpointState endpointState;

    public static class ProvisionContainerHostsTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ProvisionContainerHostsTaskState.SubStage> {
        public static final String FIELD_NAME_CUSTOM_PROP_CONTEXT_ID = "__contextId";

        public static enum SubStage {
            CREATED,
            DESCRIPTION_CREATED,
            ALLOCATING,
            ALLOCATED,
            PROVISIONING,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(ALLOCATING, PROVISIONING));
        }

        /** (Required) The AWS compute description link. */
        @PropertyOptions(usage = { PropertyUsageOption.LINK, PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public String endpointLink;

        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT },
                indexing = { PropertyIndexingOption.STORE_ONLY })
        public DockerHostDescription hostDescription;

        /** (Optional- default 1) Number of resources to provision. */
        @Documentation(description = "Number of resources to provision.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT }, indexing = {
                PropertyIndexingOption.STORE_ONLY })
        public long resourceCount;

        /** (Set by a Task) The compute resource links of the provisioned Docker Host VMs */
        @Documentation(description = "The compute resource links of the provisioned Docker Host VMs.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public Set<String> resourceLinks;

        @Documentation(description = "The description that defines the requested resource.")
        @PropertyOptions(usage = { PropertyUsageOption.SINGLE_ASSIGNMENT,
                PropertyUsageOption.AUTO_MERGE_IF_NOT_NULL }, indexing = {
                        PropertyIndexingOption.STORE_ONLY })
        public String computeDescriptionLink;
    }

    public static class DockerHostDescription extends ResourceState {
        public String instanceType;
        public String imageType;
        public int cpu;
        public int memory;
    }

    public ProvisionContainerHostsTaskService() {
        super(ProvisionContainerHostsTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void validateStateOnStart(ProvisionContainerHostsTaskState state)
            throws IllegalArgumentException {
        if (state.computeDescriptionLink == null && state.hostDescription == null) {
            throw new LocalizableValidationException(
                    "'computeDescriptionLink' or 'hostDescription' is required", "request.provision.links.empty");
        }
        if (state.hostDescription != null && (state.endpointLink == null || state.endpointLink.isEmpty())) {
            throw new LocalizableValidationException("'endpointLink' must not be empty", "request.provision.endpoint-link.empty");
        }

        if (state.resourceCount < 1) {
            throw new LocalizableValidationException("'resourceCount' must be greater than 0.", "request.resource-count.zero");
        }
    }

    @Override
    protected ServiceTaskCallbackResponse getFinishedCallbackResponse(
            ProvisionContainerHostsTaskState state) {
        CallbackCompleteResponse finishedResponse = new CallbackCompleteResponse();
        finishedResponse.copy(state.serviceTaskCallback.getFinishedResponse());
        finishedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated compute resources.");
        }
        return finishedResponse;
    }

    @Override
    protected ServiceTaskCallbackResponse getFailedCallbackResponse(
            ProvisionContainerHostsTaskState state) {
        CallbackCompleteResponse failedResponse = new CallbackCompleteResponse();
        failedResponse.copy(state.serviceTaskCallback.getFailedResponse(state.taskInfo.failure));
        failedResponse.resourceLinks = state.resourceLinks;
        if (state.resourceLinks == null || state.resourceLinks.isEmpty()) {
            logWarning("No resourceLinks found for allocated compute resources.");
        }
        return failedResponse;
    }

    protected static class CallbackCompleteResponse extends ServiceTaskCallbackResponse {
        Set<String> resourceLinks;
    }

    @Override
    protected void handleStartedStagePatch(ProvisionContainerHostsTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            if (state.computeDescriptionLink == null) {
                createComputeDescription(state, SubStage.DESCRIPTION_CREATED);
            } else {
                processComputeDescription(state, SubStage.DESCRIPTION_CREATED, null);
            }
            break;
        case DESCRIPTION_CREATED:
            createAllocationTask(state, this.endpointState);
            break;
        case ALLOCATING:
            break;
        case ALLOCATED:
            createComputeProvisionTaskState(state);
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

    private void createComputeDescription(ProvisionContainerHostsTaskState state,
            SubStage nextStage) {

        logInfo("Creating compute description: %s", state.hostDescription.name);
        ComputeDescription cd = new ComputeDescription();
        cd.name = state.hostDescription.name;
        cd.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.name()));

        cd.instanceType = state.hostDescription.instanceType;
        cd.customProperties = new HashMap<>();
        cd.customProperties
                .put(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
        cd.customProperties.put(ComputeConstants.CUSTOM_PROP_IMAGE_ID_NAME,
                state.hostDescription.imageType);

        sendRequest(Operation.createPost(this, ComputeDescriptionService.FACTORY_LINK)
                .setBody(cd)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask(String.format("Can't create compute description in task: %s",
                                getSelfLink()), e);
                        return;
                    }
                    String descLink = o.getBody(ComputeDescription.class).documentSelfLink;

                    logInfo("ComputeDescription created: %s", descLink);
                    proceedTo(nextStage, s -> {
                        s.computeDescriptionLink = descLink;
                    });
                }));
    }

    private void processComputeDescription(ProvisionContainerHostsTaskState state,
            SubStage nextStage, ComputeDescription cd) {

        if (cd == null) {
            getComputeDescription(state,
                    desc -> processComputeDescription(state, nextStage, desc));
            return;
        }
        logInfo("Validating compute description: %s", state.computeDescriptionLink);
        if (cd.supportedChildren == null
                || !cd.supportedChildren.contains(ComputeType.DOCKER_CONTAINER.name())) {
            cd.supportedChildren = new ArrayList<>(
                Arrays.asList(ComputeType.DOCKER_CONTAINER.name()));
        }
        if (cd.customProperties == null) {
            cd.customProperties = new HashMap<>();
        }
        cd.customProperties
                .put(ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME, "true");

        String endpointLink = cd.customProperties.get(ComputeProperties.ENDPOINT_LINK_PROP_NAME);

        sendRequest(Operation.createPatch(this, cd.documentSelfLink)
                .setBody(cd)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask(
                                String.format("Unable to patch compute description: %s in task: %s",
                                        cd.documentSelfLink, getSelfLink()),
                                e);
                        return;
                    }

                    logInfo("ComputeDescription updated: %s", cd.documentSelfLink);
                    proceedTo(nextStage, s -> {
                        s.endpointLink = endpointLink;
                    });
                }));
    }

    private void createComputeProvisionTaskState(ProvisionContainerHostsTaskState state) {
        ComputeProvisionTaskState ps = new ComputeProvisionTaskState();
        ps.documentSelfLink = getSelfId();
        ps.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.COMPLETED, TaskStage.STARTED, SubStage.ERROR);
        ps.customProperties = state.customProperties;
        ps.tenantLinks = state.tenantLinks;
        ps.requestTrackerLink = state.requestTrackerLink;
        ps.resourceLinks = state.resourceLinks;

        triggerTask(state, ComputeProvisionTaskService.FACTORY_LINK,
                ComputeProvisionTaskService.DISPLAY_NAME, ps,
                SubStage.PROVISIONING);
    }

    private void createAllocationTask(ProvisionContainerHostsTaskState state,
            EndpointState endpointState) {
        if (endpointState == null && state.endpointLink != null) {
            getEndpoint(state, (endpoint) -> createAllocationTask(state, endpoint));
            return;
        }

        ComputeAllocationTaskState cats = new ComputeAllocationTaskState();

        cats.documentSelfLink = getSelfId();
        cats.serviceTaskCallback = ServiceTaskCallback.create(getSelfLink(),
                TaskStage.STARTED, SubStage.ALLOCATED, TaskStage.STARTED, SubStage.ERROR);

        cats.resourceDescriptionLink = state.computeDescriptionLink;
        cats.resourcePoolLink = endpointState != null ? endpointState.resourcePoolLink : null;
        cats.resourceCount = state.resourceCount;

        cats.requestTrackerLink = state.requestTrackerLink;
        cats.tenantLinks = state.tenantLinks;
        if (state.customProperties != null) {
            cats.customProperties = state.customProperties;
        } else {
            cats.customProperties = new HashMap<>();
        }
        cats.customProperties.put(RequestUtils.FIELD_NAME_ALLOCATION_REQUEST,
                "true");
        cats.customProperties.put(
                ProvisionContainerHostsTaskState.FIELD_NAME_CUSTOM_PROP_CONTEXT_ID, getSelfId());

        triggerTask(state, ComputeAllocationTaskService.FACTORY_LINK,
                ComputeAllocationTaskService.DISPLAY_NAME, cats,
                SubStage.ALLOCATING);
    }

    private void getEndpoint(ProvisionContainerHostsTaskState state,
            Consumer<EndpointState> callbackFunction) {
        if (this.endpointState != null) {
            callbackFunction.accept(this.endpointState);
            return;
        }

        sendRequest(Operation.createGet(this, state.endpointLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving endpoint state", e);
                        return;
                    }

                    EndpointState endpoint = o.getBody(EndpointState.class);
                    this.endpointState = endpoint;
                    callbackFunction.accept(this.endpointState);
                }));
    }

    private void getComputeDescription(ProvisionContainerHostsTaskState state,
            Consumer<ComputeDescription> callbackFunction) {
        sendRequest(Operation.createGet(this, state.computeDescriptionLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving Compute description state", e);
                        return;
                    }

                    ComputeDescription description = o.getBody(ComputeDescription.class);
                    callbackFunction.accept(description);
                }));
    }

    private void triggerTask(ProvisionContainerHostsTaskState state,
            String factoryLink, String displayName, ServiceDocument body, SubStage nextStage) {
        sendRequest(Operation.createPost(this, factoryLink)
                .setBody(body)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask(String.format("Unable to trigger %s task from: %s ", displayName,
                                state.documentSelfLink), e);
                        return;
                    }
                    logInfo("%s task was triggered from: %s", displayName, state.documentSelfLink);
                    proceedTo(nextStage);
                }));
    }
}
