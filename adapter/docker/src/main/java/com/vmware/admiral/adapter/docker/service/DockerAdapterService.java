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

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_COMMAND_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_DOMAINNAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_ENTRYPOINT_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_ENV_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_EXPOSED_PORTS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOSTNAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.BINDS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CAP_ADD_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CAP_DROP_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.CPU_SHARES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DEVICES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DNS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.DNS_SEARCH_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.EXTRA_HOSTS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.LINKS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.MEMORY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.MEMORY_SWAP_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.NETWORK_MODE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.PID_MODE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.PRIVILEGED_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.PUBLISH_ALL;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.RESTART_POLICY_RETRIES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.VOLUMES_FROM_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.VOLUME_DRIVER;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_IMAGE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_LOG_CONFIG_PROP_CONFIG_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_LOG_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_LOG_CONFIG_PROP_TYPE_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORKING_CONFIG_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_OPEN_STDIN_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_TTY_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_USER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_VOLUMES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_WORKING_DIR_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_EXEC_ATTACH_STDERR_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_EXEC_COMMAND_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_DATA_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_FROM_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_IMAGE_REGISTRY_AUTH;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.SINCE;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.STD_ERR;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.STD_OUT;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.TAIL;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.TIMESTAMPS;
import static com.vmware.admiral.common.util.QueryUtil.createAnyPropertyClause;

import java.io.File;
import java.io.IOException;
import java.net.ProtocolException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;

import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG;
import com.vmware.admiral.adapter.docker.util.CommandUtil;
import com.vmware.admiral.adapter.docker.util.DockerDevice;
import com.vmware.admiral.adapter.docker.util.DockerImage;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.AuthCredentialsType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.security.EncryptionUtils;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.ServiceNetwork;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.compute.container.maintenance.ContainerStatsEvaluator;
import com.vmware.admiral.compute.container.network.NetworkUtils;
import com.vmware.admiral.compute.container.volume.VolumeBinding;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationFactoryService;
import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.RegistryService.RegistryAuthState;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Service for fulfilling ContainerInstanceRequest backed by a docker server
 */
public class DockerAdapterService extends AbstractDockerAdapterService {

    /**
     * prefix used for temp files used to store downloaded images
     */
    private static final String DOWNLOAD_TEMPFILE_PREFIX = "admiral";

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER;

    public static final String PROVISION_CONTAINER_RETRIES_COUNT_PARAM_NAME = "provision.container.retries.count";

    private SystemImageRetrievalManager imageRetrievalManager;

    /**
     * Properties in an inspect response that we want to filter out
     *
     * ExecIDs: This is an unbounded list of all execs performed on the container and can easily
     * cause the document to exceed DCPs serialization size limit (32KB)
     */
    private static final List<String> FILTER_PROPERTIES = Arrays.asList("ExecIDs");

    private static final List<Integer> RETRIABLE_HTTP_STATUSES = Arrays.asList(
            HttpStatus.SC_NOT_FOUND,
            HttpStatus.SC_REQUEST_TIMEOUT,
            HttpStatus.SC_CONFLICT,
            HttpStatus.SC_INTERNAL_SERVER_ERROR,
            HttpStatus.SC_BAD_GATEWAY,
            HttpStatus.SC_SERVICE_UNAVAILABLE,
            HttpStatus.SC_GATEWAY_TIMEOUT
    );
    private static final String DELETE_CONTAINER_MISSING_ERROR = "error 404 for DELETE";

    private volatile Integer retriesCount;

    private static class RequestContext {
        public ContainerInstanceRequest request;
        public ComputeState computeState;
        public ContainerState containerState;
        public ContainerDescription containerDescription;
        public CommandInput commandInput;
        public DockerAdapterCommandExecutor executor;
        /**
         * Flags the request as already failed. Used to avoid patching a FAILED task to FINISHED
         * state after inspecting a container.
         */
        public boolean requestFailed;
        /** Only for direct operations like exec */
        public Operation operation;
    }

    public static class AuthConfig {
        public String username;
        public String password;
        public String email;
        public String serveraddress;
        public String auth;
    }

    @Override
    public void handleStart(Operation startPost) {
        imageRetrievalManager = new SystemImageRetrievalManager(getHost());
        super.handleStart(startPost);
    }

    @Override
    public void handlePatch(Operation op) {
        RequestContext context = new RequestContext();
        context.request = op.getBody(ContainerInstanceRequest.class);
        context.request.validate();// validate the request

        ContainerOperationType operationType = context.request.getOperationType();
        if (ContainerOperationType.STATS != operationType
                && ContainerOperationType.INSPECT != operationType
                && ContainerOperationType.FETCH_LOGS != operationType) {
            logInfo("Processing operation request %s for resource %s %s",
                    operationType, context.request.resourceReference,
                    context.request.getRequestTrackingLog());
        }

        if (operationType == ContainerOperationType.EXEC) {
            // Exec is direct operation
            context.operation = op;
        } else {
            op.complete();// TODO: can't return the operation if state not persisted.
        }
        processContainerRequest(context);
    }

