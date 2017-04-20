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

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_REGISTRY_AUTH;
import static com.vmware.admiral.common.util.QueryUtil.createAnyPropertyClause;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.docker.util.DockerImage;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServerX509TrustManager;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.security.util.AuthCredentialsType;
import com.vmware.photon.controller.model.security.util.EncryptionUtils;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;

public abstract class AbstractDockerAdapterService extends StatelessService {
    protected static final long MAINTENANCE_INTERVAL_MICROS = Long.getLong(
            "dcp.management.docker.adapter.periodic.maintenance.period.micros",
            TimeUnit.SECONDS.toMicros(10));
    protected static final String NOT_FOUND_EXCEPTION_MESSAGE = "returned error 404";

    private static final Set<String> UNSUPPORTED_CREDENTIALS_TYPES = new HashSet<>(Arrays.asList(
            AuthCredentialsType.Password.toString()));

    public AbstractDockerAdapterService() {
        super();
        super.toggleOption(ServiceOption.PERIODIC_MAINTENANCE, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
        super.setMaintenanceIntervalMicros(MAINTENANCE_INTERVAL_MICROS);
    }

    static class BaseRequestContext {
        public AdapterRequest request;
        public CommandInput commandInput;
        public String imageName;
        public List<String> tenantLinks;

        /** Only for direct operations like exec */
        public Operation operation;
    }

    @Override
    public void handleStop(Operation delete) {
        getCommandExecutor().stop();
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

        getCommandExecutor().handleMaintenance(Operation.createPost(post.getUri()));

        post.complete();
    }

    protected DockerAdapterCommandExecutor getCommandExecutor() {
        synchronized (AbstractDockerAdapterService.class) {
            ServerX509TrustManager trustManager = ServerX509TrustManager.create(getHost());
            return RemoteApiDockerAdapterCommandExecutorImpl.create(getHost(),
                    trustManager);
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
                        if (op != null) {
                            op.fail(ex);
                        }
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

    /**
     * Create X-Registry-Auth header value containing Base64-encoded authConfig object so that
     * docker daemon can authenticate against registries that support basic authentication.
     *
     * For more info, see:
     * https://docs.docker.com/engine/reference/api/docker_remote_api/#authentication
     *
     * @param context
     * @param callback
     */
    protected void processAuthentication(BaseRequestContext context, Runnable callback) {
        DockerImage image = DockerImage.fromImageName(context.imageName);

        if (image.getHost() == null) {
            // if there is no registry host we assume the host is docker hub, so no authentication
            // needed
            callback.run();
            return;
        }

        QueryTask registryQuery = QueryUtil.buildQuery(RegistryService.RegistryState.class, false);
        if (context.tenantLinks != null) {
            registryQuery.querySpec.query.addBooleanClause(QueryUtil.addTenantGroupAndUserClause(
                    context.tenantLinks));
        }
        registryQuery.querySpec.query.addBooleanClause(createAnyPropertyClause(
                String.format("*://%s", image.getHost()),
                RegistryService.RegistryState.FIELD_NAME_ADDRESS));

        List<String> registryLinks = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(), ContainerService.ContainerState.class).query(
                registryQuery, (r) -> {
                    if (r.hasException()) {
                        fail(context.request, r.getException());
                        return;
                    } else if (r.hasResult()) {
                        registryLinks.add(r.getDocumentSelfLink());
                    } else {
                        if (registryLinks.isEmpty()) {
                            getHost().log(Level.WARNING,
                                    "Failed to find registry state with address '%s'.",
                                    image.getHost());
                            callback.run();
                            return;
                        }

                        fetchRegistryAuthState(registryLinks.get(0), context, callback);
                    }
                });
    }

    private void fetchRegistryAuthState(String registryStateLink, BaseRequestContext context,
            Runnable callback) {
        URI registryStateUri = UriUtils.buildUri(getHost(), registryStateLink,
                UriUtils.URI_PARAM_ODATA_EXPAND);

        Operation getRegistry = Operation.createGet(registryStateUri)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        if (context.operation != null) {
                            // direct operation
                            context.operation.fail(ex);
                        } else {
                            fail(context.request, ex);
                        }
                        return;
                    }

                    RegistryService.RegistryAuthState registryState = o
                            .getBody(RegistryService.RegistryAuthState.class);
                    if (registryState.authCredentials != null) {
                        AuthCredentialsServiceState authState = registryState.authCredentials;
                        AuthCredentialsType authType = AuthCredentialsType.valueOf(authState.type);
                        if (AuthCredentialsType.Password.equals(authType)) {
                            // create and encode AuthConfig
                            DockerAdapterService.AuthConfig authConfig = new DockerAdapterService.AuthConfig();
                            authConfig.username = authState.userEmail;
                            authConfig.password = EncryptionUtils.decrypt(authState.privateKey);
                            authConfig.email = "";
                            authConfig.auth = "";
                            DockerImage image = DockerImage.fromImageName(context.imageName);
                            authConfig.serveraddress = image.getHost();

                            String authConfigJson = Utils.toJson(authConfig);
                            String authConfigEncoded = new String(Base64.getEncoder().encode(
                                    authConfigJson.getBytes()));
                            context.commandInput.getProperties().put(DOCKER_IMAGE_REGISTRY_AUTH,
                                    authConfigEncoded);

                            getHost().log(Level.INFO,
                                    "Detected registry requiring basic authn, %s header created.",
                                    DOCKER_IMAGE_REGISTRY_AUTH);
                        }
                    }

                    callback.run();
                });

