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

package com.vmware.admiral.compute.container;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.apache.commons.lang3.StringUtils;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState.PowerState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

public class ShellContainerExecutorService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.EXEC;

    private static final int RETRY_COUNT = Integer.parseInt(System.getProperty(
            "dcp.management.container.shell.availability.retry", "20"));
    public static final String COMMAND_KEY = "command";
    public static final String HOST_LINK_URI_PARAM = "hostLink";
    public static final String CONTAINER_LINK_URI_PARAM = "containerLink";

    public static final String COMMAND_ARGUMENTS_SEPARATOR = " , ";

    public static class ShellContainerExecutorState {
        public String[] command;
        public Boolean attachStdErr;
        public Boolean attachStdOut;
    }

    @Override
    public void handlePost(Operation post) {
        Map<String, String> params = UriUtils.parseUriQueryParams(post.getUri());

        String hostLink = params.get(HOST_LINK_URI_PARAM);
        String containerLink = params.get(CONTAINER_LINK_URI_PARAM);

        if (containerLink == null) {
            if (hostLink == null) {
                post.fail(new LocalizableValidationException(String.format("%s or %s is required",
                        HOST_LINK_URI_PARAM, CONTAINER_LINK_URI_PARAM),
                        "compute.shell.container.links.required", HOST_LINK_URI_PARAM, CONTAINER_LINK_URI_PARAM));
                return;
            }

            // default to shell agent for common ssh tasks
            String hostId = Service.getId(hostLink);
            containerLink = SystemContainerDescriptions
                    .getSystemContainerSelfLink(
                            SystemContainerDescriptions.AGENT_CONTAINER_NAME, hostId);
        }

        execute(post, hostLink, containerLink);
    }

    private void execute(Operation post, String hostLink, String containerLink) {
        ShellContainerExecutorState body = post.getBody(ShellContainerExecutorState.class);

        BiConsumer<ContainerState, Exception> callback = (container, e) -> {
            if (e != null) {
                post.fail(e);
            } else {
                executeCommand(container, body, post);
            }
        };

        if (hostLink != null) {
            // execute command in system agent, check if supported
            OperationUtil.getDocumentState(this, hostLink, ComputeState.class,
                    (ComputeState host) -> {
                        if (isAgentSupported(host)) {
                            getContainerWhenAvailable(containerLink, RETRY_COUNT, callback);
                        } else {
                            logInfo("Agent not supported for host %s", hostLink);
                            post.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
                            post.complete();
                        }
                    },
                    post::fail
            );
        } else {
            // execute command for specific container
            getContainerWhenAvailable(containerLink, RETRY_COUNT, callback);
        }
    }

    private static boolean isAgentSupported(ComputeState host) {
        return !DeploymentProfileConfig.getInstance().isTest() &&
                !ContainerHostUtil.isVicHost(host);
    }

    private void getContainerWhenAvailable(String containerLink, int retryCount,
            BiConsumer<ContainerState, Exception> callback) {
        sendRequest(Operation
                .createGet(this, containerLink)
                .setCompletion(
                        (o, e) -> {
                            if (e == null && o.hasBody()) {
                                ContainerState containerState = o.getBody(ContainerState.class);
                                if (containerState.powerState == PowerState.RUNNING) {
                                    logInfo("Container %s for shell execution is running",
                                            containerState.documentSelfLink);
                                    callback.accept(containerState, null);
                                    return;
                                }
                            }

                            if (retryCount > 0) {
                                int retriesRemaining = retryCount - 1;
                                int delaySeconds = RETRY_COUNT - retriesRemaining;

                                logInfo("Retrying to retrieve running container %s for shell execution. Retries left %d",
                                        containerLink, retriesRemaining);
                                getHost().schedule(
                                        () -> {
                                            getContainerWhenAvailable(containerLink,
                                                    retriesRemaining, callback);
                                        }, delaySeconds, TimeUnit.SECONDS);
                            } else {
                                callback.accept(null, new RuntimeException(
                                        "Shell container not available"));
                            }
                        }));
    }

    private void executeCommand(ContainerState container, ShellContainerExecutorState execState,
            Operation op) {

        AdapterRequest adapterRequest = new AdapterRequest();
        // task callback not needed in case of exec, as it is direct, but needed for validation.
        adapterRequest.serviceTaskCallback = ServiceTaskCallback.create(UriUtils.buildUri(
                getHost(), SELF_LINK).toString());
        adapterRequest.resourceReference = UriUtils.buildUri(getHost(), container.documentSelfLink);
        adapterRequest.operationTypeId = ContainerOperationType.EXEC.id;
        adapterRequest.customProperties = new HashMap<>();

        String command = String.join(COMMAND_ARGUMENTS_SEPARATOR, execState.command);
        adapterRequest.customProperties.put(COMMAND_KEY, command);

        if (execState.attachStdErr != null) {
            adapterRequest.customProperties.put("AttachStderr", execState.attachStdErr.toString());
        }

        if (execState.attachStdOut != null) {
            adapterRequest.customProperties.put("AttachStdout", execState.attachStdOut.toString());
        }

        String host = container.adapterManagementReference.getHost();
        String targetPath = null;
        if (StringUtils.isBlank(host)) {
            targetPath = container.adapterManagementReference.toString();
        } else {
            targetPath = container.adapterManagementReference.getPath();
        }

        sendRequest(Operation.createPatch(getHost(), targetPath)
                .setBody(adapterRequest)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        op.fail(e);
                        return;
                    }
                    op.setBody(o.getBody(String.class));
                    op.setContentType(Operation.MEDIA_TYPE_TEXT_PLAIN);
                    op.complete();
                }));
    }

    public static String[] buildComplexCommand(String... commands) {
        String[] commandString = new String[3];
        commandString[0] = "sh";
        commandString[1] = "-c";
        commandString[2] = String.join(" && ", commands);

        return commandString;
    }

}
