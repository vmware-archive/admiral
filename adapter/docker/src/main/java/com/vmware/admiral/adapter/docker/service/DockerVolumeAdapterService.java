/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.adapter.common.VolumeOperationType.DISCOVER_VMDK_DATASTORE;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_DRIVER_OPTS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.DEFAULT_VMDK_DATASTORE_PROP_NAME;
import static com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.VMDK_VOLUME_DRIVER;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.vmware.admiral.adapter.common.VolumeOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;

public class DockerVolumeAdapterService extends AbstractDockerAdapterService {

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_VOLUME;

    public static final String DOCKER_VOLUME_DRIVER_TYPE_DEFAULT = "local";

    private static final String VMDK_DATASTORE_DISCOVERY_VOLUME_NAME = "__vmdkDatastoreDiscovery";

    private static class RequestContext {
        public ContainerVolumeRequest request;
        public ContainerVolumeState volumeState;
        public CommandInput commandInput;
        public DockerAdapterCommandExecutor executor;

        // Only for direct operations. See DIRECT_OPERATIONS list
        public Operation operation;
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.request = op.getBody(ContainerVolumeRequest.class);
        context.request.validate();

        VolumeOperationType operationType = context.request.getOperationType();

        logInfo("Processing volume operation request %s for resource %s %s", operationType,
                context.request.resourceReference, context.request.getRequestTrackingLog());

        op.complete();

        processVolumeRequest(context);
    }

