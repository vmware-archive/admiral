/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
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
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_CREATE_USE_BUNDLED_IMAGE;
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
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_HOST_CONFIG.ULIMITS;
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
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.SINCE;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.STD_ERR;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.STD_OUT;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.TAIL;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.TIMESTAMPS;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.http.HttpStatus;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG;
import com.vmware.admiral.adapter.docker.util.CommandUtil;
import com.vmware.admiral.adapter.docker.util.DockerDevice;
import com.vmware.admiral.adapter.docker.util.DockerImage;
import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.task.RetriableTask;
import com.vmware.admiral.common.task.RetriableTaskBuilder;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.ConfigurationUtil;
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
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.FileUtils;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Service for fulfilling ContainerInstanceRequest backed by a docker server
 */
public class DockerAdapterService extends AbstractDockerAdapterService {

    /**
     * prefix used for temp files used to store downloaded images
     */
    private static final String DOWNLOAD_TEMPFILE_PREFIX = "admiral";

    private static final String FILE_SCHEME = "file";

    /**
     * name of the feature toggle that enables stats collection for VCH
     */
    private static final String ALLOW_VCH_STATS_COLLECTION_PROP_NAME = "allow.vch.stats.collection";

    /**
     * default delays to wait before retrying a failed container operation
     *
     * TODO make this configurable
     */
    private static final Long[] DEFAULT_RETRY_AFTER_SECONDS = { 5L, 10L, 30L };

    /**
     * default delays to wait before retrying a failed container inspect operation.
     *
     * TODO make this configurable
     */
    private static final Long[] INSPECT_RETRY_AFTER_SECONDS = DEFAULT_RETRY_AFTER_SECONDS;

    /**
     * default delays to wait before retrying a failed container create operation.
     *
     * TODO make this configurable
     */
    private static final Long[] CREATE_RETRY_AFTER_SECONDS = DEFAULT_RETRY_AFTER_SECONDS;

    /**
     * default delays to wait before retrying a failed container start operation.
     *
     * TODO make this configurable
     */
    private static final Long[] START_RETRY_AFTER_SECONDS = DEFAULT_RETRY_AFTER_SECONDS;

    /**
     * default delays to wait before retrying a failed container stop operation.
     *
     * TODO make this configurable
     */
    private static final Long[] STOP_RETRY_AFTER_SECONDS = DEFAULT_RETRY_AFTER_SECONDS;

    /**
     * default delays to wait before retrying a failed container delete operation.
     *
     * TODO make this configurable
     */
    private static final Long[] DELETE_RETRY_AFTER_SECONDS = DEFAULT_RETRY_AFTER_SECONDS;

    /**
     * default delays to wait before retrying a failed container connect to network operation.
     *
     * TODO make this configurable
     */

    private static final Long[] CONNECT_NETWORK_RETRY_AFTER_SECONDS = DEFAULT_RETRY_AFTER_SECONDS;

    /**
     * default delays to wait before retrying a failed pull container image operation.
     *
     * TODO make this configurable
     */
    private static final Long[] PULL_IMAGE_RETRY_AFTER_SECONDS = DEFAULT_RETRY_AFTER_SECONDS;

    /**
     * default delays to wait before retrying a failed load container image operation.
     *
     * TODO make this configurable
     */
    private static final Long[] LOAD_IMAGE_RETRY_AFTER_SECONDS = DEFAULT_RETRY_AFTER_SECONDS;

    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER;

    public static final String PROVISION_CONTAINER_RETRIES_COUNT_PARAM_NAME = "provision.container.retries.count";
    public static final int PROVISION_CONTAINER_RETRIES_COUNT_DEFAULT = 3;

    public static final String PROVISION_CONTAINER_PULL_RETRIES_COUNT_PARAM_NAME = "provision.container.pull-image.retries.count";

