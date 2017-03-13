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

import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_IMAGE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NAMES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_ID_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_CONTAINER_VOLUMES_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_DRIVER_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerAdapterCommandExecutor.DOCKER_VOLUME_SCOPE_PROP_NAME;
import static com.vmware.admiral.adapter.docker.service.DockerNetworkAdapterService.DOCKER_PREDEFINED_NETWORKS;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_ALIAS_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostService.SSL_TRUST_CERT_PROP_NAME;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ConversionUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostUtil;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.NetworkListCallback;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.VolumeListCallback;
import com.vmware.admiral.compute.container.ShellContainerExecutorService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.TaskState.TaskStage;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.AuthCredentialsService.AuthCredentialsServiceState;

/**
 * Service for fulfilling ContainerHostRequest backed by a docker server
 */
public class DockerHostAdapterService extends AbstractDockerAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_HOST;

    // column 7 is "Available memory", that value might changed in case migration to newer PhotonOS/Alpine
    private static final String COMMAND_AVAILABLE_MEMORY = "free -b | awk '/^Mem:/{print $7}'";
    private static final String COMMAND_CPU_USAGE = "awk -v a=\"$(awk '/cpu /{print $2+$4,$2+$4+$5}' /proc/stat; sleep 1)\" '/cpu /{split(a,b,\" \"); print 100*($2+$4-b[1])/($2+$4+$5-b[2])}'  /proc/stat";
    private static final String HIDDEN_CUSTOM_PROPERTY_PREFIX = "__";

    // constats to extract VCH usage data
    private static final String SYSTEM_STATUS = "SystemStatus";
    private static final String VCH_MEMORY_USAGE = " VCH memory usage";
    private static final String VCH_CPU_LIMIT = " VCH CPU limit";
    private static final String VCH_CPU_USAGE = " VCH CPU usage";

    @Override
    public void handlePatch(Operation op) {
        ContainerHostRequest request = op.getBody(ContainerHostRequest.class);
        request.validate();

        logFine("Processing host operation request %s", request.getRequestTrackingLog());

        if (ContainerHostOperationType.PING == request.getOperationType()
                && ComputeService.FACTORY_LINK.equals(request.resourceReference.getPath())) {
            ComputeState hostComputeState = new ComputeState();
            hostComputeState.customProperties = request.customProperties;
            directPing(request, op, hostComputeState);
        } else if (ContainerHostOperationType.INFO == request.getOperationType()
                && ComputeService.FACTORY_LINK.equals(request.resourceReference.getPath())) {
            ComputeState hostComputeState = new ComputeState();
            hostComputeState.customProperties = request.customProperties;
            directHostInfo(request, op, hostComputeState);
        } else if (ContainerHostOperationType.LIST_CONTAINERS == request.getOperationType()
                && request.serviceTaskCallback.isEmpty()) {
            getContainerHost(request, op, request.resourceReference,
                    (computeState, commandInput) -> directListContainers(request, op, computeState,
                            commandInput));
        } else if (ContainerHostOperationType.LIST_NETWORKS == request.getOperationType()
                && request.serviceTaskCallback.isEmpty()) {
            getContainerHost(request, op, request.resourceReference,
                    (computeState, commandInput) -> directListNetworks(request, op, computeState,
                            commandInput));
        } else if (ContainerHostOperationType.LIST_VOLUMES == request.getOperationType()
                && request.serviceTaskCallback.isEmpty()) {
            getContainerHost(request, op, request.resourceReference,
                    (computeState, commandInput) -> directListVolumes(request, op, computeState,
                            commandInput));
        } else {
            getContainerHost(request, op, request.resourceReference,
                    (computeState, commandInput) -> processOperation(request, computeState,
                            commandInput));
            op.complete();
        }
    }

    private void processOperation(ContainerHostRequest request, ComputeState computeState,
            CommandInput commandInput) {
        switch (request.getOperationType()) {
        case VERSION:
            doVersion(request, computeState, commandInput);
            break;
        case INFO:
            doInfo(request, computeState, commandInput);
            break;
        case PING:
            doPing(request, computeState, commandInput);
            break;
        case LIST_CONTAINERS:
            doListContainers(request, computeState, commandInput);
            break;
        case LIST_NETWORKS:
            doListNetworks(request, computeState, commandInput);
            break;
        case LIST_VOLUMES:
            doListVolumes(request, computeState, commandInput);
            break;
        case STATS:
            doStats(request, computeState);
            break;
        default:
        }
    }

    private void doVersion(ContainerHostRequest request, ComputeState computeState,
            CommandInput commandInput) {
        getCommandExecutor().hostVersion(commandInput,
                getHostPatchCompletionHandler(request));
    }

    private void doInfo(ContainerHostRequest request, ComputeState computeState,
            CommandInput commandInput) {
        getCommandExecutor().hostInfo(commandInput,
                getHostPatchCompletionHandler(request));
    }

    private void directHostInfo(ContainerHostRequest request, Operation op,
            ComputeState hostComputeState) {
        directHostOperationWithCredentials(op, hostComputeState, (authCredentialsState) -> {
            directHostInfo(request, op, hostComputeState, authCredentialsState);
        });
    }

    @SuppressWarnings("unchecked")
    private void directHostInfo(ContainerHostRequest request, Operation op,
            ComputeState hostComputeState, AuthCredentialsServiceState authCredentialsState) {

        CommandInput commandInput = prepareDirectHostOperationCommand(hostComputeState,
                authCredentialsState);
        updateSslTrust(request, commandInput);
        getCommandExecutor().hostInfo(
                commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        op.fail(ex);
                    } else {
                        updateHostStateCustomProperties(hostComputeState, o.getBody(Map.class));
                        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                            logFine("Compute state was updated with output of docker info. Request: %s",
                                    request.getRequestTrackingLog());
                        }
                        op.setBody(hostComputeState);
                        op.complete();
                    }
                });
    }

    private void doStats(ContainerHostRequest request, ComputeState computeState) {

        String hostLink = computeState.documentSelfLink;

        Operation post = Operation.createPost(this, ShellContainerExecutorService.SELF_LINK)
                .setContextId(request.getRequestId());
        post.setUri(UriUtils.appendQueryParam(post.getUri(),
                ShellContainerExecutorService.HOST_LINK_URI_PARAM, hostLink));

        HashMap<String, Object> command = new HashMap<>();
        command.put(ShellContainerExecutorService.COMMAND_KEY,
                ShellContainerExecutorService.buildComplexCommand(
                        COMMAND_AVAILABLE_MEMORY,
                        COMMAND_CPU_USAGE));
        post.setBody(command);

        sendRequest(post.setCompletion((o2, ex2) -> {
            if (ex2 != null) {
                // We should not fail if the command does not succeed
                logSevere(Utils.toString(ex2));
                patchTaskStage(request, TaskStage.FINISHED, null);
                return;
            }

            String commandOutput = o2.getBody(String.class);
            Map<String, Object> properties = parseStatsOutput(commandOutput, hostLink);

            Operation op = Operation.createPatch(null).setBody(properties);
            getHostPatchCompletionHandler(request).handle(op, null);
        }));
    }

    private Map<String, Object> parseStatsOutput(String commandOutput, String hostLink) {
        Map<String, Object> properties = new HashMap<>();

        if (commandOutput != null) {
            String[] results = commandOutput.split("\n");

            if (results.length == 2) {
                properties = PropertyUtils.setPropertyDouble(properties,
                        ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                        results[0].trim());

                properties = PropertyUtils.setPropertyDouble(properties,
                        ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME,
                        results[1].trim());

            } else {
                logWarning("Unexpected stats output host [%s], output [%s]",
                        hostLink, commandOutput);
            }
        }

        return properties;
    }

    private void doPing(ContainerHostRequest request, ComputeState computeState,
            CommandInput commandInput) {

        updateSslTrust(request, commandInput);

        getCommandExecutor().hostPing(commandInput, (o, ex) -> {
            if (ex != null) {
                logWarning("Failure while pinging host [%s]",
                        computeState.documentSelfLink);
                fail(request, o, ex);
            } else {
                patchTaskStage(request, TaskStage.FINISHED, null);
            }
        });
    }

    private void doListContainers(ContainerHostRequest request, ComputeState computeState,
            CommandInput commandInput) {

        updateSslTrust(request, commandInput);

        getCommandExecutor().listContainers(
                commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        logWarning("Failure while listing containers of host [%s]",
                                computeState.documentSelfLink);
                        fail(request, o, ex);
                    } else {
                        ContainerListCallback callbackResponse = createContainerListCallback(
                                computeState, o);

                        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                            logFine("Collection returned container IDs: %s %s",
                                    callbackResponse.containerIdsAndNames.keySet().stream()
                                            .collect(Collectors.toList()),
                                    request.getRequestTrackingLog());
                        }

                        patchTaskStage(request, TaskStage.FINISHED, null, callbackResponse);
                    }
                });
    }

    // get containers within the current operation without using callback
    private void directListContainers(ContainerHostRequest request, Operation op,
            ComputeState computeState, CommandInput commandInput) {
        updateSslTrust(request, commandInput);

        getCommandExecutor().listContainers(
                commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        op.fail(ex);
                    } else {
                        ContainerListCallback callbackResponse = createContainerListCallback(
                                computeState, o);
                        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                            logFine("Collection returned container IDs: %s %s",
                                    callbackResponse.containerIdsAndNames.keySet().stream()
                                            .collect(Collectors.toList()),
                                    request.getRequestTrackingLog());
                        }
                        op.setBody(callbackResponse);
                        op.complete();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private ContainerListCallback createContainerListCallback(ComputeState computeState,
            Operation o) {
        List<Map<String, Object>> containerList = o.getBody(List.class);

        ContainerListCallback callbackResponse = new ContainerListCallback();
        callbackResponse.containerHostLink = computeState.documentSelfLink;

        for (Map<String, Object> containerData : containerList) {
            String id = (String) containerData.get(DOCKER_CONTAINER_ID_PROP_NAME);
            String[] names;
            Object namesProperty = containerData
                    .get(DOCKER_CONTAINER_NAMES_PROP_NAME);
            if (namesProperty instanceof List) {
                names = ((List<String>) namesProperty)
                        .toArray(new String[0]);
            } else {
                names = (String[]) namesProperty;
            }
            callbackResponse.addIdAndNames(id, names);
            callbackResponse.containerIdsAndImage.put(id,
                    (String) containerData.get(DOCKER_CONTAINER_IMAGE_PROP_NAME));
        }
        return callbackResponse;
    }

    private void doListNetworks(ContainerHostRequest request, ComputeState computeState,
            CommandInput commandInput) {

        updateSslTrust(request, commandInput);

        getCommandExecutor().listNetworks(
                commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        fail(request, o, ex);
                    } else {
                        NetworkListCallback callbackResponse = createNetworkListCallback(
                                computeState, o);

                        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                            logFine("Collection returned network IDs: %s %s",
                                    callbackResponse.networkIdsAndNames.keySet().stream()
                                            .collect(Collectors.toList()),
                                    request.getRequestTrackingLog());
                        }

                        patchTaskStage(request, TaskStage.FINISHED, null, callbackResponse);
                    }
                });
    }

    // get containers within the current operation without using callback
    private void directListNetworks(ContainerHostRequest request, Operation op,
            ComputeState computeState, CommandInput commandInput) {
        updateSslTrust(request, commandInput);

        getCommandExecutor().listNetworks(
                commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        op.fail(ex);
                    } else {
                        NetworkListCallback callbackResponse = createNetworkListCallback(
                                computeState, o);
                        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                            logFine("Collection returned network IDs: %s %s",
                                    callbackResponse.networkIdsAndNames.keySet().stream()
                                            .collect(Collectors.toList()),
                                    request.getRequestTrackingLog());
                        }
                        op.setBody(callbackResponse);
                        op.complete();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private NetworkListCallback createNetworkListCallback(ComputeState computeState,
            Operation o) {
        List<Map<String, Object>> networkList = o.getBody(List.class);

        NetworkListCallback callbackResponse = new NetworkListCallback();
        callbackResponse.containerHostLink = computeState.documentSelfLink;

        for (Map<String, Object> networkData : networkList) {
            String id = (String) networkData.get(DOCKER_CONTAINER_NETWORK_ID_PROP_NAME);
            String name = (String) networkData.get(DOCKER_CONTAINER_NETWORK_NAME_PROP_NAME);

            if (!DOCKER_PREDEFINED_NETWORKS.contains(name)) {
                callbackResponse.addIdAndNames(id, name);
            }
        }
        return callbackResponse;
    }

    private void doListVolumes(ContainerHostRequest request, ComputeState computeState,
            CommandInput commandInput) {

        updateSslTrust(request, commandInput);

        getCommandExecutor().listVolumes(
                commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        logWarning("Failure while listing volumes of host [%s]",
                                computeState.documentSelfLink);
                        fail(request, o, ex);
                    } else {
                        VolumeListCallback callbackResponse = createVolumeListCallback(
                                computeState, o);

                        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                            logFine("Collection returned volume names: %s %s",
                                    callbackResponse.volumesByName.keySet().toString(),
                                    request.getRequestTrackingLog());
                        }

                        patchTaskStage(request, TaskStage.FINISHED, null, callbackResponse);
                    }
                });
    }

    // get containers within the current operation without using callback
    private void directListVolumes(ContainerHostRequest request, Operation op,
            ComputeState computeState, CommandInput commandInput) {
        updateSslTrust(request, commandInput);

        getCommandExecutor().listVolumes(
                commandInput,
                (o, ex) -> {
                    if (ex != null) {
                        op.fail(ex);
                    } else {
                        VolumeListCallback callbackResponse = createVolumeListCallback(
                                computeState, o);
                        if (Logger.getLogger(this.getClass().getName()).isLoggable(Level.FINE)) {
                            logFine("Collection returned volume names: %s %s",
                                    callbackResponse.volumesByName.keySet().toString(),
                                    request.getRequestTrackingLog());
                        }
                        op.setBody(callbackResponse);
                        op.complete();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private VolumeListCallback createVolumeListCallback(ComputeState computeState,
            Operation o) {
        Map<String, List<Object>> volumesResponse = o.getBody(Map.class);

        VolumeListCallback callbackResponse = new VolumeListCallback();
        callbackResponse.containerHostLink = computeState.documentSelfLink;

        List<Object> volumesList = volumesResponse.get(DOCKER_CONTAINER_VOLUMES_PROP_NAME);
        if (volumesList != null) {
            for (Object volumeData : volumesList) {
                Map<String, String> dataMap = (Map<String, String>) volumeData;

                ContainerVolumeState volume = new ContainerVolumeState();
                volume.name = dataMap.get(DOCKER_VOLUME_NAME_PROP_NAME);
                volume.driver = dataMap.get(DOCKER_VOLUME_DRIVER_PROP_NAME);
                volume.scope = dataMap.get(DOCKER_VOLUME_SCOPE_PROP_NAME);

                callbackResponse.add(volume);
            }
        }

        return callbackResponse;
    }

    private void updateHostStateCustomProperties(ComputeState computeState,
            Map<String, Object> properties) {
        if (computeState != null && properties != null && !properties.isEmpty()) {
            computeState.customProperties = new HashMap<>();

            properties.entrySet().stream()
                    .forEach(entry -> {
                        if (!entry.getKey().startsWith(HIDDEN_CUSTOM_PROPERTY_PREFIX)) {
                            computeState.customProperties.put(
                                    HIDDEN_CUSTOM_PROPERTY_PREFIX + entry.getKey(),
                                    Utils.toJson(entry.getValue()));
                        } else {
                            computeState.customProperties.put(entry.getKey(),
                                    Utils.toJson(entry.getValue()));
                        }
                    });

            computeState.customProperties.remove(
                    ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME);

            if (ContainerHostUtil.isVicHost(computeState)) {
                parseVicStats(computeState, properties);
            }
        }
    }

    private void patchHostState(ContainerHostRequest request, Map<String, Object> properties,
            CompletionHandler callback) {
        ComputeState computeState = new ComputeState();
        updateHostStateCustomProperties(computeState, properties);
        sendRequest(Operation
                .createPatch(request.resourceReference)
                .setBody(computeState)
                .setCompletion(callback));
    }

    private CompletionHandler getHostPatchCompletionHandler(ContainerHostRequest request) {
        return (o, ex) -> {
            if (ex != null) {
                fail(request, o, ex);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, Object> properties = o.getBody(Map.class);
                patchHostState(request, properties,
                        (o1, ex1) -> patchTaskStage(request, TaskStage.FINISHED, ex1));
            }
        };
    }

    private void directPing(ContainerHostRequest request, Operation op,
            ComputeState hostComputeState) {
        directHostOperationWithCredentials(op, hostComputeState, (authCredentialsState) -> {
            directPing(request, op, hostComputeState, authCredentialsState);
        });
    }

    private void directPing(ContainerHostRequest request, Operation op,
            ComputeState hostComputeState, AuthCredentialsServiceState authCredentialsState) {
        CommandInput commandInput = prepareDirectHostOperationCommand(hostComputeState,
                authCredentialsState);

        updateSslTrust(request, commandInput);
        getCommandExecutor()
                .hostPing(
                        commandInput,
                        (currentOpr, currentEx) -> {
                            if (currentEx != null) {
                                op.fail(currentEx);
                            } else {
                                op.complete();
                            }
                        });
    }

    private void directHostOperationWithCredentials(Operation op,
            ComputeState hostComputeState, Consumer<AuthCredentialsServiceState> operation) {
        try {
            String credentialsLink = getAuthCredentialLink(hostComputeState);
            if (credentialsLink == null) {
                operation.accept(null);
            } else {
                sendRequest(Operation
                        .createGet(this, credentialsLink)
                        .setCompletion(
                                (o, ex) -> {
                                    if (ex != null) {
                                        op.fail(ex);
                                    } else {
                                        try {
                                            AuthCredentialsServiceState authCredentialsState = o
                                                    .getBody(AuthCredentialsServiceState.class);
                                            operation.accept(authCredentialsState);
                                        } catch (Throwable eInner) {
                                            op.fail(eInner);
                                        }
                                    }
                                }));
            }
        } catch (Throwable e) {
            op.fail(e);
        }
    }

    private CommandInput prepareDirectHostOperationCommand(ComputeState hostComputeState,
            AuthCredentialsServiceState authCredentialsState) {
        URI dockerUri = ContainerDescription.getDockerHostUri(hostComputeState);
        CommandInput commandInput = new CommandInput().withDockerUri(dockerUri);

        if (authCredentialsState != null) {
            checkAuthCredentialsSupportedType(authCredentialsState, true);
            commandInput
                    .withCredentials(authCredentialsState)
                    .withProperty(SSL_TRUST_ALIAS_PROP_NAME,
                            ContainerHostUtil.getTrustAlias(hostComputeState));
        }
        return commandInput;
    }

    private void updateSslTrust(ContainerHostRequest request, CommandInput commandInput) {
        if (request.customProperties == null) {
            request.customProperties = new HashMap<>();
        }
        commandInput.withProperty(SSL_TRUST_CERT_PROP_NAME,
                request.customProperties.get(SSL_TRUST_CERT_PROP_NAME));

        commandInput.withProperty(SSL_TRUST_ALIAS_PROP_NAME,
                request.customProperties.get(SSL_TRUST_ALIAS_PROP_NAME));
    }

    private void parseVicStats(ComputeState computeState, Map<String, Object> properties) {
        Long totalMemory = PropertyUtils.getPropertyLong(computeState.customProperties,
                ContainerHostService.DOCKER_HOST_TOTAL_MEMORY_PROP_NAME).orElse(0L);
        Double usedMemory = Double.valueOf(0);
        Long totalCpu = Long.valueOf(0);
        Long usedCpu = Long.valueOf(0);
        try {
            @SuppressWarnings("unchecked")
            List<List<String>> systemStatus = (List<List<String>>) properties.get(SYSTEM_STATUS);
            // parse SystemStatus in the best possible way, but do not fail if result is not in
            // expected format
            for (List<String> status : systemStatus) {
                if (status == null || status.size() < 2) {
                    continue;
                }
                if (VCH_MEMORY_USAGE.equals(status.get(0))) {
                    usedMemory = getMemoryStatus(status);
                } else if (VCH_CPU_LIMIT.equals(status.get(0))) {
                    totalCpu = getCpuStatus(status);
                } else if (VCH_CPU_USAGE.equals(status.get(0))) {
                    usedCpu = getCpuStatus(status);
                }
            }
            String cpuUsagePct = "0";
            if (totalCpu != 0) {
                cpuUsagePct = Double.toString(100.0 * usedCpu / totalCpu);
            }
            computeState.customProperties.put(
                    ContainerHostService.DOCKER_HOST_AVAILABLE_MEMORY_PROP_NAME,
                    Double.toString(totalMemory - usedMemory));
            if (totalCpu != 0) {
                computeState.customProperties.put(
                        ContainerHostService.DOCKER_HOST_CPU_USAGE_PCT_PROP_NAME,
                        cpuUsagePct);
            }
        } catch (Exception e) {
            logWarning("Unable to parse SystemStatus contents: %s", e.getMessage());
        }
    }

    private Double getMemoryStatus(List<String> status) {
        if (status.get(0) == null || status.get(1) == null) {
            logWarning("Unable to parse memory status for VIC host");
            return Double.valueOf(0);
        }
        String[] sp = status.get(1).split(" ");
        if (sp.length < 2) {
            logWarning("Unable to parse memory status for VIC host");
            return Double.valueOf(0);
        }
        return ConversionUtil.memoryToBytes(Double.parseDouble(sp[0]), sp[1]);
    }

    private Long getCpuStatus(List<String> status) {
        if (status.get(0) == null || status.get(1) == null) {
            logWarning("Unable to parse CPU status for VIC host");
            return Long.valueOf(0);
        }
        String[] sp = status.get(1).split(" ");
        if (sp.length < 2) {
            logWarning("Unable to parse CPU status for VIC host");
            return Long.valueOf(0);
        }
        return ConversionUtil.cpuToHertz(Long.parseLong(sp[0]), sp[1]);
    }
}