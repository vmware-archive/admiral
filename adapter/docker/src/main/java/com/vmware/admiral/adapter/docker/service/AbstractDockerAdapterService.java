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

package com.vmware.admiral.adapter.docker.service;

import static com.vmware.admiral.compute.ContainerHostService.HOST_DOCKER_ADAPTER_TYPE_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSH_HOST_KEY_PROP_NAME;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService.DockerAdapterType;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public abstract class AbstractDockerAdapterService extends StatelessService {
    protected static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.docker.adapter.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(10));

    private static DockerAdapterCommandExecutor sshCommandExecutor;
    private static DockerAdapterCommandExecutor apiCommandExecutor;

    protected final Map<DockerAdapterType, DockerAdapterCommandExecutor> executors;

    public AbstractDockerAdapterService() {
        super();
        executors = new HashMap<>(2);
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
    }

    @Override
    public void handleStart(Operation startPost) {
        executors.put(DockerAdapterType.API, getApiCommandExecutor());
        executors.put(DockerAdapterType.SSH, getSshCommandExecutor());
        startPost.complete();
    }

    @Override
    public void handleStop(Operation delete) {
        for (DockerAdapterCommandExecutor executor : executors.values()) {
            if (executor != null) {
                executor.stop();
            }
        }
        delete.complete();
    }

    @Override
    public void handleMaintenance(Operation post) {
        if (getProcessingStage() != ProcessingStage.AVAILABLE) {
            logFine("Skipping maintenance since service is not available: %s ", getUri());
            post.complete();
            return;
        }

        if (DeploymentProfileConfig.getInstance().isTest()) {
            logFine("Skipping scheduled maintenance in test mode: %s", getUri());
            post.complete();
            return;
        }

        logFine("Performing maintenance for: %s", getUri());

        for (DockerAdapterCommandExecutor adapterExecutor : executors.values()) {
            adapterExecutor.handleMaintenance(Operation.createPost(post.getUri()));
        }

        post.complete();
    }

    protected DockerAdapterCommandExecutor getCommandExecutor(ComputeState parentComputeState) {
        String adapterDockerType = parentComputeState.customProperties
                .get(HOST_DOCKER_ADAPTER_TYPE_PROP_NAME);
        DockerAdapterType adapterType = DockerAdapterType.API;
        if (adapterDockerType != null) {
            adapterType = DockerAdapterType.valueOf(adapterDockerType);
        }
        return executors.get(adapterType);
    }

    protected DockerAdapterCommandExecutor getApiCommandExecutor() {
        synchronized (AbstractDockerAdapterService.class) {
            if (apiCommandExecutor == null) {
                ServerX509TrustManager trustManager = ServerX509TrustManager.create(getHost());
                apiCommandExecutor = RemoteApiDockerAdapterCommandExecutorImpl.create(getHost(),
                        trustManager);
            }
            return apiCommandExecutor;
        }
    }

    protected DockerAdapterCommandExecutor getSshCommandExecutor() {
        synchronized (AbstractDockerAdapterService.class) {
            if (sshCommandExecutor == null) {
                sshCommandExecutor = new SshDockerAdapterCommandExecutorImpl(getHost());
            }
            return sshCommandExecutor;
        }
    }

    protected void getContainerHost(AdapterRequest request, Operation op,
            URI containerHostReference,
            BiConsumer<ComputeState, CommandInput> callbackFunction) {

        sendRequest(Operation.createGet(containerHostReference)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setContextId(request.getRequestId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        op.fail(ex);
                        fail(request, ex);
                    } else {
                        handleExceptions(request, op, () -> {
                            ComputeState hostComputeState = o.getBody(ComputeState.class);
                            createHostConnection(request, op, hostComputeState, callbackFunction);
                        });
                    }
                }));

        getHost().log(Level.FINE, "Fetching ComputeState: %s %s", containerHostReference,
                request.getRequestTrackingLog());
    }

    protected void createHostConnection(AdapterRequest request, Operation op,
            ComputeState hostComputeState,
            BiConsumer<ComputeState, CommandInput> callbackFunction) {

        URI dockerUri;
        try {
            dockerUri = ContainerDescription.getDockerHostUri(hostComputeState);

            logFine("Processing request for adapter %s %s", dockerUri,
                    request.getRequestTrackingLog());

            getCredentials(request, op, hostComputeState, callbackFunction, dockerUri);
        } catch (Exception x) {
            op.fail(x);
            fail(request, x);
            return;
        }
    }

    private void getCredentials(AdapterRequest request, Operation op, ComputeState hostComputeState,
            BiConsumer<ComputeState, CommandInput> callbackFunction, URI dockerUri) {

        CommandInput commandInput = new CommandInput()
                .withDockerUri(dockerUri);

        String sshHostKey = hostComputeState.customProperties
                .get(SSH_HOST_KEY_PROP_NAME);
        commandInput.withProperty(SSH_HOST_KEY_PROP_NAME, sshHostKey);

        String credentialsLink = getAuthCredentialLink(hostComputeState);
        if (credentialsLink == null) {
            callbackFunction.accept(hostComputeState, commandInput);
            return;
        }

        final AtomicBoolean credentialsFound = new AtomicBoolean();

        new ServiceDocumentQuery<>(getHost(),
                AuthCredentialsServiceState.class)
                .queryDocument(credentialsLink, (r) -> {
                    if (r.hasException()) {
                        fail(request, r.getException());
                        op.fail(r.getException());
                    } else if (r.hasResult()) {
                        commandInput.withCredentials(r.getResult());

                        credentialsFound.set(true);

                        callbackFunction.accept(hostComputeState, commandInput);
                    } else {
                        if (!credentialsFound.get()) {
                            Throwable t = new IllegalArgumentException(
                                    "AuthCredentialsState not found with link: "
                                            + credentialsLink + request.getRequestTrackingLog());
                            op.fail(t);
                            fail(request, t);
                        }
                    }
                });

        getHost().log(Level.FINE, "Fetching AuthCredentials: %s %s", credentialsLink,
                request.getRequestTrackingLog());
    }

    protected String getAuthCredentialLink(ComputeState hostComputeState) {
        if (hostComputeState.customProperties == null) {
            return null;
        }
        return hostComputeState.customProperties
                .get(ComputeConstants.HOST_AUTH_CREDNTIALS_PROP_NAME);
    }

    protected void fail(AdapterRequest request, Throwable e) {
        logWarning(Utils.toString(e));
        patchTaskStage(request, TaskStage.FAILED, e);
    }

    protected void fail(AdapterRequest request, Operation o, Throwable e) {
        if (o != null && o.getBodyRaw() != null) {
            String errMsg = String.format("%s; Reason: %s", e.getMessage(),
                    o.getBodyRaw().toString());
            e = new Exception(errMsg, e);
        }
        fail(request, e);
    }

    protected void handleExceptions(AdapterRequest request, Operation op, Runnable function) {
        try {
            function.run();
        } catch (Throwable e) {
            logSevere(e);
            if (op != null) {
                op.fail(e);
            }
            fail(request, e);
        }
    }

    protected void patchTaskStage(AdapterRequest request, TaskStage taskStage,
            Throwable exception) {
        patchTaskStage(request, taskStage, exception, null);
    }

    protected void patchTaskStage(AdapterRequest request, TaskStage taskStage, Throwable exception,
            ServiceTaskCallbackResponse callbackResponse) {

        try {
            if (request.serviceTaskCallback.isEmpty()) {
                logFine("No callback provided to Docker adapter service for resource: %s.",
                        request.resourceReference);
                return;
            }
            if (exception != null) {
                taskStage = TaskStage.FAILED;
            }

            logInfo("Patching adapter callback task %s with state %s for resource: %s",
                    request.serviceTaskCallback.serviceSelfLink, taskStage,
                    request.resourceReference);

            switch (taskStage) {
            case FINISHED:
                if (callbackResponse == null) {
                    callbackResponse = request.serviceTaskCallback.getFinishedResponse();
                }
                break;
            case CANCELLED:
                return;
            case FAILED:
            default:
                callbackResponse = request.serviceTaskCallback.getFailedResponse(
                        exception == null ? new Exception("Adapter Exception.") : exception);
                break;
            }

            callbackResponse.customProperties = PropertyUtils.mergeCustomProperties(
                    callbackResponse.customProperties, request.customProperties);

            URI callbackReference = URI.create(request.serviceTaskCallback.serviceSelfLink);
            if (callbackReference.getScheme() == null) {
                callbackReference = UriUtilsExtended.buildUri(getHost(),
                        request.serviceTaskCallback.serviceSelfLink);
            }

            sendRequest(Operation.createPatch(callbackReference)
                    .setBody(callbackResponse)
                    .setContextId(request.getRequestId())
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            logWarning("Notifying parent task for resource: %s %s failed: %s",
                                    request.resourceReference,
                                    request.getRequestTrackingLog(), Utils.toString(e));
                        }
                    }));
        } catch (Throwable e) {
            logWarning(
                    "System exception while calling back docker operation requester for resource: %s %s",
                    request.resourceReference, request.getRequestTrackingLog());
            logSevere(e);
        }
    }
}