    /**
     * Start processing the request. First fetches the {@link ContainerVolumeState}. Will result in
     * filling the {@link RequestContext#volumeState} property.
     */
    private void processVolumeRequest(RequestContext context) {
        if (context.request.getOperationType() == DISCOVER_VMDK_DATASTORE) {
            handleExceptions(context.request, context.operation, () -> {
                processDiscoverVmdkDatastore(context);
            });
            return;
        }

        Operation getVolumeState = Operation.createGet(context.request.getVolumeStateReference())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        handleExceptions(context.request, context.operation, () -> {
                            context.volumeState = o.getBody(ContainerVolumeState.class);
                            processVolumeState(context);
                        });
                    }
                });
        handleExceptions(context.request, context.operation, () -> {
            logFine("Fetching VolumeState: %s %s",
                    context.request.getRequestTrackingLog(),
                    context.request.getVolumeStateReference());
            sendRequest(getVolumeState);
        });
    }

    /**
     * Process the {@link ContainerVolumeState}. Will result in filling the
     * {@link RequestContext#commandInput} and {@link RequestContext#executor} properties.
     */
    private void processVolumeState(RequestContext context) {
        if (context.volumeState.originatingHostLink == null) {
            fail(context.request,
                    new IllegalArgumentException("originatingHostLink missing for volumen state "
                            + context.volumeState.documentSelfLink));
            return;
        }

        getContainerHost(
                context.request,
                context.operation,
                UriUtils.buildUri(getHost(), context.volumeState.originatingHostLink),
                (computeState, commandInput) -> {
                    context.commandInput = commandInput;
                    context.executor = getCommandExecutor();
                    handleExceptions(context.request, context.operation,
                            () -> processOperation(context));
                });
    }

    /**
     * Process the operation. This method should be called after {@link RequestContext#request},
     * {@link RequestContext#volumeState}, {@link RequestContext#commandInput} and
     * {@link RequestContext#executor} have been filled. For direct operations,
     * {@link RequestContext#operation} must also be filled
     *
     * @see #DIRECT_OPERATIONS
     */
    private void processOperation(RequestContext context) {
        try {
            switch (context.request.getOperationType()) {
            case CREATE:
                processCreateVolume(context);
                break;

            case DELETE:
                processDeleteVolume(context);
                break;

            case INSPECT:
                inspectAndUpdateVolume(context);
                break;

            case LIST_VOLUMES:
                processListVolume(context);
                break;

            default:
                fail(context.request, new IllegalArgumentException("Unexpected request type: "
                        + context.request.getOperationType()
                        + context.request.getRequestTrackingLog()));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    private void processCreateVolume(RequestContext context) {
        CommandInput createCommandInput = context.commandInput.withPropertyIfNotNull(
                DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME,
                context.volumeState.name);
        if (context.volumeState.driver != null && !context.volumeState.driver.isEmpty()) {
            createCommandInput.withProperty(
                    DOCKER_VOLUME_DRIVER_PROP_NAME,
                    context.volumeState.driver);
        } else {
            createCommandInput.withProperty(
                    DOCKER_VOLUME_DRIVER_PROP_NAME,
                    DOCKER_VOLUME_DRIVER_TYPE_DEFAULT);
        }

        if (context.volumeState.options != null && !context.volumeState.options.isEmpty()) {
            createCommandInput.withProperty(
                    DOCKER_VOLUME_DRIVER_OPTS_PROP_NAME,
                    context.volumeState.options);
        }

        context.executor.createVolume(createCommandInput, (op, ex) -> {
            if (ex != null) {
                logWarning("Failure while creating volume [%s]",
                        context.volumeState.documentSelfLink);
                fail(context.request, op, ex);
            } else {
                inspectAndUpdateVolume(context);
            }
        });
    }

    private void processDeleteVolume(RequestContext context) {
        CommandInput deleteCommandInput = context.commandInput.withPropertyIfNotNull(
                DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME,
                context.volumeState.name);

        context.executor.removeVolume(deleteCommandInput, (op, ex) -> {
            if (ex != null) {
                logWarning("Failure while removing volume [%s]",
                        context.volumeState.documentSelfLink);
                fail(context.request, op, ex);
            } else {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void inspectAndUpdateVolume(RequestContext context) {
        CommandInput inspectCommandInput = context.commandInput.withProperty(
                DOCKER_VOLUME_NAME_PROP_NAME, context.volumeState.name);

        logFine("Executing inspect volume: %s %s", context.volumeState.documentSelfLink,
                context.request.getRequestTrackingLog());

        context.executor.inspectVolume(inspectCommandInput, (o, ex) -> {
            if (ex != null) {
                logWarning("Failure while inspecting volume [%s]",
                        context.volumeState.documentSelfLink);
                fail(context.request, o, ex);
            } else {
                handleExceptions(context.request, context.operation, () -> {
                    Map<String, Object> properties = o.getBody(Map.class);

                    patchVolumeState(context.request, context.volumeState, properties, context);
                });
            }
        });
    }

    private void patchVolumeState(ContainerVolumeRequest request,
            ContainerVolumeState volumeState, Map<String, Object> properties,
            RequestContext context) {

        ContainerVolumeState newVolumeState = new ContainerVolumeState();
        newVolumeState.documentSelfLink = volumeState.documentSelfLink;
        newVolumeState.documentExpirationTimeMicros = -1; // make sure the expiration is reset.
        newVolumeState.adapterManagementReference = volumeState.adapterManagementReference;
        newVolumeState._healthFailureCount = 0;

        ContainerVolumeStateMapper.propertiesToContainerVolumeState(newVolumeState, properties);

        logFine("Patching ContainerVolumeState: %s %s",
                newVolumeState.documentSelfLink,
                request.getRequestTrackingLog());
        sendRequest(Operation
                .createPatch(request.getVolumeStateReference())
                .setBodyNoCloning(newVolumeState)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failure while patching volume [%s]",
                                volumeState.documentSelfLink);
                        fail(context.request, o, ex);
                    } else {
                        patchTaskStage(request, TaskStage.FINISHED, null);
                    }
                }));
    }

    private void processListVolume(RequestContext context) {

        CommandInput createListVolumeCommandInput = new CommandInput(context.commandInput);

        context.executor.listVolumes(createListVolumeCommandInput, (op, ex) -> {
            if (ex != null) {
                context.operation.fail(ex);
            } else {
                if (op.hasBody()) {
                    context.operation.setBodyNoCloning(op.getBody(String.class));
                }
                context.operation.complete();
            }
        });
    }

    private void processDiscoverVmdkDatastore(RequestContext context) {
        getContainerHost(
                context.request,
                context.operation,
                context.request.resourceReference,
                (computeState, commandInput) -> {
                    context.commandInput = commandInput;
                    context.executor = getCommandExecutor();
                    handleExceptions(context.request, context.operation,
                            () -> performVmdkDatastoreDiscovery(context));
                });
    }

    private void performVmdkDatastoreDiscovery(RequestContext context) {
        // generate random name to prevent name conflicts; deleting and recreating
        // VMDK volumes with the same name sometimes result in error
        CommandInput createCommandInput = context.commandInput.withProperty(
                DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME,
                VMDK_DATASTORE_DISCOVERY_VOLUME_NAME + UUID.randomUUID());

        createCommandInput.withProperty(
                DOCKER_VOLUME_DRIVER_PROP_NAME,
                VMDK_VOLUME_DRIVER);

        context.executor.createVolume(createCommandInput, (op, ex) -> {
            if (ex != null) {
                logWarning("Failure while creating volume [%s]",
                        context.volumeState.documentSelfLink);
                fail(context.request, op, ex);
            } else {
                inspectVmdkDatastoreDiscoveryVolume(context);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void inspectVmdkDatastoreDiscoveryVolume(RequestContext context) {
        context.executor.inspectVolume(
                context.commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        logWarning("Failure while inspecting volume [%s]",
                                context.volumeState.documentSelfLink);
                        fail(context.request, o, ex);
                    } else {
                        handleExceptions(context.request, context.operation, () -> {
                            Map<String, Object> properties = o.getBody(Map.class);

                            String datastore = ContainerVolumeStateMapper
                                    .getVmdkDatastoreName(properties);

                            if (datastore == null) {
                                String errMsg = String.format(
                                        "Failed to discover default datastore for host [%s]",
                                        context.request.resourceReference);
                                fail(context.request, new Exception(errMsg));
                                return;
                            }

                            patchComputeState(context, datastore,
                                    () -> deleteVmdkDatastoreDiscoveryVolume(context));
                        });
                    }
                });
    }

    private void deleteVmdkDatastoreDiscoveryVolume(RequestContext context) {
        context.executor.removeVolume(context.commandInput, (op, ex) -> {
            if (ex != null) {
                logWarning("Failure while removing volume [%s]",
                        context.volumeState.documentSelfLink);
                fail(context.request, op, ex);
            } else {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        });
    }

    private void patchComputeState(RequestContext context, String datastore, Runnable callback) {
        ComputeState patch = new ComputeState();
        patch.customProperties = new HashMap<>();
        patch.customProperties.put(DEFAULT_VMDK_DATASTORE_PROP_NAME, datastore);

        sendRequest(Operation
                .createPatch(context.request.resourceReference)
                .setBodyNoCloning(patch)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Failure while patching compute state [%s] with volume [%s]",
                                context.request.resourceReference,
                                context.volumeState.documentSelfLink);
                        fail(context.request, o, ex);
                    } else {
                        callback.run();
                    }
                }));
    }

}
