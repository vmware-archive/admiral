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

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.PropertyUtils.mergeLists;
import static com.vmware.admiral.compute.ComputeConstants.COMPUTE_CONFIG_CONTENT_PROP_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostSpec;
import com.vmware.admiral.request.compute.ComputeProvisionTaskService.ComputeProvisionTaskState.SubStage;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService;
import com.vmware.photon.controller.model.tasks.ProvisionComputeTaskService;
import com.vmware.photon.controller.model.tasks.SubTaskService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.OperationSequence;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.Utils;

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
            CUSTOMIZED_COMPUTE,
            PROVISIONING_COMPUTE,
            PROVISIONING_COMPUTE_COMPLETED,
            COMPLETED,
            ERROR;

            static final Set<SubStage> TRANSIENT_SUB_STAGES = new HashSet<>(
                    Arrays.asList(PROVISIONING_COMPUTE));
        }

        /** (Required) Links to already allocated resources that are going to be provisioned. */
        @Documentation(description = "Links to already allocated resources that are going to be provisioned.")
        @PropertyOptions(indexing = PropertyIndexingOption.STORE_ONLY, usage = {
                PropertyUsageOption.REQUIRED, PropertyUsageOption.SINGLE_ASSIGNMENT })
        public List<String> resourceLinks;

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
    protected void validateStateOnStart(ComputeProvisionTaskState state)
            throws IllegalArgumentException {
        assertNotNull(state.resourceLinks, "resourceLinks");
    }

    @Override
    protected boolean validateStageTransition(Operation patch, ComputeProvisionTaskState patchBody,
            ComputeProvisionTaskState currentState) {
        currentState.resourceLinks = mergeLists(
                currentState.resourceLinks, patchBody.resourceLinks);

        return false;
    }

    @Override
    protected void handleStartedStagePatch(ComputeProvisionTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            customizeCompute(state);
            break;
        case CUSTOMIZED_COMPUTE:
            provisionResources(state, null);
            break;
        case PROVISIONING_COMPUTE:
            break;
        case PROVISIONING_COMPUTE_COMPLETED:
            queryForProvisionedResources(state);
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

    private void customizeCompute(ComputeProvisionTaskState state) {

        OperationJoin.JoinedCompletionHandler getComputeCompletion = (opsGetComputes, exsGetComputes) -> {

            if (exsGetComputes != null && !exsGetComputes.isEmpty()) {
                failTask("Error retrieving compute states",
                        exsGetComputes.values().iterator().next());
            }

            Map<String, String> diskLinkToContent = new HashMap<>();

            Predicate<ComputeState> hasCustomConfigContent = cs -> cs.customProperties != null
                    && cs.customProperties.containsKey(COMPUTE_CONFIG_CONTENT_PROP_NAME);

            opsGetComputes.values().stream()
                    .map(op -> op.getBody(ComputeState.class))
                    .filter(hasCustomConfigContent)
                    .forEach(cs -> {
                        for (String diskLink : cs.diskLinks) {
                            diskLinkToContent.put(diskLink, cs.customProperties
                                    .get(COMPUTE_CONFIG_CONTENT_PROP_NAME));
                        }
                    });


            OperationJoin.JoinedCompletionHandler getDisksCompletion = (opsGetDisks, exsGetDisks) -> {
                if (exsGetDisks != null && !exsGetDisks.isEmpty()) {
                    failTask("Unable to get disk(s)", exsGetDisks.values().iterator().next());
                    return;
                }

                Stream<Operation> updateOperations = opsGetDisks.values().stream()
                        .map(op -> op.getBody(DiskService.DiskState.class))
                        .filter(diskState -> diskState.type == DiskService.DiskType.HDD)
                        .filter(diskState -> diskState.bootConfig != null
                                && diskState.bootConfig.files.length > 0)
                        .map(diskState -> {
                            diskState.bootConfig.files[0].contents = diskLinkToContent
                                    .get(diskState.documentSelfLink);
                            return Operation.createPut(this, diskState.documentSelfLink)
                                    .setBody(diskState);
                        });

                OperationJoin.create(updateOperations).setCompletion((opsUpdDisks, exsUpdDisks) -> {
                    if (exsUpdDisks != null && !exsUpdDisks.isEmpty()) {
                        failTask("Unable to update disk(s)",
                                exsUpdDisks.values().iterator().next());
                        return;
                    }

                    sendSelfPatch(createUpdateSubStageTask(state, SubStage.CUSTOMIZED_COMPUTE));
                }).sendWith(this);
            };

            List<Operation> getDisksOperations = diskLinkToContent.keySet().stream()
                    .map(link -> Operation.createGet(this, link)).collect(Collectors.toList());

            if (getDisksOperations.size() > 0) {
                OperationJoin.create(getDisksOperations).setCompletion(getDisksCompletion)
                        .sendWith(this);
                return;
            } else {
                sendSelfPatch(createUpdateSubStageTask(state, SubStage.CUSTOMIZED_COMPUTE));
            }
        };

        Stream<Operation> getComputeOperations = state.resourceLinks.stream()
                .map(link -> Operation.createGet(this, link));
        OperationJoin.create(getComputeOperations).setCompletion(getComputeCompletion)
                .sendWith(this);
    }

    private void provisionResources(ComputeProvisionTaskState state, String subTaskLink) {
        try {
            List<String> resourceLinks = state.resourceLinks;
            if (resourceLinks == null || resourceLinks.isEmpty()) {
                throw new IllegalStateException("No compute instances to provision");
            }
            if (subTaskLink == null) {
                // recurse after creating a sub task
                createSubTaskForProvisionCallbacks(state);
                return;
            }
            boolean isMockRequest = DeploymentProfileConfig.getInstance().isTest();
            Stream<Operation> operations = resourceLinks.stream().map((p) -> {
                ProvisionComputeTaskService.ProvisionComputeTaskState provisionTaskState = new ProvisionComputeTaskService.ProvisionComputeTaskState();
                provisionTaskState.computeLink = p;

                provisionTaskState.parentTaskLink = subTaskLink;
                provisionTaskState.isMockRequest = isMockRequest;
                provisionTaskState.taskSubStage = ProvisionComputeTaskService.ProvisionComputeTaskState.SubStage.CREATING_HOST;
                provisionTaskState.tenantLinks = state.tenantLinks;

                return Operation.createPost(this, ProvisionComputeTaskService.FACTORY_LINK)
                        .setBody(provisionTaskState);
            });

            OperationJoin.create(operations)
                    .setCompletion((ox,
                            exc) -> {
                        if (exc != null) {
                            logSevere(
                                    "Failure creating provisioning tasks: %s",
                                    Utils.toString(exc));

                            completeSubTasksCounter(null,
                                    new IllegalStateException(Utils.toString(exc)));
                            return;

                        }
                        logInfo("Requested provisioning of %s compute resources.",
                                resourceLinks.size());
                        sendSelfPatch(
                                createUpdateSubStageTask(state, SubStage.PROVISIONING_COMPUTE));
                        return;
                    }).sendWith(this);
        } catch (Throwable e) {
            failTask("System failure creating ContainerStates", e);
        }
    }

    private void createSubTaskForProvisionCallbacks(ComputeProvisionTaskState currentState) {
        SubTaskService.SubTaskState subTaskInitState = new SubTaskService.SubTaskState();
        ComputeProvisionTaskState subTaskPatchBody = new ComputeProvisionTaskState();
        subTaskPatchBody.taskInfo = new TaskState();
        subTaskPatchBody.taskSubStage = SubStage.PROVISIONING_COMPUTE_COMPLETED;
        subTaskPatchBody.taskInfo.stage = TaskState.TaskStage.STARTED;
        // tell the sub task with what to patch us, on completion
        subTaskInitState.parentPatchBody = Utils.toJson(subTaskPatchBody);
        subTaskInitState.errorThreshold = 0;

        subTaskInitState.parentTaskLink = getSelfLink();
        subTaskInitState.completionsRemaining = currentState.resourceLinks.size();
        subTaskInitState.tenantLinks = currentState.tenantLinks;
        Operation startPost = Operation
                .createPost(this, UUID.randomUUID().toString())
                .setBody(subTaskInitState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning("Failure creating sub task: %s",
                                        Utils.toString(e));
                                failTask("Failure creating sub task", e);
                                return;
                            }
                            SubTaskService.SubTaskState body = o
                                    .getBody(SubTaskService.SubTaskState.class);
                            // continue, passing the sub task link
                            provisionResources(currentState, body.documentSelfLink);
                        });
        getHost().startService(startPost, new SubTaskService());
    }

    private void queryForProvisionedResources(ComputeProvisionTaskState state) {
        List<String> resourceLinks = state.resourceLinks;
        if (resourceLinks == null || resourceLinks.isEmpty()) {
            complete(state, SubStage.COMPLETED);
            return;
        }
        ArrayList<Operation> operations = new ArrayList<>(resourceLinks.size());
        for (String link : resourceLinks) {
            operations.add(Operation.createGet(this, link));
        }

        OperationSequence.create(operations.toArray(new Operation[operations.size()]))
                .setCompletion((ops, exs) -> {
                    if (exs != null) {
                        failTask("Failure retrieving provisioned resources: " + Utils.toString(exs),
                                null);
                        return;
                    }
                    List<ComputeState> computes = new ArrayList<>();
                    ops.forEach((k, v) -> {
                        ComputeState cs = v.getBody(ComputeState.class);
                        if (ComputeAllocationTaskService.enableContainerHost(cs.customProperties)) {
                            computes.add(cs);
                        }
                    });
                    if (computes.isEmpty()) {
                        complete(state, SubStage.COMPLETED);
                        return;
                    } else {
                        validateConnectionsAndRegisterContainerHost(state, computes);
                    }
                }).sendWith(this);

    }

    private void validateConnectionsAndRegisterContainerHost(ComputeProvisionTaskState state,
            List<ComputeState> computes) {
        URI specValidateUri = UriUtilsExtended.buildUri(getHost(), ContainerHostService.SELF_LINK,
                ManagementUriParts.REQUEST_PARAM_VALIDATE_OPERATION_NAME);
        URI specUri = UriUtilsExtended.buildUri(getHost(), ContainerHostService.SELF_LINK);
        AtomicInteger remaining = new AtomicInteger(computes.size());
        AtomicReference<Throwable> error = new AtomicReference<>();
        for (ComputeState computeState : computes) {
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
                complete(state, SubStage.COMPLETED);
            }
        }).sendWith(this);

    }

    private void waitUntilConnectionValid(URI specValidateUri, ContainerHostSpec spec,
            int retryCount, Consumer<Throwable> callback) {

        Operation.createPut(specValidateUri).setBody(spec).setCompletion((op, er) -> {
            if (er != null) {
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