    /*
     * start processing the request - first fetch the ContainerState
     */
    private void processContainerRequest(RequestContext context) {
        Operation getContainerState = Operation
                .createGet(context.request.getContainerStateReference())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                        if (context.operation != null) {
                            context.operation.fail(ex);
                        }
                    } else {
                        handleExceptions(context.request, context.operation, () -> {
                            context.containerState = o
                                    .getBody(ContainerState.class);
                            processContainerState(context);
                        });
                    }
                });
        handleExceptions(context.request, context.operation, () -> {
            getHost().log(Level.FINE, "Fetching ContainerState: %s %s",
                    context.request.getRequestTrackingLog(),
                    context.request.getContainerStateReference());
            sendRequest(getContainerState);
        });
    }

    /*
     * process the ContainerState - fetch the referenced parent ComputeState
     */
    private void processContainerState(RequestContext context) {
        if (context.containerState.parentLink == null) {
            fail(context.request, new IllegalArgumentException("parentLink"));
            return;
        }

        getContainerHost(
                context.request,
                context.operation,
                context.request.resolve(context.containerState.parentLink),
                (computeState, commandInput) -> {
                    context.commandInput = commandInput;
                    context.executor = getCommandExecutor();
                    context.computeState = computeState;
                    handleExceptions(context.request, context.operation,
                            () -> processOperation(context));
                });
    }

    private void processOperation(RequestContext context) {
        try {
            switch (context.request.getOperationType()) {
            case CREATE:
                // before the container is created the image needs to be pulled
                processCreateImage(context);
                break;

            case DELETE:
                processDeleteContainer(context);
                break;

            case START:
                processStartContainer(context);
                break;

            case STOP:
                processStopContainer(context);
                break;

            case FETCH_LOGS:
                processFetchContainerLog(context);
                break;

            case INSPECT:
                inspectContainer(context);
                break;

            case EXEC:
                execContainer(context);
                break;

            case STATS:
                fetchContainerStats(context);
                break;

            default:
                fail(context.request, new IllegalArgumentException(
                        "Unexpected request type: " + context.request.getOperationType()
                                + context.request.getRequestTrackingLog()));
            }
        } catch (Throwable e) {
            fail(context.request, e);
        }
    }

    private void processFetchContainerLog(RequestContext context) {
        CommandInput fetchLogCommandInput = constructFetchLogCommandInput(context.request,
                context.commandInput, context.containerState);

        // currently VIC does not support container logs
        if (ContainerHostUtil.isVicHost(context.computeState)) {
            byte[] log = "--".getBytes();
            processContainerLogResponse(context, log);
            return;
        }

        context.executor.fetchContainerLog(fetchLogCommandInput,
                (operation, excep) -> {
                    if (excep != null) {
                        fail(context.request, operation, excep);
                    } else {
                        /* Write this to the log service */
                        handleExceptions(context.request, context.operation, () -> {
                            byte[] log = null;
                            if (operation.getBodyRaw() != null) {
                                if (Operation.MEDIA_TYPE_APPLICATION_OCTET_STREAM.equals(
                                        operation.getContentType())) {

                                    log = operation.getBody(byte[].class);

                                } else {
                                    /* TODO check for encoding header */
                                    String logStr = operation.getBody(String.class);
                                    if (logStr != null) {
                                        log = logStr.getBytes();
                                    }
                                }
                            }

                            if (log == null) {
                                log = "--".getBytes();
                                // log a warning
                                String containerId = Service
                                        .getId(context.containerState.documentSelfLink);
                                logWarning("Found empty logs for container %s", containerId);
                            }

                            processContainerLogResponse(context, log);
                        });
                    }
                });
    }

    private CommandInput constructFetchLogCommandInput(ContainerInstanceRequest request,
            CommandInput commandInput, ContainerState containerState) {
        CommandInput fetchLogCommandInput = new CommandInput(commandInput);
        boolean stdErr = true;
        boolean stdOut = true;
        boolean includeTimeStamp = true;
        int tail = DockerAdapterCommandExecutor.DEFAULT_VALUE_TAIL;
        long sinceInSeconds = 0;

        if (request.customProperties != null) {
            stdErr = Boolean.parseBoolean(request.customProperties.getOrDefault(STD_ERR,
                    String.valueOf(stdErr)));
            stdOut = Boolean.parseBoolean(request.customProperties.getOrDefault(STD_OUT,
                    String.valueOf(stdOut)));
            includeTimeStamp = Boolean.parseBoolean(request.customProperties.getOrDefault(
                    TIMESTAMPS, String.valueOf(includeTimeStamp)));
            String since = request.customProperties.get(SINCE);
            if (since != null && !since.isEmpty()) {
                sinceInSeconds = Long.parseLong(since);
            }
        }

        fetchLogCommandInput.withProperty(STD_ERR, stdErr);
        fetchLogCommandInput.withProperty(STD_OUT, stdOut);
        fetchLogCommandInput.withProperty(TIMESTAMPS, includeTimeStamp);
        fetchLogCommandInput.withProperty(TAIL, tail);
        fetchLogCommandInput.withProperty(SINCE, sinceInSeconds);
        fetchLogCommandInput.withProperty(DOCKER_CONTAINER_ID_PROP_NAME, containerState.id);

        return fetchLogCommandInput;
    }

    private void processContainerLogResponse(RequestContext context, byte[] log) {
        LogService.LogServiceState logServiceState = new LogService.LogServiceState();
        logServiceState.documentSelfLink = Service.getId(context.containerState.documentSelfLink);
        logServiceState.logs = log;
        logServiceState.tenantLinks = context.containerState.tenantLinks;

        sendRequest(Operation.createPost(this, LogService.FACTORY_LINK)
                .setBody(logServiceState)
                .setContextId(context.request.getRequestId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        if (context.request.serviceTaskCallback.isEmpty()) {
                            /* avoid logging warnings */
                            patchTaskStage(context.request, TaskStage.FINISHED, null);
                        }
                    }
                }));
    }

    private void processCreateImage(RequestContext context) {
        sendRequest(Operation.createGet(this, context.containerState.descriptionLink)
                .setContextId(context.request.getRequestId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                    } else {
                        handleExceptions(context.request, context.operation, () -> {
                            context.containerDescription = o.getBody(ContainerDescription.class);

                            processAuthentication(context, () -> processContainerDescription(context));
                        });
                    }
                }));

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
    private void processAuthentication(RequestContext context, Runnable callback) {
        DockerImage image = DockerImage.fromImageName(context.containerDescription.image);

        if (image.getHost() == null) {
            // if there is no registry host we assume the host is docker hub, so no authentication
            // needed
            callback.run();
            return;
        }

        QueryTask registryQuery = QueryUtil.buildQuery(RegistryState.class, false);
        if (context.containerDescription.tenantLinks != null) {
            registryQuery.querySpec.query.addBooleanClause(QueryUtil.addTenantGroupAndUserClause(
                    context.containerDescription.tenantLinks));
        }
        registryQuery.querySpec.query.addBooleanClause(createAnyPropertyClause(
                String.format("*://%s", image.getHost()), RegistryState.FIELD_NAME_ADDRESS));

        List<String> registryLinks = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(), ContainerState.class).query(
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

    private void processContainerDescription(RequestContext context) {
        context.containerState.adapterManagementReference = context.containerDescription.instanceAdapterReference;

        CommandInput createImageCommandInput = new CommandInput(context.commandInput);

        URI imageReference = context.containerDescription.imageReference;

        CompletionHandler imageCompletionHandler = (o, ex) -> {
            if (ex != null) {
                fail(context.request, o, ex);
            } else {
                handleExceptions(
                        context.request,
                        context.operation,
                        () -> processCreateContainer(context, 0)
                );
            }
        };

        if (SystemContainerDescriptions.getAgentImageNameAndVersion()
                .equals(context.containerDescription.image)) {
            String ref = SystemContainerDescriptions.AGENT_IMAGE_REFERENCE;

            imageRetrievalManager.retrieveAgentImage(ref, context.request, (imageData) -> {
                processLoadedImageData(context, imageData, ref, imageCompletionHandler);
            });
        } else if (shouldTryCreateFromLocalImage(context.containerDescription)) {
            // try to create the container from a local image first. Only if the image is not available it will be
            // fetched according to the settings.
            logInfo("Trying to create the container using local image first...");
            handleExceptions(
                    context.request,
                    context.operation,
                    () -> processCreateContainer(context, 0));
        } else if (imageReference == null) {
            // canonicalize the image name (add latest tag if needed)
            String fullImageName = DockerImage.fromImageName(context.containerDescription.image)
                    .toString();

            // use 'fromImage' - this will perform a docker pull
            createImageCommandInput.withProperty(DOCKER_IMAGE_FROM_PROP_NAME,
                    fullImageName);

            getHost().log(Level.INFO, "Pulling image: %s %s", fullImageName,
                    context.request.getRequestTrackingLog());
            processPullImageFromRegistry(context, createImageCommandInput, imageCompletionHandler);
        } else {
            // fetch the image first, then execute a image load command
            getHost().log(Level.INFO, "Downloading image from: %s %s", imageReference,
                    context.request.getRequestTrackingLog());
            try {
                File tempFile = File.createTempFile(DOWNLOAD_TEMPFILE_PREFIX, null);
                tempFile.deleteOnExit();

                Operation fetchOp = Operation.createGet(imageReference);

                fetchOp.setExpiration(ServiceUtils.getExpirationTimeFromNowInMicros(
                        getHost().getOperationTimeoutMicros()))
                        .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                        .setContextId(context.request.getRequestId())
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                if (!tempFile.delete()) {
                                    this.logWarning("Failed to delete temp file: %s %s", tempFile,
                                            context.request.getRequestTrackingLog());
                                }
                                fail(context.request, ex);

                            } else {
                                // Hack: until issue:
                                // https://www.pivotaltracker.com/projects/1471320/stories/111849709
                                // tempFile is not ready by the time it gets here.
                                for (int i = 0; i < 200; i++) {
                                    if (tempFile.length() > 0) {
                                        break;
                                    }

                                    try {
                                        Thread.sleep(50);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                                getHost().log(Level.INFO,
                                        "Finished download of %d bytes from %s to %s %s",
                                        tempFile.length(), o.getUri(), tempFile.getAbsolutePath(),
                                        context.request.getRequestTrackingLog());

                                processDownloadedImage(context, tempFile, imageCompletionHandler);
                            }
                        });

                // TODO ssl trust / credentials for the image server
                FileUtils.getFile(getHost().getClient(), fetchOp, tempFile);

            } catch (IOException x) {
                throw new RuntimeException("Failure downloading image from: " + imageReference
                        + context.request.getRequestTrackingLog(), x);
            }
        }
    }

    /**
     * read the temp file containing the downloaded image from the file system and proceed with
     * imageCompletionHandler
     *
     * @param context
     * @param tempFile
     * @param imageCompletionHandler
     */
    private void processDownloadedImage(RequestContext context, File tempFile,
            CompletionHandler imageCompletionHandler) {

        Operation fileReadOp = Operation.createPatch(null)
                .setContextId(context.request.getRequestId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                        return;
                    }

                    byte[] imageData = o.getBody(byte[].class);
                    if (!tempFile.delete()) {
                        this.logWarning("Failed to delete temp file: %s %s", tempFile,
                                context.request.getRequestTrackingLog());
                    }

                    processLoadedImageData(context, imageData,
                            context.containerDescription.imageReference.toString(),
                            imageCompletionHandler);
                });

        FileUtils.readFileAndComplete(fileReadOp, tempFile);
    }

    private void processLoadedImageData(RequestContext context, byte[] imageData,
            String fileName,
            CompletionHandler imageCompletionHandler) {
        if (imageData == null || imageData.length == 0) {
            String errMsg = String.format("No content loaded for file: %s %s",
                    fileName,
                    context.request.getRequestTrackingLog());
            this.logSevere(errMsg);
            imageCompletionHandler.handle(null, new LocalizableValidationException(errMsg, "adapter.load.image.empty", fileName,
                    context.request.getRequestTrackingLog()));
            return;
        }

        logInfo("Loaded content for file: %s %s. Now sending to host...", fileName,
                context.request.getRequestTrackingLog());

        CommandInput loadCommandInput = new CommandInput(context.commandInput)
                .withProperty(DOCKER_IMAGE_DATA_PROP_NAME, imageData);
        context.executor.loadImage(loadCommandInput, imageCompletionHandler);
    }

    private void processPullImageFromRegistry(RequestContext context,
            CommandInput createImageCommandInput, CompletionHandler imageCompletionHandler) {

        ensurePropertyExists((retryCountProperty) -> {
            processPullImageFromRegistryWithRetry(context, createImageCommandInput,
                    imageCompletionHandler, 0, retryCountProperty);
        });
    }

    private void processPullImageFromRegistryWithRetry(RequestContext context,
            CommandInput createImageCommandInput, CompletionHandler imageCompletionHandler,
            int retriesCount, int maxRetryCount) {
        AtomicInteger retryCount = new AtomicInteger(retriesCount);
        context.executor.createImage(createImageCommandInput, (op, ex) -> {
            if (ex != null && RETRIABLE_HTTP_STATUSES.contains(op.getStatusCode())
                        && retryCount.getAndIncrement() < maxRetryCount) {
                String fullImageName = DockerImage.fromImageName(context.containerDescription.image)
                        .toString();
                logWarning("Pulling image %s failed with %s. Retries left %d",
                        fullImageName, Utils.toString(ex),
                        maxRetryCount - retryCount.get());
                processPullImageFromRegistryWithRetry(context, createImageCommandInput,
                        imageCompletionHandler, retryCount.get(), maxRetryCount);
            } else {
                imageCompletionHandler.handle(op, ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void processCreateContainer(RequestContext context, int retriesCount) {
        AssertUtil.assertNotEmpty(context.containerState.names, "containerState.names");

        String fullImageName = DockerImage.fromImageName(context.containerDescription.image)
                .toString();

        CommandInput createCommandInput = new CommandInput(context.commandInput)
                .withProperty(DOCKER_CONTAINER_IMAGE_PROP_NAME,
                        fullImageName)
                .withProperty(DOCKER_CONTAINER_TTY_PROP_NAME, true)
                .withProperty(DOCKER_CONTAINER_OPEN_STDIN_PROP_NAME, true)
                .withPropertyIfNotNull(DOCKER_CONTAINER_COMMAND_PROP_NAME,
                        CommandUtil.spread(context.containerDescription.command))
                .withProperty(DOCKER_CONTAINER_NAME_PROP_NAME,
                        context.containerState.names.get(0))
                .withPropertyIfNotNull(DOCKER_CONTAINER_ENV_PROP_NAME,
                        context.containerState.env)
                .withPropertyIfNotNull(DOCKER_CONTAINER_USER_PROP_NAME,
                        context.containerDescription.user)
                .withPropertyIfNotNull(DOCKER_CONTAINER_ENTRYPOINT_PROP_NAME,
                        context.containerDescription.entryPoint)
                .withPropertyIfNotNull(DOCKER_CONTAINER_HOSTNAME_PROP_NAME,
                        context.containerDescription.hostname)
                .withPropertyIfNotNull(DOCKER_CONTAINER_DOMAINNAME_PROP_NAME,
                        context.containerDescription.domainName)
                .withPropertyIfNotNull(DOCKER_CONTAINER_WORKING_DIR_PROP_NAME,
                        context.containerDescription.workingDir);

        Map<String, Object> hostConfig = getOrAddMap(createCommandInput,
                DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME);

        hostConfig.put(MEMORY_SWAP_PROP_NAME, context.containerDescription.memorySwapLimit);

        hostConfig.put(MEMORY_PROP_NAME, context.containerState.memoryLimit);
        hostConfig.put(CPU_SHARES_PROP_NAME, context.containerState.cpuShares);

        // TODO Can't limit the storage? https://github.com/docker/docker/issues/3804

        hostConfig.put(DNS_PROP_NAME, context.containerDescription.dns);
        hostConfig.put(DNS_SEARCH_PROP_NAME, context.containerDescription.dnsSearch);
        hostConfig.put(EXTRA_HOSTS_PROP_NAME, context.containerState.extraHosts);

        // the volumes are added as binds property
        hostConfig.put(BINDS_PROP_NAME, filterVolumeBindings(context.containerState.volumes));
        hostConfig.put(VOLUME_DRIVER, context.containerDescription.volumeDriver);
        hostConfig.put(CAP_ADD_PROP_NAME, context.containerDescription.capAdd);
        hostConfig.put(CAP_DROP_PROP_NAME, context.containerDescription.capDrop);
        hostConfig.put(NETWORK_MODE_PROP_NAME, context.containerDescription.networkMode);
        hostConfig.put(LINKS_PROP_NAME, context.containerState.links);
        hostConfig.put(PRIVILEGED_PROP_NAME, context.containerDescription.privileged);
        hostConfig.put(PID_MODE_PROP_NAME, context.containerDescription.pidMode);

        if (context.containerDescription.publishAll != null) {
            hostConfig.put(PUBLISH_ALL, context.containerDescription.publishAll);
        }

        // Mapping properties from containerState to the docker config:
        hostConfig.put(VOLUMES_FROM_PROP_NAME, context.containerState.volumesFrom);

        // Add first container network to avoid container to be connected to default network.
        // Other container networks will be added after container is created.
        // Docker APIs fail if there is more than one network added to the container when it is created
        if (context.containerState.networks != null && !context.containerState.networks.isEmpty()) {
            createNetworkConfig(createCommandInput, context.containerState.networks.entrySet().iterator().next());
        }

        if (context.containerState.ports != null) {
            addPortBindings(createCommandInput, context.containerState.ports);
        }

        if (context.containerDescription.logConfig != null) {
            addLogConfiguration(createCommandInput, context.containerDescription.logConfig);
        }

        if (context.containerDescription.restartPolicy != null) {
            Map<String, Object> restartPolicy = new HashMap<>();
            restartPolicy.put(RESTART_POLICY_NAME_PROP_NAME,
                    context.containerDescription.restartPolicy);
            if (context.containerDescription.maximumRetryCount != null
                    && context.containerDescription.maximumRetryCount != 0) {
                restartPolicy.put(RESTART_POLICY_RETRIES_PROP_NAME,
                        context.containerDescription.maximumRetryCount);
            }
            hostConfig.put(RESTART_POLICY_PROP_NAME, restartPolicy);
        }

        if (context.containerState.volumes != null) {
            Map<String, Object> volumeMap = new HashMap<>();
            for (String volume : context.containerState.volumes) {
                // docker expects each volume to be mapped to an empty object (an empty map)
                // where the key is the container_path (second element in the volume string)
                String containerPart = VolumeBinding.fromString(volume).getContainerPart();
                volumeMap.put(containerPart, Collections.emptyMap());
            }

            createCommandInput.withProperty(DOCKER_CONTAINER_VOLUMES_PROP_NAME, volumeMap);
        }

        if (context.containerDescription.device != null) {
            List<?> devices = Arrays.stream(context.containerDescription.device)
                    .map(deviceStr -> DockerDevice.fromString(deviceStr).toMap())
                    .collect(Collectors.toList());

            hostConfig.put(DEVICES_PROP_NAME, devices);
        }

        // copy custom properties
        if (context.containerState.customProperties != null) {
            for (Map.Entry<String, String> customProperty : context.containerState.customProperties
                    .entrySet()) {
                createCommandInput.withProperty(customProperty.getKey(),
                        customProperty.getValue());
            }
        }

        if (ContainerHostUtil.isVicHost(context.computeState)) {
            // VIC has requires several mandatory elements, add them
            addVicRequiredConfig(createCommandInput);
        }

        AtomicInteger retryCount = new AtomicInteger(retriesCount);
        ensurePropertyExists((retryCountProperty) -> {
            context.executor.createContainer(createCommandInput, (o, ex) -> {
                if (ex != null) {
                    if (shouldTryCreateFromLocalImage(context.containerDescription)) {
                        logInfo("Unable to create container using local image. Will be fetched from a remote "
                                + "location...");
                        context.containerDescription.customProperties
                                .put(DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY, "false");
                        processContainerDescription(context);
                    } else if (RETRIABLE_HTTP_STATUSES.contains(o.getStatusCode())
                            && retryCount.getAndIncrement() < retryCountProperty) {
                        logWarning("Provisioning for container %s failed with %s. Retries left %d",
                                context.containerState.names.get(0), Utils.toString(ex),
                                retryCountProperty - retryCount.get());
                        processCreateContainer(context, retryCount.get());
                    } else {
                        fail(context.request, o, ex);
                    }
                } else {
                    handleExceptions(context.request, context.operation, () -> {
                        Map<String, Object> body = o.getBody(Map.class);
                        context.containerState.id = (String) body
                                .get(DOCKER_CONTAINER_ID_PROP_NAME);
                        processCreatedContainer(context);
                    });
                }
            });
        });
    }

    private void addVicRequiredConfig(CommandInput input) {
        // networking config element is mandatory for VIC
        // https://github.com/vmware/vic/blob/8b3ad1a36597f65449ce4a5b77176ccbe4a7c301/lib/apiservers/engine/backends/container.go#L1372
        getOrAddMap(input, DOCKER_CONTAINER_NETWORKING_CONFIG_PROP_NAME);
        // config element is mandatory for VIC
        // https://github.com/vmware/vic/blob/8b3ad1a36597f65449ce4a5b77176ccbe4a7c301/lib/apiservers/engine/backends/container.go#L1372
        getOrAddMap(input, DOCKER_CONTAINER_CONFIG_PROP_NAME);
    }

    private void addNetworkConfig(CommandInput input, String containerId, String networkId,
            ServiceNetwork network) {
        Map<String, Object> endpointConfig = getOrAddMap(input,
                DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG_PROP_NAME);

        mapContainerNetworkToNetworkConfig(network, endpointConfig);

        input.withProperty(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.CONTAINER_PROP_NAME,
                containerId);
        input.withProperty(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME, networkId);
    }

    private void createNetworkConfig(CommandInput input, Entry<String, ServiceNetwork> network) {
        Map<String, Object> endpointConfig = new HashMap<>();
        mapContainerNetworkToNetworkConfig(network.getValue(), endpointConfig);

        Map<String, Object> endpointsConfig = new HashMap<>();
        endpointsConfig.put(network.getKey(), endpointConfig);

        Map<String, Object> networkConfig = getOrAddMap(input,
                DOCKER_CONTAINER_NETWORKING_CONFIG_PROP_NAME);
        networkConfig.put(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINTS_CONFIG_PROP_NAME,
                endpointsConfig);

    }

    private void mapContainerNetworkToNetworkConfig(ServiceNetwork network, Map<String, Object> endpointConfig) {
        Map<String, Object> ipamConfig = new HashMap<>();
        if (network.ipv4_address != null) {
            ipamConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG.IPV4_CONFIG,
                    network.ipv4_address);
        }

        if (network.ipv6_address != null) {
            ipamConfig
                    .put(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG.IPV6_CONFIG,
                            network.ipv6_address);
        }

        if (!ipamConfig.isEmpty()) {
            endpointConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.IPAM_CONFIG_PROP_NAME,
                    ipamConfig);
        }

        if (network.aliases != null) {
            endpointConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.ALIASES,
                    network.aliases);
        }

        if (network.links != null) {
            endpointConfig.put(
                    DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINT_CONFIG.LINKS,
                    network.links);
        }
    }

    private boolean shouldTryCreateFromLocalImage(ContainerDescription containerDescription) {
        if (containerDescription.customProperties == null) {
            return false;
        }
        String useLocalImageFirst = containerDescription.customProperties.get
                (DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY);

        // Flag that forces container to be started from a local image and only if the image is not available
        // download it from a registry.
        return Boolean.valueOf(useLocalImageFirst);
    }

    private void fetchRegistryAuthState(String registryStateLink, RequestContext context,
            Runnable callback) {
        URI registryStateUri = UriUtils.buildUri(getHost(), registryStateLink,
                UriUtils.URI_PARAM_ODATA_EXPAND);

        Operation getRegistry = Operation.createGet(registryStateUri)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        context.operation.fail(ex);
                        return;
                    }

                    RegistryAuthState registryState = o.getBody(RegistryAuthState.class);
                    if (registryState.authCredentials != null) {
                        AuthCredentialsServiceState authState = registryState.authCredentials;
                        AuthCredentialsType authType = AuthCredentialsType.valueOf(authState.type);
                        if (AuthCredentialsType.Password.equals(authType)) {
                            // create and encode AuthConfig
                            AuthConfig authConfig = new AuthConfig();
                            authConfig.username = authState.userEmail;
                            authConfig.password = EncryptionUtils.decrypt(authState.privateKey);
                            authConfig.email = "";
                            authConfig.auth = "";
                            DockerImage image = DockerImage.fromImageName(
                                    context.containerDescription.image);
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

    /**
     * get a mapped value or add a new one if it's not mapped yet
     *
     * @param commandInput
     * @param propName
     * @return
     */
    private Map<String, Object> getOrAddMap(CommandInput commandInput, String propName) {
        Map<String, Object> newMap = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> oldMap = (Map<String, Object>) commandInput.getProperties()
                .putIfAbsent(propName, newMap);

        return oldMap == null ? newMap : oldMap;
    }

    private void processCreatedContainer(RequestContext context) {
        if (context.containerState.networks != null && !context.containerState.networks.isEmpty()) {
            connectCreatedContainerToNetworks(context);
        } else {
            startCreatedContainer(context);
        }
    }

    private void connectCreatedContainerToNetworks(RequestContext context) {
        AtomicInteger count = new AtomicInteger(context.containerState.networks.size());
        AtomicBoolean error = new AtomicBoolean();

        for (Entry<String, ServiceNetwork> entry : context.containerState.networks.entrySet()) {

            CommandInput connectCommandInput = new CommandInput(context.commandInput);

            String containerId = context.containerState.id;
            String networkId = entry.getKey();

            addNetworkConfig(connectCommandInput, context.containerState.id, entry.getKey(),
                    entry.getValue());

            context.executor.connectContainerToNetwork(connectCommandInput, (o, ex) -> {
                if (ex != null) {
                    logWarning("Exception while connecting container [%s] to network [%s]",
                            containerId, networkId);
                    if (error.compareAndSet(false, true)) {
                        // Update the container state so further actions (e.g. cleanup) can be performed
                        context.containerState.status = ContainerState.CONTAINER_ERROR_STATUS;
                        context.containerState.powerState = ContainerState.PowerState.ERROR;
                        context.requestFailed = true;
                        inspectContainer(context);

                        fail(context.request, o, ex);
                    }
                } else if (count.decrementAndGet() == 0) {
                    startCreatedContainer(context);
                }
            });
        }
    }

    private void startCreatedContainer(RequestContext context) {
        CommandInput startCommandInput = new CommandInput(context.commandInput)
                .withProperty(DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);

        // add port bindings
        if (context.containerState.ports != null) {
            addPortBindings(startCommandInput, context.containerState.ports);
        }

        context.executor.startContainer(startCommandInput, (o, ex) -> {
            if (ex != null) {
                fail(context.request, o, ex);
            } else {
                handleExceptions(context.request, context.operation, () -> {
                    NetworkUtils.updateConnectedNetworks(getHost(), context.containerState, 1);
                    inspectContainer(context);
                });
            }
        });
    }

    /**
     * Map log configurations.
     *
     * @param input
     * @param logConfig
     */
    private void addLogConfiguration(CommandInput input, LogConfig logConfig) {
        Map<String, Object> hostConfig = getOrAddMap(input, DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME);

        Map<String, Object> logConfigMap = new HashMap<>();
        logConfigMap.put(DOCKER_CONTAINER_LOG_CONFIG_PROP_TYPE_NAME, logConfig.type);
        logConfigMap.put(DOCKER_CONTAINER_LOG_CONFIG_PROP_CONFIG_NAME, logConfig.config);

        hostConfig.put(DOCKER_CONTAINER_LOG_CONFIG_PROP_NAME, logConfigMap);
    }

    /**
     * Map port binding to ExposedPorts and PortBinding
     *
     * ExposedPorts are only used by the API adapter, as the docker CLI will add that itself
     *
     * @param input
     * @param portBindings
     */
    private void addPortBindings(CommandInput input, List<PortBinding> portBindings) {
        Map<String, Map<String, String>> exposedPortsMap = new HashMap<>();
        input.withProperty(DOCKER_CONTAINER_EXPOSED_PORTS_PROP_NAME, exposedPortsMap);

        Map<String, Object> hostConfig = getOrAddMap(input,
                DOCKER_CONTAINER_HOST_CONFIG_PROP_NAME);

        Map<String, List<Map<String, String>>> portBindingsMap = new HashMap<>();
        hostConfig.put(DOCKER_CONTAINER_PORT_BINDINGS_PROP_NAME, portBindingsMap);

        for (PortBinding portBinding : portBindings) {
            DockerPortMapping mapping = DockerPortMapping.fromString(portBinding.toString());
            Map<String, List<Map<String, String>>> portDetails = mapping.toMap();
            portBindingsMap.putAll(portDetails);

            exposedPortsMap.put(mapping.getContainerPortAndProtocol(), Collections.emptyMap());
        }
    }

    @SuppressWarnings("unchecked")
    private void inspectContainer(RequestContext context) {
        CommandInput inspectCommandInput = new CommandInput(context.commandInput).withProperty(
                DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);

        getHost().log(Level.FINE, "Executing inspect container: %s %s",
                context.containerState.documentSelfLink, context.request.getRequestTrackingLog());

        if (context.containerState.id == null) {
            if (!context.requestFailed && (context.containerState.powerState == null
                    || context.containerState.powerState.isUnmanaged())) {
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            } else {
                fail(context.request, new IllegalStateException("container id is required"
                        + context.request.getRequestTrackingLog()));
            }
            return;
        }

        context.executor.inspectContainer(
                inspectCommandInput,
                (o, ex) -> {
                    if (ex != null) {
                        fail(context.request, o, ex);
                    } else {
                        handleExceptions(
                                context.request,
                                context.operation,
                                () -> {
                                    Map<String, Object> properties = o.getBody(Map.class);
                                    patchContainerState(context.request, context.containerState,
                                            properties, context);
                                });
                    }
                });
    }

    private void execContainer(RequestContext context) {
        String command = context.request.customProperties
                .get(ShellContainerExecutorService.COMMAND_KEY);
        if (command == null) {
            context.operation.fail(new LocalizableValidationException("Command not provided"
                    + context.request.getRequestTrackingLog(), "adapter.exec.container.command.missing",
                    context.request.getRequestTrackingLog()));
        }

        String[] commandArr = command
                .split(ShellContainerExecutorService.COMMAND_ARGUMENTS_SEPARATOR);

        CommandInput execCommandInput = new CommandInput(context.commandInput).withProperty(
                DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id).withProperty(
                DOCKER_EXEC_COMMAND_PROP_NAME, commandArr);

        if (context.request.customProperties.get(DOCKER_EXEC_ATTACH_STDERR_PROP_NAME) != null) {
            execCommandInput.withProperty(DOCKER_EXEC_ATTACH_STDERR_PROP_NAME,
                    context.request.customProperties.get(DOCKER_EXEC_ATTACH_STDERR_PROP_NAME));
        }

        if (context.request.customProperties.get(DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME) != null) {
            execCommandInput.withProperty(DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME,
                    context.request.customProperties.get(DOCKER_EXEC_ATTACH_STDOUT_PROP_NAME));
        }

        getHost().log(Level.FINE, "Executing command in container: %s %s",
                context.containerState.documentSelfLink,
                context.request.getRequestTrackingLog());

        if (context.containerState.id == null) {
            fail(context.request, new IllegalStateException("container id is required" +
                    context.request.getRequestTrackingLog()));
            return;
        }

        context.executor.execContainer(execCommandInput, (op, ex) -> {
            if (ex != null) {
                context.operation.fail(ex);
            } else {
                if (op.hasBody()) {
                    context.operation.setBody(op.getBody(String.class));
                }
                context.operation.complete();
            }
        });
    }

    private void fetchContainerStats(RequestContext context) {
        if (context.containerState.powerState != PowerState.RUNNING) {
            fail(context.request, new IllegalStateException(
                    "Can't fetch stats from a stopped container without blocking: "
                            + context.containerState.documentSelfLink
                            + context.request.getRequestTrackingLog()));
            return;
        }

        // currently VIC does not support container stats
        if (ContainerHostUtil.isVicHost(context.computeState)) {
            return;
        }

        CommandInput statsCommandInput = new CommandInput(context.commandInput).withProperty(
                DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);

        getHost().log(Level.FINE, "Executing fetch container stats: %s %s",
                context.containerState.documentSelfLink, context.request.getRequestTrackingLog());

        context.executor.fetchContainerStats(statsCommandInput, (o, ex) -> {
            if (ex != null) {
                notifyFailedHealthStatus(context);
                fail(context.request, o, ex);
            } else {
                handleExceptions(context.request, context.operation, () -> {
                    String stats = o.getBody(String.class);
                    processContainerStats(context, stats, null);
                });
            }
        });
    }

    private void notifyFailedHealthStatus(RequestContext context) {
        boolean healthCheckSuccess = false;
        processContainerStats(context, null, healthCheckSuccess);
    }

    private void processContainerStats(RequestContext context, String stats,
            Boolean healthCheckSuccess) {
        getHost().log(Level.FINE, "Updating container stats: %s %s",
                context.request.resourceReference, context.request.getRequestTrackingLog());

        ContainerStats containerStats = ContainerStatsEvaluator.calculateStatsValues(stats);
        containerStats.healthCheckSuccess = healthCheckSuccess;
        String containerLink = context.request.resourceReference.getPath();
        URI uri = UriUtils.buildUri(getHost(), containerLink);
        sendRequest(Operation.createPatch(uri)
                .setBody(containerStats)
                .setCompletion((o, ex) -> {
                    patchTaskStage(context.request, TaskStage.FINISHED, ex);
                }));
    }

    private void patchContainerState(ContainerInstanceRequest request,
            ContainerState containerState, Map<String, Object> properties, RequestContext context) {

        // start with a new ContainerState object because we don't want to overwrite with stale data
        ContainerState newContainerState = new ContainerState();
        newContainerState.documentSelfLink = containerState.documentSelfLink;
        newContainerState.documentExpirationTimeMicros = -1; // make sure the expiration is reset.
        newContainerState.adapterManagementReference = containerState.adapterManagementReference;

        // copy properties into the ContainerState's attributes
        newContainerState.attributes = properties.entrySet()
                .stream()
                .filter((e) -> !FILTER_PROPERTIES.contains(e.getKey()))
                .collect(Collectors.toMap(
                        (e) -> e.getKey(),
                        (e) -> Utils.toJson(e.getValue())));

        new ContainerStateMapper().propertiesToContainerState(newContainerState, properties);

        getHost().log(Level.FINE, "Patching ContainerState: %s %s",
                containerState.documentSelfLink, request.getRequestTrackingLog());
        sendRequest(Operation
                .createPatch(request.getContainerStateReference())
                .setBody(newContainerState)
                .setCompletion((o, ex) -> {
                    if (!context.requestFailed) {
                        patchTaskStage(request, TaskStage.FINISHED, ex);
                    }
                    if (newContainerState.powerState == PowerState.RUNNING) {
                        // request fetch stats

                        ContainerInstanceRequest containerRequest = new ContainerInstanceRequest();
                        containerRequest.operationTypeId = ContainerOperationType.STATS.id;
                        containerRequest.resourceReference = request.resourceReference;
                        containerRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();

                        RequestContext newContext = new RequestContext();
                        newContext.containerState = newContainerState;
                        newContext.computeState = context.computeState;
                        newContext.containerDescription = context.containerDescription;
                        newContext.request = containerRequest;
                        newContext.commandInput = context.commandInput;
                        newContext.executor = context.executor;
                        newContext.operation = context.operation;

                        processOperation(newContext);
                        return;
                    }
                }));
    }

    private void processDeleteContainer(RequestContext context) {
        CommandInput commandInput = new CommandInput(context.commandInput).withProperty(
                DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);

        context.executor.removeContainer(commandInput, (o, ex) -> {
            if (ex != null) {
                if (ex instanceof ProtocolException
                        && ex.getMessage().contains(DELETE_CONTAINER_MISSING_ERROR)) {
                    logWarning("Container %s not found", context.containerState.id);
                    patchTaskStage(context.request, TaskStage.FINISHED, null);
                } else {
                    fail(context.request, o, ex);
                }
            } else {
                NetworkUtils.updateConnectedNetworks(getHost(), context.containerState, -1);
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            }
        });
    }

    private void processStartContainer(RequestContext context) {
        ensurePropertyExists((retryCountProperty) -> {
            processStartContainerWithRetry(context, 0, retryCountProperty);
        });
    }

    private void processStartContainerWithRetry(RequestContext context, int retriesCount,
            Integer maxRetryCount) {
        AtomicInteger retryCount = new AtomicInteger(retriesCount);
        CommandInput startCommandInput = new CommandInput(context.commandInput)
                .withProperty(DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);
        context.executor.startContainer(startCommandInput, (o, ex) -> {
            if (ex != null) {
                if (RETRIABLE_HTTP_STATUSES.contains(o.getStatusCode())
                        && retryCount.getAndIncrement() < maxRetryCount) {
                    logWarning("Starting container %s failed with %s. Retries left %d",
                            context.containerState.names.get(0), Utils.toString(ex),
                            maxRetryCount - retryCount.get());
                    processStartContainerWithRetry(context, retryCount.get(), maxRetryCount);
                } else {
                    fail(context.request, o, ex);
                }
            } else {
                handleExceptions(context.request, context.operation, () -> {
                    NetworkUtils.updateConnectedNetworks(getHost(), context.containerState, 1);
                    inspectContainer(context);
                });
            }
        });
    }

    private void processStopContainer(RequestContext context) {
        ensurePropertyExists((retryCountProperty) -> {
            processStopContainerWithRetry(context, 0, retryCountProperty);
        });
    }

    private void processStopContainerWithRetry(RequestContext context, int retriesCount,
            int maxRetryCount) {
        AtomicInteger retryCount = new AtomicInteger(retriesCount);
        CommandInput stopCommandInput = new CommandInput(context.commandInput)
                .withProperty(DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);
        context.executor.stopContainer(stopCommandInput, (o, ex) -> {
            if (ex != null) {
                if (RETRIABLE_HTTP_STATUSES.contains(o.getStatusCode())
                        && retryCount.getAndIncrement() < maxRetryCount) {
                    logWarning("Stopping container %s failed with %s. Retries left %d",
                            context.containerState.names.get(0), Utils.toString(ex),
                            maxRetryCount - retryCount.get());
                    processStopContainerWithRetry(context, retryCount.get(), maxRetryCount);
                } else {
                    fail(context.request, o, ex);
                }
            } else {
                handleExceptions(context.request, context.operation, () -> {
                    NetworkUtils.updateConnectedNetworks(getHost(), context.containerState, -1);
                    inspectContainer(context);
                });
            }
        });
    }

    private void ensurePropertyExists(Consumer<Integer> callback) {
        if (retriesCount != null) {
            callback.accept(retriesCount);
        } else {
            String maxRetriesCountConfigPropPath = UriUtils.buildUriPath(
                    ConfigurationFactoryService.SELF_LINK,
                    PROVISION_CONTAINER_RETRIES_COUNT_PARAM_NAME);
            sendRequest(Operation.createGet(this, maxRetriesCountConfigPropPath)
                    .setCompletion((o, ex) -> {
                        /** in case of exception the default retry count will be 3 */
                        retriesCount = Integer.valueOf(3);
                        if (ex == null) {
                            retriesCount = Integer.valueOf(
                                    o.getBody(ConfigurationState.class).value);
                        }
                        callback.accept(retriesCount);
                    }));
        }
    }

    /**
     * Filter out volume bindings without host-src or volume name. Each volume binding is a
     * string in the following form: [volume-name|host-src:]container-dest[:ro] Both host-src,
     * and container-dest must be an absolute path.
     */
    private List<String> filterVolumeBindings(String[] volumes) {
        List<String> volumeBindings = new ArrayList<>();
        if (volumes != null) {
            for (String volume : volumes) {
                VolumeBinding binding = VolumeBinding.fromString(volume);
                if (binding.getHostPart() != null) {
                    volumeBindings.add(volume);
                }
            }
        }
        return volumeBindings;
    }
}