        sendRequest(getRegistry);
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
            if (op != null) {
                op.fail(x);
            }
            fail(request, x);
            return;
        }
    }

    private void getCredentials(AdapterRequest request, Operation op, ComputeState hostComputeState,
            BiConsumer<ComputeState, CommandInput> callbackFunction, URI dockerUri) {

        CommandInput commandInput = new CommandInput()
                .withDockerUri(dockerUri);

        String credentialsLink = getAuthCredentialLink(hostComputeState);
        if (credentialsLink == null) {
            callbackFunction.accept(hostComputeState, commandInput);
            return;
        }

        sendRequest(Operation
                .createGet(this, credentialsLink)
                .setCompletion((o, ex) -> {
                    if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND) {
                        String errorMsg = String.format(
                                "AuthCredentialsState not found with link: %s %s",
                                credentialsLink, request.getRequestTrackingLog());
                        Throwable t = new LocalizableValidationException(errorMsg,
                                "adapter.auth.not.found", credentialsLink,
                                request.getRequestTrackingLog());
                        if (op != null) {
                            op.fail(t);
                        }
                        fail(request, t);
                        return;
                    }

                    if (ex != null) {
                        logWarning("Failure while getting credentials for %s and docker uri %s",
                                credentialsLink, dockerUri);
                        fail(request, ex);
                        if (op != null) {
                            op.fail(ex);
                        }
                        return;
                    }

                    AuthCredentialsServiceState credentials = o.getBody(
                            AuthCredentialsServiceState.class);
                    Throwable e = checkAuthCredentialsSupportedType(credentials, false);
                    if (e != null) {
                        if (op != null) {
                            op.fail(e);
                        }
                        fail(request, e);
                        return;
                    }

                    commandInput
                            .withCredentials(credentials)
                            .withProperty(SSL_TRUST_ALIAS_PROP_NAME,
                                    ContainerHostUtil.getTrustAlias(hostComputeState));

                    callbackFunction.accept(hostComputeState, commandInput);
                }));

        getHost().log(Level.FINE, "Fetching AuthCredentials: %s %s", credentialsLink,
                request.getRequestTrackingLog());
    }

    protected Throwable checkAuthCredentialsSupportedType(AuthCredentialsServiceState c,
            boolean throwError) {
        RuntimeException e = null;
        if (UNSUPPORTED_CREDENTIALS_TYPES.contains(c.type)) {
            e = new LocalizableValidationException("Unsupported credentials type",
                    "adapter.unsuported.auth.credentials.type");
            if (throwError) {
                throw e;
            }
        }
        return e;
    }

    protected String getAuthCredentialLink(ComputeState hostComputeState) {
        if (hostComputeState.customProperties == null) {
            return null;
        }
        return hostComputeState.customProperties
                .get(ComputeConstants.HOST_AUTH_CREDENTIALS_PROP_NAME);
    }

    protected void fail(AdapterRequest request, Throwable e) {
        if (e.getMessage() != null && e.getMessage().contains(NOT_FOUND_EXCEPTION_MESSAGE)) {
            logWarning(e.getMessage());
        } else {
            logWarning("%s", Utils.toString(e));
        }

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
            logWarning(
                    "System exception while calling back docker operation requester for resource: %s %s",
                    request.resourceReference, request.getRequestTrackingLog());
            logSevere(e);
        }
    }
}