    public static final String RETRIED_AFTER_FAILURE = "failedAfterRetry";

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
            HttpStatus.SC_GATEWAY_TIMEOUT);
    private static final String DELETE_CONTAINER_MISSING_ERROR = "error 404 for DELETE";

    private volatile Integer retriesCount;
    private volatile Integer pullRetriesCount;

    private static class RequestContext extends BaseRequestContext {
        public ComputeState computeState;
        public ContainerState containerState;
        public ContainerDescription containerDescription;

        public DockerAdapterCommandExecutor executor;
        /**
         * Flags the request as already failed. Used to avoid patching a FAILED task to FINISHED
         * state after inspecting a container.
         */
        public boolean requestFailed;
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

        ContainerInstanceRequest containerRequest = (ContainerInstanceRequest) context.request;
        ContainerOperationType operationType = containerRequest.getOperationType();
        logInfo("Processing operation request %s for resource %s %s",
                operationType, containerRequest.resourceReference,
                containerRequest.getRequestTrackingLog());

        if (ContainerOperationType.EXEC == operationType
                || ContainerOperationType.STATS == operationType) {
            // Exec is direct operation, stats will complete operation after completion
            context.operation = op;
        } else {
            op.complete();
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
                            context.containerState = o.getBody(ContainerState.class);
                            processContainerState(context);
                        });
                    }
                });
        handleExceptions(context.request, context.operation, () -> {
            logFine("Fetching ContainerState: %s %s", context.request.getRequestTrackingLog(),
                    context.request.getContainerStateReference());
            sendRequest(getContainerState);
        });
    }

    /*
     * process the ContainerState - fetch the referenced parent ComputeState
     */
    private void processContainerState(RequestContext context) {
        if (context.containerState.parentLink == null) {
            fail(context.request, new IllegalArgumentException(
                    "parentLink missing for container " + context.containerState.documentSelfLink));
            return;
        }

        getContainerHost(
                context.request,
                context.operation,
                UriUtils.buildUri(getHost(), context.containerState.parentLink),
                (computeState, commandInput) -> {
                    context.commandInput = commandInput;
                    context.executor = getCommandExecutor();
                    context.computeState = computeState;
                    handleExceptions(context.request, context.operation,
                            () -> processOperation(context));
                });
    }

    private void processOperation(RequestContext context) {
        ContainerInstanceRequest request = (ContainerInstanceRequest) context.request;
        try {
            switch (request.getOperationType()) {
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
                processInspectContainer(context);
                break;

            case EXEC:
                execContainer(context);
                break;

            case STATS:
                fetchContainerStats(context);
                break;

            default:
                fail(request, new IllegalArgumentException(
                        "Unexpected request type: " + request.getOperationType()
                                + request.getRequestTrackingLog()));
            }
        } catch (Throwable e) {
            fail(request, e);
        }
    }

    private void processFetchContainerLog(RequestContext context) {
        CommandInput fetchLogCommandInput = constructFetchLogCommandInput(context.request,
                context.commandInput, context.containerState);

        context.executor.fetchContainerLog(fetchLogCommandInput, (op, ex) -> {
            if (ex != null) {
                logWarning("Failure while fetching logs for container [%s] of host [%s]",
                        context.containerState.documentSelfLink,
                        context.computeState.documentSelfLink);
                fail(context.request, op, ex);
            } else {
                /* Write this to the log service */
                handleExceptions(context.request, context.operation, () -> {
                    byte[] log = null;
                    if (op.getBodyRaw() != null) {
                        if (Operation.MEDIA_TYPE_APPLICATION_OCTET_STREAM.equals(
                                op.getContentType())) {

                            log = op.getBody(byte[].class);
                        } else {
                            /* TODO check for encoding header */
                            String logStr = op.getBody(String.class);
                            if (logStr != null) {
                                log = logStr.getBytes();
                            }
                        }
                    }

                    if (log == null) {
                        log = "--".getBytes();
                        // log a warning
                        String containerId = Service.getId(context.containerState.documentSelfLink);
                        logWarning("Found empty logs for container %s", containerId);
                    }

                    processContainerLogResponse(context, log);
                });
            }
        });
    }

    private CommandInput constructFetchLogCommandInput(AdapterRequest request,
            CommandInput commandInput, ContainerState containerState) {
        CommandInput fetchLogCommandInput = new CommandInput(commandInput);
        boolean stdErr = true;
        boolean stdOut = true;
        boolean includeTimeStamp = false;
        String since = null;
        String tail = null;

        if (request.customProperties != null) {
            stdErr = Boolean.parseBoolean(request.customProperties.getOrDefault(STD_ERR,
                    String.valueOf(stdErr)));
            stdOut = Boolean.parseBoolean(request.customProperties.getOrDefault(STD_OUT,
                    String.valueOf(stdOut)));
            includeTimeStamp = Boolean.parseBoolean(request.customProperties.getOrDefault(
                    TIMESTAMPS, String.valueOf(includeTimeStamp)));
            since = request.customProperties.get(SINCE);
            tail = request.customProperties.get(TAIL);
        }

        fetchLogCommandInput.withProperty(STD_ERR, stdErr);
        fetchLogCommandInput.withProperty(STD_OUT, stdOut);
        fetchLogCommandInput.withProperty(TIMESTAMPS, includeTimeStamp);

        if (since != null && !since.isEmpty()) {
            fetchLogCommandInput.withProperty(SINCE, Long.parseLong(since));
        }

        if (tail != null && !tail.isEmpty()) {
            fetchLogCommandInput.withProperty(TAIL, Integer.parseInt(tail));
        }

        fetchLogCommandInput.withProperty(DOCKER_CONTAINER_ID_PROP_NAME, containerState.id);

        return fetchLogCommandInput;
    }

    private void processContainerLogResponse(RequestContext context, byte[] log) {
        LogService.LogServiceState logServiceState = new LogService.LogServiceState();
        logServiceState.documentSelfLink = Service.getId(context.containerState.documentSelfLink);

        // 256 bytes spare for service document data
        int maxDocumentSize = LogService.MAX_LOG_SIZE - 256;
        if (log.length > maxDocumentSize) {
            log = Arrays.copyOfRange(log, log.length - maxDocumentSize, log.length);
        }

        logServiceState.logs = log;
        logServiceState.tenantLinks = context.containerState.tenantLinks;

        sendRequest(Operation.createPost(this, LogService.FACTORY_LINK)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
                .setBodyNoCloning(logServiceState)
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

                            context.imageName = context.containerDescription.image;
                            processAuthentication(context,
                                    () -> processContainerDescription(context));
                        });
                    }
                }));

    }

    private void processContainerDescription(RequestContext context) {
        context.containerState.adapterManagementReference = context.containerDescription.instanceAdapterReference;

        CommandInput createImageCommandInput = new CommandInput(context.commandInput);

        URI imageReference = context.containerDescription.imageReference;

        Runnable imageCompletionAction = () -> {
            handleExceptions(
                    context.request,
                    context.operation,
                    () -> processCreateContainer(context));
        };

        if (SystemContainerDescriptions.getAgentImageNameAndVersion()
                .equals(context.containerDescription.image)) {
            String ref = SystemContainerDescriptions.AGENT_IMAGE_REFERENCE;

            imageRetrievalManager.retrieveAgentImage(ref, context.request, (imageData) -> {
                processLoadImageData(context, imageData, ref, imageCompletionAction);
            });
        } else if (shouldTryCreateFromLocalImage(context.containerDescription)) {
            if (getBundledImage(context.containerDescription) != null) {
                String ref = getBundledImage(context.containerDescription);
                imageRetrievalManager.retrieveAgentImage(ref, context.request, (imageData) -> {
                    processLoadImageData(context, imageData, ref, imageCompletionAction);
                });
            } else {
                // try to create the container from a local image first. Only if the image is not
                // available it will be fetched according to the settings.
                logInfo("Trying to create the container using local image first...");
                handleExceptions(
                        context.request,
                        context.operation,
                        () -> processCreateContainer(context));
            }
        } else if (imageReference == null) {
            // canonicalize the image name (add latest tag if needed)
            String fullImageName = DockerImage.fromImageName(context.containerDescription.image)
                    .toString();

            // use 'fromImage' - this will perform a docker pull
            createImageCommandInput.withProperty(DOCKER_IMAGE_FROM_PROP_NAME, fullImageName);

            logInfo("Pulling image: %s %s", fullImageName, context.request.getRequestTrackingLog());
            processPullImageFromRegistry(context, createImageCommandInput, imageCompletionAction);
        } else {
            // fetch the image first, then execute a image load command
            logInfo("Downloading image from: %s %s", imageReference,
                    context.request.getRequestTrackingLog());
            try {
                if (FILE_SCHEME.equals(imageReference.getScheme())) {
                    // for file scheme use the file and do not delete it (it is not a temp copy)
                    processDownloadedImage(context, new File(imageReference),
                            imageCompletionAction, false);
                } else {
                    // for not file scheme, download it to a temp file
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
                                        this.logWarning("Failed to delete temp file: %s %s",
                                                tempFile, context.request.getRequestTrackingLog());
                                    }
                                    fail(context.request, ex);
                                } else {
                                    logInfo("Finished download of %d bytes from %s to %s %s",
                                            tempFile.length(), o.getUri(),
                                            tempFile.getAbsolutePath(),
                                            context.request.getRequestTrackingLog());

                                    processDownloadedImage(context, tempFile,
                                            imageCompletionAction, true);
                                }
                            });

                    // TODO ssl trust / credentials for the image server
                    FileUtils.getFile(getHost().getClient(), fetchOp, tempFile);
                }

            } catch (IOException x) {
                throw new RuntimeException("Failure downloading image from: " + imageReference
                        + context.request.getRequestTrackingLog(), x);
            }
        }
    }

    /**
     * read the temp file containing the downloaded image from the file system and proceed with
     * imageCompletionAction
     *
     * @param context
     * @param tempFile
     * @param imageCompletionAction
     */
    private void processDownloadedImage(RequestContext context, File tempFile,
            Runnable imageCompletionAction, boolean isTempFile) {

        Operation fileReadOp = Operation.createPatch(null)
                .setContextId(context.request.getRequestId())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        fail(context.request, ex);
                        return;
                    }

                    byte[] imageData = o.getBody(byte[].class);
                    if (isTempFile && !tempFile.delete()) {
                        this.logWarning("Failed to delete temp file: %s %s", tempFile,
                                context.request.getRequestTrackingLog());
                    }

                    processLoadImageData(context, imageData,
                            context.containerDescription.imageReference.toString(),
                            imageCompletionAction);
                });

        FileUtils.readFileAndComplete(fileReadOp, tempFile);
    }

    private void processLoadImageData(RequestContext context, byte[] imageData, String fileName,
            Runnable imageCompletionAction) {
        // TODO consider merging this functionality with DockerHostAdapterImageService.doLoadImage
        if (imageData == null || imageData.length == 0) {
            String errMsg = String.format("No content loaded for file: %s %s",
                    fileName, context.request.getRequestTrackingLog());
            this.logSevere(errMsg);
            fail(context.request, new LocalizableValidationException(errMsg,
                    "adapter.load.image.empty", fileName, context.request.getRequestTrackingLog()));
            return;
        }

        logInfo("Loaded content for file: %s %s. Now sending to host...", fileName,
                context.request.getRequestTrackingLog());
        doLoadImage(context, imageData, fileName, imageCompletionAction);
    }

    private void doLoadImage(RequestContext context, byte[] imageData, String fileName,
            Runnable imageCompletionAction) {

        CommandInput loadImageCommandInput = new CommandInput(context.commandInput)
                .withProperty(DOCKER_IMAGE_DATA_PROP_NAME, imageData);

        ensurePullRetriesPropertyExists((retryCountProperty) -> {
            new RetriableTaskBuilder<Void>(
                    String.format("load-image-from-file-%s", fileName))
                            .withMaximumRetries(retryCountProperty)
                            .withRetryDelays(LOAD_IMAGE_RETRY_AFTER_SECONDS)
                            .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                            .withServiceHost(getHost())
                            .withTaskFunction(prepareLoadImageFunction(context, fileName,
                                    loadImageCommandInput))
                            .execute()
                            .whenComplete((ignore, ex) -> {
                                if (ex != null) {
                                    Throwable failureCause = ex instanceof CompletionException
                                            ? ex.getCause() : ex;
                                    fail(context.request, failureCause);
                                    return;
                                }

                                imageCompletionAction.run();
                            });
        });
    }

    private Function<RetriableTask<Void>, DeferredResult<Void>> prepareLoadImageFunction(
            RequestContext context, String fileName, CommandInput loadImageCommandInput) {
        return (task) -> {
            DeferredResult<Void> result = new DeferredResult<>();

            logFine("Loading container image from file [%s]", fileName);

            context.executor.loadImage(loadImageCommandInput, (o, ex) -> {
                if (ex == null) {
                    // Nothing to do, success completion will be
                    // handled on the retriable task completion
                    result.complete(null);
                    return;
                }

                if (isRetriableFailure(o.getStatusCode())) {
                    markRequestAsRetriedAfterFailure(context.request);
                } else {
                    task.preventRetries();
                    logSevere(
                            "Failure loading container image from file [%s] (status code: %s)",
                            fileName,
                            o.getStatusCode());
                }

                result.fail(DockerAdapterUtils.runtimeExceptionFromFailedDockerOperation(o, ex));
            });

            return result;
        };
    }

    private void processPullImageFromRegistry(RequestContext context,
            CommandInput createImageCommandInput, Runnable imageCompletionAction) {

        ensurePullRetriesPropertyExists((retryCountProperty) -> {
            String fullImageName = DockerImage.fromImageName(context.containerDescription.image)
                    .toString();
            new RetriableTaskBuilder<Void>(
                    String.format("pull-image-%s", fullImageName))
                            .withMaximumRetries(retryCountProperty)
                            .withRetryDelays(PULL_IMAGE_RETRY_AFTER_SECONDS)
                            .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                            .withServiceHost(getHost())
                            .withTaskFunction(
                                    preparePullImageFunction(context, createImageCommandInput))
                            .execute()
                            .whenComplete((ignore, ex) -> {
                                if (ex != null) {
                                    Throwable failureCause = ex instanceof CompletionException
                                            ? ex.getCause() : ex;
                                    fail(context.request, failureCause);
                                    return;
                                }

                                imageCompletionAction.run();
                            });
        });
    }

    private Function<RetriableTask<Void>, DeferredResult<Void>> preparePullImageFunction(
            RequestContext context, CommandInput createImageCommandInput) {
        return (task) -> {
            DeferredResult<Void> result = new DeferredResult<>();

            String fullImageName = DockerImage.fromImageName(context.containerDescription.image)
                    .toString();
            logFine("Pulling container image [%s]", fullImageName);

            context.executor.createImage(createImageCommandInput, (o, ex) -> {
                if (ex == null) {
                    // Nothing to do, success completion will be
                    // handled on the retriable task completion
                    result.complete(null);
                    return;
                }

                if (isRetriableFailure(o.getStatusCode())) {
                    markRequestAsRetriedAfterFailure(context.request);
                } else {
                    task.preventRetries();
                    logSevere(
                            "Failure pulling container image [%s] (status code: %s)",
                            fullImageName,
                            o.getStatusCode());
                }

                result.fail(DockerAdapterUtils.runtimeExceptionFromFailedDockerOperation(o, ex));
            });

            return result;
        };
    }

    private void processCreateContainer(RequestContext context) {
        doCreateContainer(context, prepareCreateContainerCommandInput(context));
    }

    private CommandInput prepareCreateContainerCommandInput(RequestContext context) {
        AssertUtil.assertNotEmpty(context.containerState.names, "containerState.names");

        String fullImageName = DockerImage.fromImageName(context.containerDescription.image)
                .toString();

        CommandInput createCommandInput = new CommandInput(context.commandInput)
                .withProperty(DOCKER_CONTAINER_IMAGE_PROP_NAME, fullImageName)
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
        if (context.containerState.ulimits != null) {
            hostConfig.put(ULIMITS, context.containerState.ulimits);
        }

        // TODO Can't limit the storage? https://github.com/docker/docker/issues/3804

        hostConfig.put(DNS_PROP_NAME, context.containerDescription.dns);
        hostConfig.put(DNS_SEARCH_PROP_NAME, context.containerDescription.dnsSearch);
        hostConfig.put(EXTRA_HOSTS_PROP_NAME, context.containerState.extraHosts);

        // the volumes are added as binds property
        hostConfig.put(BINDS_PROP_NAME,
                DockerAdapterUtils.filterVolumeBindings(context.containerState.volumes));
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
        // Docker APIs fail if there is more than one network added to the container when it is
        // created
        if (context.containerState.networks != null && !context.containerState.networks.isEmpty()) {
            createNetworkConfig(createCommandInput,
                    context.containerState.networks.entrySet().iterator().next());
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

        return createCommandInput;
    }

    private void doCreateContainer(RequestContext context, CommandInput createCommandInput) {
        ensurePropertyExists((retryCountProperty) -> {
            new RetriableTaskBuilder<Map<String, String>>(
                    String.format("create-container-%s", context.containerState.names.get(0)))
                            .withMaximumRetries(retryCountProperty)
                            .withRetryDelays(CREATE_RETRY_AFTER_SECONDS)
                            .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                            .withServiceHost(getHost())
                            .withTaskFunction(
                                    prepareCreateContainerFunction(context, createCommandInput))
                            .execute()
                            .whenComplete((createResponse, ex) -> {
                                if (ex != null) {
                                    if (shouldTryCreateFromLocalImage(
                                            context.containerDescription)) {
                                        logInfo("Unable to create container using local image. Will be fetched"
                                                + " from a remote location...");
                                        context.containerDescription.customProperties.put(
                                                DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY,
                                                "false");
                                        processContainerDescription(context);
                                    } else {
                                        Throwable failureCause = ex instanceof CompletionException
                                                ? ex.getCause() : ex;
                                        fail(context.request, failureCause);
                                    }
                                } else {
                                    handleExceptions(context.request, context.operation, () -> {
                                        String id = (String) createResponse
                                                .get(DOCKER_CONTAINER_ID_PROP_NAME);
                                        updateCreatedContainerStateWithId(context, id);
                                    });
                                }
                            });
        });
    }

    private Function<RetriableTask<Map<String, String>>, DeferredResult<Map<String, String>>> prepareCreateContainerFunction(
            RequestContext context, CommandInput createCommandInput) {

        return (task) -> {
            DeferredResult<Map<String, String>> deferredResult = new DeferredResult<>();

            context.executor.createContainer(createCommandInput, (o, ex) -> {
                if (ex == null) {
                    try {
                        // Nothing to do, success completion will be
                        // handled on the retriable task completion
                        @SuppressWarnings("unchecked")
                        Map<String, String> taskResult = o.getBody(Map.class);
                        deferredResult.complete(taskResult);
                    } catch (Throwable e) {
                        task.preventRetries();
                        deferredResult.fail(e);
                    }

                    return;
                }

                if (isRetriableFailure(o.getStatusCode())
                        && !shouldTryCreateFromLocalImage(context.containerDescription)) {
                    // if local image was currently preferred, another task
                    // will be submitted to try with remote image
                    markRequestAsRetriedAfterFailure(context.request);
                } else {
                    task.preventRetries();
                    logSevere(
                            "Failure creating container [%s] of host [%s] (status code: %s)",
                            context.containerState.names.get(0),
                            context.computeState.documentSelfLink,
                            o.getStatusCode());
                }

                deferredResult
                        .fail(DockerAdapterUtils.runtimeExceptionFromFailedDockerOperation(o, ex));
            });

            return deferredResult;
        };

    }

    private void updateCreatedContainerStateWithId(RequestContext context, String id) {
        context.containerState.id = id;
        sendRequest(Operation.createPatch(this, context.containerState.documentSelfLink)
                .setBody(context.containerState)
                .setReferer(getSelfLink())
                .setCompletion((op, e) -> {
                    if (e != null) {
                        logWarning(
                                "Could not patch container state for created container %s",
                                context.computeState.name);
                    }
                    processCreatedContainer(context);
                }));
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

        DockerAdapterUtils.mapContainerNetworkToNetworkConfig(network, endpointConfig);

        input.withProperty(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.CONTAINER_PROP_NAME,
                containerId);
        input.withProperty(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME, networkId);
    }

    private void createNetworkConfig(CommandInput input, Entry<String, ServiceNetwork> network) {
        Map<String, Object> endpointConfig = new HashMap<>();
        DockerAdapterUtils.mapContainerNetworkToNetworkConfig(network.getValue(), endpointConfig);

        Map<String, Object> endpointsConfig = new HashMap<>();
        endpointsConfig.put(network.getKey(), endpointConfig);

        Map<String, Object> networkConfig = getOrAddMap(input,
                DOCKER_CONTAINER_NETWORKING_CONFIG_PROP_NAME);
        networkConfig.put(DOCKER_CONTAINER_NETWORKING_CONNECT_CONFIG.ENDPOINTS_CONFIG_PROP_NAME,
                endpointsConfig);

    }

    private boolean shouldTryCreateFromLocalImage(ContainerDescription containerDescription) {
        if (containerDescription.customProperties == null) {
            return false;
        }
        String useLocalImageFirst = containerDescription.customProperties
                .get(DOCKER_CONTAINER_CREATE_USE_LOCAL_IMAGE_WITH_PRIORITY);

        // Flag that forces container to be started from a local image and only if the image is not
        // available download it from a registry.
        return Boolean.valueOf(useLocalImageFirst);
    }

    private String getBundledImage(ContainerDescription containerDescription) {
        if (containerDescription.customProperties == null) {
            return null;
        }
        // container image which is bundled and should be uploaded to the host.
        return containerDescription.customProperties.get(DOCKER_CONTAINER_CREATE_USE_BUNDLED_IMAGE);
    }

    /**
     * get a mapped value or add a new one if it's not mapped yet
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
            processStartContainer(context);
        }
    }

    private void connectCreatedContainerToNetworks(RequestContext context) {
        ensurePropertyExists((retryCountProperty) -> {
            List<DeferredResult<Void>> tasks = context.containerState.networks.entrySet().stream()
                    .map((entry) -> {
                        return new RetriableTaskBuilder<Void>(
                                String.format("connect-created-container-%s-to-networks",
                                        context.containerState.id))
                                                .withMaximumRetries(retryCountProperty)
                                                .withRetryDelays(
                                                        CONNECT_NETWORK_RETRY_AFTER_SECONDS)
                                                .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                                                .withServiceHost(getHost())
                                                .withTaskFunction(
                                                        prepareConnectContainerToNetworkFunction(
                                                                context,
                                                                entry.getKey(),
                                                                entry.getValue()))
                                                .execute();
                    }).collect(Collectors.toList());
            DeferredResult.allOf(tasks).whenComplete((ignore, ex) -> {
                if (ex == null) {
                    processStartContainer(context);
                    return;
                }

                // Update the container state so further actions (e.g. cleanup) can be
                // performed
                context.containerState.powerState = ContainerState.PowerState.ERROR;
                context.requestFailed = true;
                processInspectContainer(context);

                Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                fail(context.request, cause);
            });
        });
    }

    private Function<RetriableTask<Void>, DeferredResult<Void>> prepareConnectContainerToNetworkFunction(
            RequestContext context, String networkId, ServiceNetwork network) {
        return (task) -> {
            DeferredResult<Void> deferredResult = new DeferredResult<>();

            String containerId = context.containerState.id;
            CommandInput connectCommandInput = new CommandInput(context.commandInput);
            addNetworkConfig(connectCommandInput, context.containerState.id, networkId, network);

            logFine("Connecting container [%s] to network [%s]", containerId, networkId);

            context.executor.connectContainerToNetwork(connectCommandInput, (o, ex) -> {
                if (ex == null) {
                    // Nothing to do, success completion will be
                    // handled on the combined tasks completion
                    deferredResult.complete(null);
                    return;
                }

                if (isRetriableFailure(o.getStatusCode())) {
                    markRequestAsRetriedAfterFailure(context.request);
                } else {
                    task.preventRetries();
                    context.containerState.status = String
                            .format("Cannot connect container to network %s", networkId);
                    logSevere(
                            "Failure connecting container [%s] of host [%s] to network [%s] (status code: %s)",
                            context.containerState.id,
                            networkId,
                            context.computeState.documentSelfLink,
                            o.getStatusCode());
                }

                deferredResult
                        .fail(DockerAdapterUtils.runtimeExceptionFromFailedDockerOperation(o, ex));
            });

            return deferredResult;
        };
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

    private void processInspectContainer(RequestContext context) {
        if (context.containerState.id == null) {
            PowerState powerState = context.containerState.powerState;
            if (!context.requestFailed && (powerState == null || powerState.isUnmanaged())) {
                logWarning(
                        "Skipping container inspect for container [%s] of host [%s]: Container power state is %s",
                        context.containerState.documentSelfLink,
                        context.computeState.documentSelfLink,
                        powerState != null ? powerState.toString() : "null");
                patchTaskStage(context.request, TaskStage.FINISHED, null);
            } else {
                String error = String.format(
                        "Cannot inspect container for request [%s]: container id is required",
                        context.request.getRequestTrackingLog());
                fail(context.request, new IllegalStateException(error));
            }
            return;
        }

        doInspectContainer(context);
    }

    private void doInspectContainer(RequestContext context) {
        ensurePropertyExists((retryCountProperty) -> {
            new RetriableTaskBuilder<Map<String, Object>>(
                    String.format("inspect-container-%s", context.containerState.id))
                            .withMaximumRetries(retryCountProperty)
                            .withRetryDelays(INSPECT_RETRY_AFTER_SECONDS)
                            .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                            .withServiceHost(getHost())
                            .withTaskFunction(prepareInspectContainerFunction(context))
                            .execute()
                            .whenComplete((props, ex) -> {
                                if (ex != null) {
                                    Throwable failureCause = ex instanceof CompletionException
                                            ? ex.getCause() : ex;
                                    fail(context.request, failureCause);
                                    return;
                                }

                                handleExceptions(context.request, context.operation, () -> {
                                    patchContainerState(context.request, context.containerState,
                                            props, context);
                                });
                            });
        });
    }

    private Function<RetriableTask<Map<String, Object>>, DeferredResult<Map<String, Object>>> prepareInspectContainerFunction(
            RequestContext context) {
        return (task) -> {
            DeferredResult<Map<String, Object>> result = new DeferredResult<>();

            CommandInput inspectCommandInput = new CommandInput(context.commandInput)
                    .withProperty(DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);
            logFine("Inspecting container [%s] for request [%s]", context.containerState.id,
                    context.request.getRequestTrackingLog());
            context.executor.inspectContainer(inspectCommandInput, (o, ex) -> {
                if (ex == null) {
                    try {
                        // Nothing to do, success completion will be
                        // handled on the retriable task completion
                        @SuppressWarnings("unchecked")
                        Map<String, Object> props = o.getBody(Map.class);
                        result.complete(props);
                    } catch (Throwable e) {
                        task.preventRetries();
                        result.fail(e);
                    }
                    return;
                }

                if (isRetriableFailure(o.getStatusCode())) {
                    markRequestAsRetriedAfterFailure(context.request);
                } else {
                    task.preventRetries();
                    logSevere(
                            "Failure inspecting container [%s] of host [%s] (status code: %s)",
                            context.containerState.documentSelfLink,
                            context.computeState.documentSelfLink,
                            o.getStatusCode());
                }

                result.fail(DockerAdapterUtils.runtimeExceptionFromFailedDockerOperation(o, ex));
            });

            return result;
        };
    }

    private void execContainer(RequestContext context) {
        String command = context.request.customProperties
                .get(ShellContainerExecutorService.COMMAND_KEY);
        if (command == null) {
            Exception e = new LocalizableValidationException(
                    "Command not provided" + context.request.getRequestTrackingLog(),
                    "adapter.exec.container.command.missing",
                    context.request.getRequestTrackingLog());
            if (context.operation != null) {
                context.operation.fail(e);
            }
            fail(context.request, e);
            return;
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

        logFine("Executing command in container: %s %s", context.containerState.documentSelfLink,
                context.request.getRequestTrackingLog());

        if (context.containerState.id == null) {
            fail(context.request, new IllegalStateException("container id is required" +
                    context.request.getRequestTrackingLog()));
            return;
        }

        context.executor.execContainer(execCommandInput, (op, ex) -> {
            if (ex != null) {
                context.operation.fail(ex);
                fail(context.request, op, ex);
            } else {
                if (op.hasBody()) {
                    context.operation.setBodyNoCloning(op.getBodyRaw());
                }
                context.operation.complete();
            }
        });
    }

    private void fetchContainerStats(RequestContext context) {
        if (context.containerState.powerState != PowerState.RUNNING) {
            Exception e = new IllegalStateException(
                    "Can't fetch stats from a stopped container without blocking: "
                            + context.containerState.documentSelfLink
                            + context.request.getRequestTrackingLog());
            if (context.operation != null) {
                context.operation.fail(e);
            }
            fail(context.request, e);
            return;
        }

        boolean allowVchStatsCollection = Boolean
                .valueOf(ConfigurationUtil.getProperty(ALLOW_VCH_STATS_COLLECTION_PROP_NAME));
        if (ContainerHostUtil.isVicHost(context.computeState) && !allowVchStatsCollection) {
            context.operation.fail(new LocalizableValidationException(
                    "Container stats are not supported by VCH hosts.",
                    "request.container.stats.not.supported"));
            return;
        }

        CommandInput statsCommandInput = new CommandInput(context.commandInput).withProperty(
                DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);

        logFine("Executing fetch container stats: %s %s",
                context.containerState.documentSelfLink, context.request.getRequestTrackingLog());

        context.executor.fetchContainerStats(statsCommandInput, (o, ex) -> {
            if (ex != null) {
                logWarning(
                        "Exception while fetching stats for container [%s] of host [%s]",
                        context.containerState.documentSelfLink,
                        context.computeState.documentSelfLink);
                context.operation.fail(ex);
                fail(context.request, o, ex);
            } else {
                handleExceptions(context.request, context.operation, () -> {
                    String stats = o.getBody(String.class);
                    processContainerStats(context, stats);
                });
            }
        });
    }

    private void processContainerStats(RequestContext context, String stats) {
        logFine("Updating container stats: %s %s", context.request.resourceReference,
                context.request.getRequestTrackingLog());

        ContainerStats containerStats = ContainerStatsEvaluator.calculateStatsValues(stats);
        String containerLink = context.request.resourceReference.getPath();
        URI uri = UriUtils.buildUri(getHost(), containerLink);
        sendRequest(Operation.createPatch(uri)
                .setBodyNoCloning(containerStats)
                .setCompletion((o, ex) -> {
                    if (context.operation != null) {
                        if (ex != null) {
                            context.operation.fail(ex);
                        } else {
                            context.operation.complete();
                        }
                    }
                    patchTaskStage(context.request, TaskStage.FINISHED, ex);
                }));
    }

    private void patchContainerState(AdapterRequest request,
            ContainerState containerState, Map<String, Object> properties, RequestContext context) {

        // start with a new ContainerState object because we don't want to overwrite with stale data
        ContainerState newContainerState = new ContainerState();
        newContainerState.documentSelfLink = containerState.documentSelfLink;
        newContainerState.documentExpirationTimeMicros = -1; // make sure the expiration is reset.
        newContainerState.adapterManagementReference = containerState.adapterManagementReference;

        // copy status related attributes to check for unhealthy containers
        newContainerState.powerState = containerState.powerState;
        newContainerState.status = containerState.status;

        // workaround for VCH (see Github issue #228)
        DockerAdapterUtils.filterHostConfigEmptyPortBindings(properties);

        // copy properties into the ContainerState's attributes
        newContainerState.attributes = properties.entrySet()
                .stream()
                .filter((e) -> !FILTER_PROPERTIES.contains(e.getKey()))
                .collect(Collectors.toMap(
                        (e) -> e.getKey(),
                        (e) -> Utils.toJson(e.getValue())));

        ContainerStateMapper.propertiesToContainerState(newContainerState, properties);

        logFine("Patching ContainerState: %s %s", containerState.documentSelfLink,
                request.getRequestTrackingLog());
        sendRequest(Operation
                .createPatch(request.getContainerStateReference())
                .setBodyNoCloning(newContainerState)
                .setCompletion((o, ex) -> {
                    if (!context.requestFailed) {
                        patchTaskStage(request, TaskStage.FINISHED, ex);
                    }
                }));
    }

    private void processDeleteContainer(RequestContext context) {
        ensurePropertyExists((retryCountProperty) -> {
            new RetriableTaskBuilder<Boolean>(
                    String.format("delete-container-%s", context.containerState.id))
                            .withMaximumRetries(retryCountProperty)
                            .withRetryDelays(DELETE_RETRY_AFTER_SECONDS)
                            .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                            .withServiceHost(getHost())
                            .withTaskFunction(prepareDeleteContainerFunction(context))
                            .execute()
                            .whenComplete((deleted, ex) -> {
                                if (ex != null) {
                                    Throwable failureCause = ex instanceof CompletionException
                                            ? ex.getCause() : ex;
                                    fail(context.request, failureCause);
                                    return;
                                }

                                handleExceptions(context.request, context.operation, () -> {
                                    if (deleted) {
                                        NetworkUtils.updateConnectedNetworks(getHost(),
                                                context.containerState, -1);
                                    }
                                    patchTaskStage(context.request, TaskStage.FINISHED, null);
                                });
                            });
        });
    }

    /**
     * the deferred result completion value indicates whether a container was or was not deleted
     */
    private Function<RetriableTask<Boolean>, DeferredResult<Boolean>> prepareDeleteContainerFunction(
            RequestContext context) {
        return (task) -> {
            DeferredResult<Boolean> result = new DeferredResult<>();

            CommandInput deleteCommandInput = new CommandInput(context.commandInput)
                    .withProperty(DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);
            logFine("Deleting container [%s]", context.containerState.id);
            context.executor.removeContainer(deleteCommandInput, (o, ex) -> {
                if (ex == null) {
                    // Nothing to do, success completion will be
                    // handled on the retriable task completion
                    result.complete(true);
                    return;
                }

                // there is no container to delete
                if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND
                        || ex.getMessage().contains(DELETE_CONTAINER_MISSING_ERROR)) {
                    logWarning(
                            "Could not delete container [%s] of host [%s]: "
                                    + "container not found. Skipping deletion.",
                            context.containerState.documentSelfLink,
                            context.computeState.documentSelfLink);
                    result.complete(false);
                    return;
                }

                if (isRetriableFailure(o.getStatusCode())) {
                    markRequestAsRetriedAfterFailure(context.request);
                } else {
                    task.preventRetries();
                    logSevere(
                            "Failure deleting container [%s] of host [%s] (status code: %s)",
                            context.containerState.documentSelfLink,
                            context.computeState.documentSelfLink,
                            o.getStatusCode());
                }

                result.fail(DockerAdapterUtils.runtimeExceptionFromFailedDockerOperation(o, ex));
            });

            return result;
        };
    }

    private boolean isRetriableFailure(int statusCode) {
        return RETRIABLE_HTTP_STATUSES.contains(statusCode);
    }

    private void processStartContainer(RequestContext context) {
        ensurePropertyExists((retryCountProperty) -> {
            new RetriableTaskBuilder<Void>(
                    String.format("start-container-%s", context.containerState.id))
                            .withMaximumRetries(retryCountProperty)
                            .withRetryDelays(START_RETRY_AFTER_SECONDS)
                            .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                            .withServiceHost(getHost())
                            .withTaskFunction(prepareStartContainerFunction(context))
                            .execute()
                            .whenComplete((ignore, ex) -> {
                                if (ex != null) {
                                    Throwable failureCause = ex instanceof CompletionException
                                            ? ex.getCause() : ex;
                                    fail(context.request, failureCause);
                                    return;
                                }

                                handleExceptions(context.request, context.operation, () -> {
                                    NetworkUtils.updateConnectedNetworks(getHost(),
                                            context.containerState, 1);
                                    processInspectContainer(context);
                                });
                            });
        });
    }

    private Function<RetriableTask<Void>, DeferredResult<Void>> prepareStartContainerFunction(
            RequestContext context) {
        return (task) -> {
            DeferredResult<Void> result = new DeferredResult<>();

            CommandInput startCommandInput = new CommandInput(context.commandInput)
                    .withProperty(DOCKER_CONTAINER_ID_PROP_NAME,
                            context.containerState.id);

            logFine("Starting container [%s]", context.containerState.id);

            context.executor.startContainer(startCommandInput, (o, ex) -> {
                if (ex == null) {
                    // Nothing to do, success completion will be
                    // handled on the retriable task completion
                    result.complete(null);
                    return;
                }

                if (isRetriableFailure(o.getStatusCode())) {
                    markRequestAsRetriedAfterFailure(context.request);
                } else {
                    task.preventRetries();
                    logSevere(
                            "Failure starting container [%s] of host [%s] (status code: %s)",
                            context.containerState.documentSelfLink,
                            context.computeState.documentSelfLink,
                            o.getStatusCode());
                }

                result.fail(DockerAdapterUtils.runtimeExceptionFromFailedDockerOperation(o, ex));
            });

            return result;
        };
    }

    private void processStopContainer(RequestContext context) {
        ensurePropertyExists((retryCountProperty) -> {
            new RetriableTaskBuilder<Void>(
                    String.format("stop-container-%s", context.containerState.id))
                            .withMaximumRetries(retryCountProperty)
                            .withRetryDelays(STOP_RETRY_AFTER_SECONDS)
                            .withRetryDelaysTimeUnit(TimeUnit.SECONDS)
                            .withServiceHost(getHost())
                            .withTaskFunction(prepareStopContainerFunction(context))
                            .execute()
                            .whenComplete((ignore, ex) -> {
                                if (ex != null) {
                                    Throwable failureCause = ex instanceof CompletionException
                                            ? ex.getCause() : ex;
                                    fail(context.request, failureCause);
                                    return;
                                }

                                handleExceptions(context.request, context.operation, () -> {
                                    NetworkUtils.updateConnectedNetworks(getHost(),
                                            context.containerState, -1);
                                    processInspectContainer(context);
                                });
                            });
        });
    }

    private Function<RetriableTask<Void>, DeferredResult<Void>> prepareStopContainerFunction(
            RequestContext context) {
        return (task) -> {
            DeferredResult<Void> result = new DeferredResult<>();

            CommandInput stopCommandInput = new CommandInput(context.commandInput)
                    .withProperty(DOCKER_CONTAINER_ID_PROP_NAME, context.containerState.id);
            logFine("Stopping container [%s]", context.containerState.id);
            context.executor.stopContainer(stopCommandInput, (o, ex) -> {
                if (ex == null) {
                    // Nothing to do, success completion will be
                    // handled on the retriable task completion
                    result.complete(null);
                    return;
                }

                if (isRetriableFailure(o.getStatusCode())) {
                    markRequestAsRetriedAfterFailure(context.request);
                } else {
                    task.preventRetries();
                    logSevere(
                            "Failure stopping container [%s] of host [%s] (status code: %s)",
                            context.containerState.documentSelfLink,
                            context.computeState.documentSelfLink,
                            o.getStatusCode());
                }

                result.fail(DockerAdapterUtils.runtimeExceptionFromFailedDockerOperation(o, ex));
            });

            return result;
        };
    }

    private void markRequestAsRetriedAfterFailure(AdapterRequest request) {
        if (request.customProperties == null) {
            request.customProperties = new HashMap<>();
        }
        request.customProperties.put(RETRIED_AFTER_FAILURE, Boolean.TRUE.toString());
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
                        /* in case of exception the default retry count will be set */
                        retriesCount = PROVISION_CONTAINER_RETRIES_COUNT_DEFAULT;
                        if (ex == null) {
                            retriesCount = Integer.valueOf(
                                    o.getBody(ConfigurationState.class).value);
                        }
                        callback.accept(retriesCount);
                    }));
        }
    }

    private void ensurePullRetriesPropertyExists(Consumer<Integer> callback) {
        if (pullRetriesCount != null) {
            callback.accept(pullRetriesCount);
        } else {
            String maxRetriesCountConfigPropPath = UriUtils.buildUriPath(
                    ConfigurationFactoryService.SELF_LINK,
                    PROVISION_CONTAINER_PULL_RETRIES_COUNT_PARAM_NAME);
            sendRequest(Operation.createGet(this, maxRetriesCountConfigPropPath)
                    .setCompletion((o, ex) -> {
                        if (ex == null) {
                            pullRetriesCount = Integer.valueOf(
                                    o.getBody(ConfigurationState.class).value);
                            callback.accept(pullRetriesCount);
                        } else {
                            /*
                             * in case of exception the default retry count will be set to the
                             * PROVISION_CONTAINER_RETRIES_COUNT_PARAM_NAME
                             */
                            ensurePropertyExists(retriesCount -> {
                                pullRetriesCount = retriesCount;
                                callback.accept(pullRetriesCount);
                            });
                        }
                    }));
        }
    }

}
