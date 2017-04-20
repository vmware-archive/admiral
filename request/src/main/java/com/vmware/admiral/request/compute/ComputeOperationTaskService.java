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

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.SINGLE_ASSIGNMENT;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.request.compute.ComputeOperationTaskService.ComputeOperationTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.admiral.service.common.TaskServiceDocument;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationRequest;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceType;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationUtils;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Task implementing Container post-provisioning (Day2) operation.
 */
public class ComputeOperationTaskService extends
        AbstractTaskStatefulService<ComputeOperationTaskService.ComputeOperationTaskState, ComputeOperationTaskService.ComputeOperationTaskState.SubStage> {

    public static final String DISPLAY_NAME = "Compute Operation";

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_RESOURCE_OPERATIONS;

    public static class ComputeOperationTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeOperationTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            COMPLETED,
            ERROR;
        }

        @Documentation(description = "The identifier of the resource operation to be performed.")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public String operation;

        @Documentation(description = "The resources on which the given operation will be applied")
        @PropertyOptions(usage = { SINGLE_ASSIGNMENT, REQUIRED }, indexing = STORE_ONLY)
        public Set<String> resourceLinks;
    }

    public ComputeOperationTaskService() {
        super(ComputeOperationTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    protected void handleStartedStagePatch(ComputeOperationTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            queryComputeResources(state);
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
    protected TaskStatusState fromTask(TaskServiceDocument<SubStage> state) {
        TaskStatusState statusTask = super.fromTask(state);
        ComputeOperationTaskState currentState = (ComputeOperationTaskState) state;
        statusTask.name = ContainerOperationType.extractDisplayName(currentState.operation);
        return statusTask;
    }

    private void queryComputeResources(ComputeOperationTaskState state) {
        List<Operation> operations = new ArrayList<>();
        for (String resourceLink : state.resourceLinks) {
            URI u = ComputeService.ComputeStateWithDescription
                    .buildUri(UriUtils.buildUri(getHost(), resourceLink));
            operations.add(Operation.createGet(u));
        }
        OperationJoin.create(operations).setCompletion((ops, exc) -> {
            if (exc != null) { // if there are exceptions and operation is not delete, we will fail.
                if (!ComputeOperationType.DELETE.id.equals(state.operation)) {
                    logSevere("Failed to get a computes",
                            Utils.toString(exc));
                    failTask("Some of the resources are not avaiable", null);
                    return;
                }
            }
            // because of the above logic, we are safe that if we have exception here, the operation
            // is delete, so we can skip them.
            List<ComputeStateWithDescription> computes = new ArrayList<>();
            for (Entry<Long, Operation> e : ops.entrySet()) {
                if (exc != null && exc.containsKey(e.getKey())) {
                    logWarning("No resource found to be removed with links: %s",
                            e.getValue().getUri());
                    continue;
                }
                computes.add(e.getValue().getBody(ComputeStateWithDescription.class));
            }

            if (computes.isEmpty()) {
                proceedTo(SubStage.COMPLETED);
            } else {
                performResourceOperations(state, computes, null);
            }
        }).sendWith(this);
    }

    private void performResourceOperations(ComputeOperationTaskState state,
            Collection<ComputeStateWithDescription> resources, ServiceTaskCallback taskCallback) {
        if (taskCallback == null) {
            createCounterSubTaskCallback(state, resources.size(), true, true,
                    DefaultSubStage.COMPLETED,
                    (serviceTask) -> performResourceOperations(state, resources, serviceTask));
            return;
        }

        try {
            logInfo("Starting %s of %d container resources", state.operation, resources.size());
            for (ComputeStateWithDescription compute : resources) {
                doAdapterRequest(state, compute, taskCallback);
            }
        } catch (Throwable e) {
            failTask("Unexpected exception while requesting operation: " + state.operation, e);
        }
    }

    private void doAdapterRequest(ComputeOperationTaskState state,
            ComputeStateWithDescription compute, ServiceTaskCallback taskCallback) {

        ComputeOperationType operationType = ComputeOperationType.instanceById(state.operation);
        URI resourceReference = UriUtils.buildUri(
                getHost().getPublicUri(), compute.documentSelfLink);
        URI callbackReference = UriUtils.buildUri(taskCallback.serviceSelfLink);
        DeferredResult<AdapterRequestMetadata> drMetadata;
        if (operationType != null) {
            drMetadata = DeferredResult.completed(AdapterRequestMetadata.of(
                    operationType.getAdapterReference(compute),
                    operationType.getBody(state, resourceReference, callbackReference)));
        } else {
            DeferredResult<ResourceOperationSpec> drLookup =
                    ResourceOperationUtils.lookUpByEndpointLink(
                            getHost(), getUri(),
                            compute.endpointLink, ResourceType.COMPUTE, state.operation);
            drMetadata = drLookup.thenApply(s -> {
                if (s == null) {
                    throw new IllegalArgumentException(
                            String.format("No operation %s, for compute: %s",
                                    state.operation, compute.documentSelfLink));
                }
                ResourceOperationRequest request = new ResourceOperationRequest();
                request.resourceReference = resourceReference;
                request.taskReference = callbackReference;
                request.operation = state.operation;
                request.isMockRequest = DeploymentProfileConfig.getInstance().isTest();
                return AdapterRequestMetadata.of(s.adapterReference, request);
            });
        }

        drMetadata.handle((metadata, ex) -> {
            invokeAdapter(state, compute, metadata, ex, taskCallback);
            return null;
        });

    }

    private void invokeAdapter(ComputeOperationTaskState state,
            ComputeStateWithDescription compute,
            AdapterRequestMetadata metadata, Throwable err,
            ServiceTaskCallback taskCallback) {
        if (metadata == null || err != null) {
            logWarning("Target compute %s, doesn't support %s operation",
                    compute.documentSelfLink,
                    state.operation);
            completeSubTasksCounter(taskCallback, err);
            return;
        }
        sendRequest(Operation.createPatch(metadata.adapterReference)
                .setBody(metadata.body)
                .setContextId(getSelfId())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        logSevere("Error when call adapter: %s "
                                        + "to perform operation: %s "
                                        + "for resource: %s."
                                        + " Cause: %s",
                                metadata.adapterReference,
                                state.operation,
                                compute.documentSelfLink,
                                Utils.toString(e));
                        completeSubTasksCounter(taskCallback, e);
                        return;
                    }
                }));
    }

    private static class AdapterRequestMetadata {
        URI adapterReference;
        Object body;

        static AdapterRequestMetadata of(URI adapterReference, Object body) {
            AdapterRequestMetadata ret = new AdapterRequestMetadata();
            ret.adapterReference = adapterReference;
            ret.body = body;
            return ret;
        }
    }
}
