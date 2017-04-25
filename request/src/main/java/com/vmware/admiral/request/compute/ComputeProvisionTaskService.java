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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.request.compute.ComputeAllocationTaskService.ComputeAllocationTaskState;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState.SubStage;
import com.vmware.admiral.request.compute.enhancer.ComputeStateEnhancers;
import com.vmware.admiral.request.compute.enhancer.Enhancer.EnhanceContext;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest;
import com.vmware.photon.controller.model.adapterapi.ComputeInstanceRequest.InstanceRequestType;
import com.vmware.photon.controller.model.adapterapi.ResourceOperationResponse;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeStateWithDescription;
import com.vmware.photon.controller.model.tasks.ServiceTaskCallback;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Task implementing the provisioning of a compute resource.
 */
public class ComputeProvisionTaskService extends
        AbstractTaskStatefulService<ComputeProvisionTaskService.ComputeProvisionTaskState, ComputeProvisionTaskService.ComputeProvisionTaskState.SubStage> {

    private static final int WAIT_CONNECTION_RETRY_COUNT = 10;

    public static final String FACTORY_LINK = ManagementUriParts.REQUEST_COMPUTE_PROVISION_TASKS;

    public static final String DISPLAY_NAME = "Compute Provision";

    public static class ComputeProvisionTaskState
            extends
            com.vmware.admiral.service.common.TaskServiceDocument<ComputeProvisionTaskState.SubStage> {

        public static enum SubStage {
            CREATED,
            CUSTOMIZING_COMPUTE,
            PROVISIONING_COMPUTE,
            PROVISIONING_COMPUTE_COMPLETED,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING_COMPUTE));
        }

        /**
         * (Required) Links to already allocated resources that are going to be provisioned.
         */
        @Documentation(description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = STORE_ONLY, usage = { REQUIRED, SINGLE_ASSIGNMENT })
        public Set<String> resourceLinks;

        /**
         * Normalized error threshold between 0 and 1.0.
         */
        public double errorThreshold;

    }

    public ComputeProvisionTaskService() {
        super(ComputeProvisionTaskState.class, SubStage.class, DISPLAY_NAME);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.transientSubStages = SubStage.TRANSIENT_SUB_STAGES;
    }

    @Override
    protected void handleStartedStagePatch(ComputeProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            customizeCompute(state);
            break;
        case CUSTOMIZING_COMPUTE:
            provisionResources(state, null);
            break;
        case PROVISIONING_COMPUTE:
            break;
        case PROVISIONING_COMPUTE_COMPLETED:
            queryForProvisionedResources(state);
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

    private void customizeCompute(ComputeProvisionTaskState state) {
        URI referer = UriUtils.buildUri(getHost().getPublicUri(), getSelfLink());
        List<DeferredResult<Operation>> results = state.resourceLinks.stream()
                .map(link -> Operation.createGet(this, link))
                .map(o -> sendWithDeferredResult(o, ComputeState.class))
                .map(dr -> {
                    return dr.thenCompose(cs -> {
                        EnhanceContext context = new EnhanceContext();
                        context.endpointType = cs.customProperties
                                .get(ComputeConstants.CUSTOM_PROP_ENDPOINT_TYPE_NAME);
                        context.imageType = cs.customProperties.get("__requestedImageType");

                        return ComputeStateEnhancers.build(getHost(), referer).enhance(context, cs);
                    }).thenCompose(cs -> sendWithDeferredResult(
                            Operation.createPatch(this, cs.documentSelfLink).setBody(cs)));
                })
                .collect(Collectors.toList());

        DeferredResult.allOf(results).whenComplete((all, t) -> {
            if (t != null) {
                failTask("Error patching compute states", t);
                return;
            }
            proceedTo(SubStage.CUSTOMIZING_COMPUTE);
        });
    }

    @Override
    protected void validateStateOnStart(ComputeProvisionTaskState state)
            throws IllegalArgumentException {
        if (state.resourceLinks.isEmpty()) {
            throw new LocalizableValidationException("No compute instances to provision",
                    "request.compute.provision.empty");
        }
    }

    private void provisionResources(ComputeProvisionTaskState state, String subTaskLink) {
        try {
            Set<String> resourceLinks = state.resourceLinks;
            if (subTaskLink == null) {
                // recurse after creating a sub task
                createSubTaskForProvisionCallbacks(state);
                return;
            }
            boolean isMockRequest = DeploymentProfileConfig.getInstance().isTest();

            List<DeferredResult<Operation>> ops = resourceLinks.stream().map(
                    rl -> ComputeStateWithDescription.buildUri(UriUtils.buildUri(getHost(), rl)))
                    .map(uri -> sendWithDeferredResult(Operation.createGet(uri),
                            ComputeStateWithDescription.class))
                    .map(dr -> dr.thenCompose(c -> {
                        ComputeInstanceRequest cr = new ComputeInstanceRequest();
                        cr.resourceReference = UriUtils.buildUri(getHost(), c.documentSelfLink);
                        cr.requestType = InstanceRequestType.CREATE;
                        cr.taskReference = UriUtils.buildUri(getHost(), subTaskLink);
                        cr.isMockRequest = isMockRequest;
                        return sendWithDeferredResult(
                                Operation.createPatch(c.description.instanceAdapterReference)
                                        .setBody(cr))
                                .exceptionally(e -> {
                                    ResourceOperationResponse r = ResourceOperationResponse
                                            .fail(c.documentSelfLink, e);
                                    completeSubTask(subTaskLink, r);
                                    return null;
                                });
                    })).collect(Collectors.toList());

            DeferredResult.allOf(ops).whenComplete((all, e) -> {
                logInfo("Requested provisioning of %s compute resources.",
                        resourceLinks.size());
                proceedTo(SubStage.PROVISIONING_COMPUTE);
            });
        } catch (Throwable e) {
            failTask("System failure creating ContainerStates", e);
        }
    }

    private void createSubTaskForProvisionCallbacks(ComputeProvisionTaskState currentState) {

        ServiceTaskCallback<SubStage> callback = ServiceTaskCallback.create(getSelfLink());
        callback.onSuccessTo(SubStage.PROVISIONING_COMPUTE_COMPLETED);
        SubTaskService.SubTaskState<SubStage> subTaskInitState = new SubTaskService.SubTaskState<>();
        // tell the sub task with what to patch us, on completion
        subTaskInitState.serviceTaskCallback = callback;
        subTaskInitState.errorThreshold = currentState.errorThreshold;
        subTaskInitState.completionsRemaining = currentState.resourceLinks.size();
        subTaskInitState.tenantLinks = currentState.tenantLinks;
        Operation startPost = Operation
                .createPost(this, SubTaskService.FACTORY_LINK)
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failure creating sub task: %s",
                                        Utils.toString(e));
                                failTask("Failure creating sub task", e);
                                return;
                            }
                            SubTaskService.SubTaskState<?> body = o
                                    .getBody(SubTaskService.SubTaskState.class);
                            // continue, passing the sub task link
                            provisionResources(currentState, body.documentSelfLink);
                        });
        sendRequest(startPost);
    }

    private void completeSubTask(String subTaskLink, Object body) {
        Operation.createPatch(this, subTaskLink)
                .setBody(body)
                .setCompletion(
                        (o, ex) -> {
                            if (ex != null) {
                                logWarning("Unable to complete subtask: %s, reason: %s",
                                        subTaskLink, Utils.toString(ex));
                            }
                        })
                .sendWith(this);
    }

    private void queryForProvisionedResources(ComputeProvisionTaskState state) {
        Set<String> resourceLinks = state.resourceLinks;
        if (resourceLinks == null || resourceLinks.isEmpty()) {
            complete();
            return;
        }

        Builder queryBuilder = Query.Builder.create()
                .addInClause(ServiceDocument.FIELD_NAME_SELF_LINK, resourceLinks)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeAllocationTaskState.ENABLE_COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
        QueryTask.Builder queryTaskBuilder = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.EXPAND_CONTENT)
                .setQuery(queryBuilder.build());

        sendRequest(Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTaskBuilder.build())
                .setCompletion((o, e) -> {
                    if (e != null) {
                        failTask("Failure retrieving provisioned resources: " + Utils.toString(e),
                                null);
                        return;
                    }

                    QueryTask task = o.getBody(QueryTask.class);
                    if (task.results.documents == null || task.results.documents.isEmpty()) {
                        complete();
                        return;
                    }
                    validateConnectionsAndRegisterContainerHost(state,
                            task.results.documents.values().stream()
                                    .map(json -> Utils.fromJson(json, ComputeState.class))
                                    .collect(Collectors.toList()));
                }));
    }

    private void validateConnectionsAndRegisterContainerHost(ComputeProvisionTaskState state,
            List<ComputeState> computes) {
        URI specValidateUri = UriUtils.buildUri(getHost(), ContainerHostService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
        URI specUri = UriUtils.buildUri(getHost(), ContainerHostService.SELF_LINK);
        AtomicInteger remaining = new AtomicInteger(computes.size());
        AtomicReference<Throwable> error = new AtomicReference<>();
        for (ComputeState computeState : computes) {
            if (computeState.address == null) {
                if (DeploymentProfileConfig.getInstance().isTest()) {
                    computeState.address = "127.0.0.1";
                } else {
                    failTask("No IP address allocated on machine: " + computeState.name, null);
                    return;
                }
            }
            ContainerHostSpec spec = new ContainerHostSpec();
            spec.hostState = computeState;
            spec.acceptCertificate = true;
            waitUntilConnectionValid(specValidateUri, spec, 0, (ex) -> {
                if (error.get() != null) {
                    return;
                }

                if (ex != null) {
                    error.set(ex);
                    logWarning("Failed to validate container host connection: %s",
                            Utils.toString(ex));
                    failTask("Failed registering container host", error.get());
                    return;
                }

                spec.acceptHostAddress = true;
                registerContainerHost(state, specUri, remaining, spec, error);
            });
        }
    }

    private void registerContainerHost(ComputeProvisionTaskState state, URI specUri,
            AtomicInteger remaining, ContainerHostSpec spec, AtomicReference<Throwable> error) {

        Operation.createPut(specUri).setBody(spec).setCompletion((op, er) -> {
            if (error.get() != null) {
                return;
            }
            if (er != null) {
                error.set(er);
                failTask("Failed registering container host", er);
                return;
            }
            if (remaining.decrementAndGet() == 0) {
                complete();
            }
        }).sendWith(this);

    }

    private void waitUntilConnectionValid(URI specValidateUri, ContainerHostSpec spec,
            int retryCount, Consumer<Throwable> callback) {

        Operation.createPut(specValidateUri).setBody(spec).setCompletion((op, er) -> {
            if (er != null) {
                logSevere(er);
                if (retryCount > WAIT_CONNECTION_RETRY_COUNT) {
                    callback.accept(er);
                } else {
                    getHost().schedule(() -> waitUntilConnectionValid(specValidateUri, spec,
                            retryCount + 1, callback), 10, TimeUnit.SECONDS);
                }
            } else {
                callback.accept(null);
            }
        }).sendWith(this);
    }
}
