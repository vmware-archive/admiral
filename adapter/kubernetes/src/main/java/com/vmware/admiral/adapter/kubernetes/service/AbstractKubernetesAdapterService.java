/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.kubernetes.service;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.kubernetes.KubernetesRemoteApiClient;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.KubernetesHostConstants;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

public abstract class AbstractKubernetesAdapterService extends StatelessService {
    protected static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.docker.adapter.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(10));
    protected static final String NOT_FOUND_EXCEPTION_MESSAGE = "returned error 404";

    public static class KubernetesContext {
        public AuthCredentialsServiceState credentials;
        public String SSLTrustCertificate;
        public String SSLTrustAlias;

        public ComputeState host;
        public Map<String, String> customProperties;

        public KubernetesContext() {
            this.credentials = null;
            this.host = new ComputeState();
            this.customProperties = new HashMap<>();
        }
    }

    public AbstractKubernetesAdapterService() {
        super();
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
    }

    protected KubernetesRemoteApiClient getApiClient() {
        synchronized (AbstractKubernetesAdapterService.class) {
            ServerX509TrustManager trustManager = ServerX509TrustManager.create(getHost());
            return KubernetesRemoteApiClient.create(getHost(),
                    trustManager);
        }
    }

    @Override
    public void handleStop(Operation delete) {
        getApiClient().stop();
        delete.complete();
    }

    @Override
    public void handlePeriodicMaintenance(Operation post) {
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

        getApiClient().handleMaintenance(Operation.createPost(post.getUri()));

        post.complete();
    }

    protected void getComputeHost(AdapterRequest request, Operation op,
            URI containerHostReference, Consumer<KubernetesContext> callbackFunction) {
        sendRequest(Operation.createGet(containerHostReference)
                .setContextId(request.getRequestId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (op != null) {
                            op.fail(ex);
                        }
                        fail(request, ex);
                    } else {
                        KubernetesContext context = new KubernetesContext();
                        context.host = o.getBody(ComputeState.class);
                        context.host.customProperties.putIfAbsent(
                                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                                KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE);
                        getCredentials(request, op, context, callbackFunction);
                    }
                }));

        getHost().log(Level.FINE, "Fetching ComputeState: %s %s", containerHostReference,
                request.getRequestTrackingLog());
    }

    private void getCredentials(AdapterRequest request, Operation op, KubernetesContext context,
            Consumer<KubernetesContext> callbackFunction) {

        String credentialsLink = getAuthCredentialLink(context.host);
        if (credentialsLink == null) {
            callbackFunction.accept(context);
            return;
        }

        sendRequest(Operation
                .createGet(this, credentialsLink)
                .setCompletion(
                        (o, ex) -> {
                            if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                                String errorMag = String.format(
                                        "AuthCredentialsState not found with link: %s %s",
                                        credentialsLink, request.getRequestTrackingLog());
                                Throwable t = new LocalizableValidationException(errorMag,
                                        "adapter.auth.not.found", credentialsLink,
                                        request.getRequestTrackingLog());
                                if (op != null) {
                                    op.fail(t);
                                }

                                fail(request, t);
                                return;
                            }

                            if (ex != null) {
                                fail(request, ex);
                                if (op != null) {
                                    op.fail(ex);
                                }
                                return;
                            }

                            context.credentials = o.getBody(AuthCredentialsServiceState.class);
                            callbackFunction.accept(context);
                        }));

        getHost().log(Level.FINE, "Fetching AuthCredentials: %s %s", credentialsLink,
                request.getRequestTrackingLog());
    }

    protected String getAuthCredentialLink(ComputeState hostComputeState) {
        if (hostComputeState.customProperties == null) {
            return null;
        }
        return hostComputeState.customProperties
                .get(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME);
    }

    protected void fail(AdapterRequest request, Throwable e) {
        logWarning("Fail: %s", Utils.toString(e));
        patchTaskStage(request, TaskStage.FAILED, e);
    }

    protected void fail(AdapterRequest request, Operation o, Throwable e) {
        if (o != null && o.getBodyRaw() != null) {
            String errMsg = String.format("%s; Reason: %s", e.getMessage(),
                    Utils.toJson(o.getBodyRaw()));

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
                logFine("No callback provided to Kubernetes adapter service for resource: %s.",
                        request.resourceReference);
                return;
            }
            if (exception != null) {
                taskStage = TaskStage.FAILED;
            }

            logInfo("Patching adapter callback task %s with state %s for resource: %s",
                    request.serviceTaskCallback.serviceSelfLink, taskStage,
                    request.resourceReference);

            if (callbackResponse != null) {
                if (callbackResponse.taskInfo == null) {
                    callbackResponse.taskInfo = new TaskState();
                }
                if (callbackResponse.taskInfo.stage == null) {
                    callbackResponse.taskInfo.stage = taskStage;
                }
            }

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
                callbackReference = UriUtils.buildUri(getHost(),
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
            logWarning("System exception while calling back docker operation requester for"
                            + " resource: %s %s",
                    request.resourceReference, request.getRequestTrackingLog());
            logSevere(e);
        }
    }
}
