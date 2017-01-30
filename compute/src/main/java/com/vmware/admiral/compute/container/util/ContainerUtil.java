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

package com.vmware.admiral.compute.container.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import io.netty.util.internal.StringUtil;

import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.HostPortProfileService;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerUtil {

    private static final String DISCOVERED_CONTAINER_DESC = "DISCOVERED_CONTAINER_DESCRIPTION-";
    private static final String DISCOVERED_CONTAINER_UPDATED = "DISCOVERED_CONTAINER_UPDATED";
    private static final String CONFIG_PROPERTY = "Config";
    private static final String HOST_CONFIG_PROPERTY = "HostConfig";
    private static final String NOT_KNOWN_IMAGE = "__not_known_image";

    @SuppressWarnings("unchecked")
    public static ContainerDescription createContainerDescription(ContainerState state) {

        ContainerDescription containerDescription = new ContainerDescription();

        containerDescription.documentSelfLink = state.descriptionLink;
        containerDescription.documentDescription = state.documentDescription;
        containerDescription.tenantLinks = state.tenantLinks;
        // there are some corner cases (mostly in tests) when the state might be missing the image
        containerDescription.image = state.image == null ? NOT_KNOWN_IMAGE : state.image;
        containerDescription.cpuShares = state.cpuShares;
        containerDescription.instanceAdapterReference = state.adapterManagementReference;
        containerDescription.env = state.env;
        containerDescription.command = state.command;
        containerDescription.name = state.names != null ? state.names.get(0)
                : DISCOVERED_CONTAINER_DESC + UUID.randomUUID().toString();
        containerDescription.customProperties = state.customProperties;
        if (state.extraHosts != null) {
            containerDescription.extraHosts = state.extraHosts;
        }
        List<PortBinding> portBindings = state.ports;
        if (portBindings != null) {
            PortBinding[] ports = !portBindings.isEmpty()
                    ? new PortBinding[portBindings.size()] : new PortBinding[0];
            if (!portBindings.isEmpty()) {
                for (int i = 0; i < portBindings.size(); i++) {
                    ports[i] = portBindings.get(i);
                }
            }
            containerDescription.portBindings = ports;
        }

        if (state.attributes != null) {
            state.attributes.forEach((k, v) -> {

                switch (k) {

                case CONFIG_PROPERTY:
                    containerDescription.hostname = getJsonValue(v,
                            ContainerDescriptionHelper.HOSTNAME_PROPERTY);
                    containerDescription.domainName = getJsonValue(v,
                            ContainerDescriptionHelper.DOMAIN_NAME_PROPERTY);
                    containerDescription.user = getJsonValue(v,
                            ContainerDescriptionHelper.USER_PROPERTY);
                    containerDescription.workingDir = getJsonValue(v,
                            ContainerDescriptionHelper.WORKING_DIR_PROPERTY);
                    break;

                case HOST_CONFIG_PROPERTY:

                    Map<String, String> logConfigProperty = Utils.getJsonMapValue(v,
                            ContainerDescriptionHelper.LOG_CONFIG_PROPERTY, Map.class);
                    if (!logConfigProperty.isEmpty()) {
                        try {
                            LogConfig logConfig = new LogConfig();
                            logConfig.type = logConfigProperty
                                    .get(ContainerDescriptionHelper.TYPE_PROPERTY);
                            logConfig.config = Utils.getJsonMapValue(
                                    logConfigProperty.toString(), CONFIG_PROPERTY,
                                    Map.class);
                            containerDescription.logConfig = logConfig;
                        } catch (Exception e) {
                            Utils.log(ContainerUtil.class, ContainerUtil.class.getSimpleName(),
                                    Level.WARNING,
                                    "Failed to retrieve value for LogConfig of ContainerState: %s. Exception: %s",
                                    state.documentSelfLink,
                                    Utils.toString(e));
                        }
                    }

                    containerDescription.publishAll = Utils.getJsonMapValue(v,
                            ContainerDescriptionHelper.PUBLISH_ALL_PORTS_PROPERTY,
                            Boolean.class);
                    break;
                default:
                    break;
                }
            });
        }

        return containerDescription;
    }

    private static String getJsonValue(Object json, String key) {
        String result = null;
        try {
            result = Utils.getJsonMapValue(json, key, String.class);
        } catch (Exception e) {
            Utils.log(ContainerUtil.class, ContainerUtil.class.getSimpleName(),
                    Level.WARNING,
                    "Failed to retrieve value for key: %s. Exception: %s",
                    key,
                    Utils.toString(e));
        }
        return result == null ? "" : result;
    }

    /**
     * Method checks if ContainerState is mapped to ContainerDescription which is "Discovered". The
     * only difference between this method and the one in SystemContainerDescriptions is that this
     * one will return false if check is against system container.
     *
     * @param containerState
     *            - ContainerState which will be checked.
     * @return
     */
    public static boolean isDiscoveredContainer(ContainerState containerState) {

        return containerState.descriptionLink != null
                && containerState.descriptionLink.contains(UriUtils
                .buildUriPath(
                        SystemContainerDescriptions.DISCOVERED_DESCRIPTION_LINK));

    }

    public static URI getShellUri(ComputeState host, ContainerState shellContainer) {

        PortBinding portBinding = getShellPortBinding(shellContainer);

        if (portBinding == null) {
            throw new LocalizableValidationException("Could not locate shell port", "compute.shell.port");
        }

        String uriHost = UriUtilsExtended.extractHost(host.address);

        return UriUtils.buildUri(UriUtils.HTTPS_SCHEME, uriHost,
                Integer.parseInt(portBinding.hostPort), null, null);
    }

    private static PortBinding getShellPortBinding(ContainerState containerState) {
        if (containerState.ports != null) {
            for (PortBinding portBinding : containerState.ports) {
                if (SystemContainerDescriptions.CORE_AGENT_SHELL_PORT
                        .equals(portBinding.containerPort)) {
                    return portBinding;
                }
            }
        }
        return null;
    }

    /**
     * The class provides ability for updating the ContainerDescription based on ContainerState.
     */
    public static class ContainerDescriptionHelper {

        private static final String HOSTNAME_PROPERTY = "Hostname";
        private static final String DOMAIN_NAME_PROPERTY = "Domainname";
        private static final String USER_PROPERTY = "User";
        private static final String WORKING_DIR_PROPERTY = "WorkingDir";
        private static final String LOG_CONFIG_PROPERTY = "LogConfig";
        private static final String TYPE_PROPERTY = "Type";
        private static final String PUBLISH_ALL_PORTS_PROPERTY = "PublishAllPorts";

        private final StatefulService service;

        public static ContainerDescriptionHelper createInstance(StatefulService service) {
            return new ContainerDescriptionHelper(service);
        }

        private ContainerDescriptionHelper(StatefulService service) {
            this.service = service;
        }

        public void updateDiscoveredContainerDesc(ContainerState containerState,
                ContainerState newState) {
            if (!isDiscoveredContainer(containerState)) {
                return;
            }

            if (containerState.customProperties != null
                    && containerState.customProperties.containsKey(DISCOVERED_CONTAINER_UPDATED)) {
                return;
            }

            ContainerDescription patch = createContainerDescription(containerState);

            if (containerState.customProperties == null) {
                containerState.customProperties = new HashMap<String, String>();
            }
            containerState.customProperties.put(DISCOVERED_CONTAINER_UPDATED,
                    Boolean.TRUE.toString());

            service.sendRequest(Operation
                    .createPatch(service, patch.documentSelfLink)
                    .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                    .setBody(patch)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    service.logWarning(
                                            "Failed to update ContainerDescription: %s. Error: %s",
                                            patch.documentSelfLink, Utils.toString(e));
                                    return;
                                }
                                service.logInfo(
                                        "Discovered ContainerDescription: %s, was successfully updated.",
                                        patch.documentSelfLink);

                                ContainerState patchState = new ContainerState();
                                patchState.customProperties = containerState.customProperties;
                                service.sendRequest(Operation
                                        .createPatch(service, containerState.documentSelfLink)
                                        .setBody(patchState)
                                        .setCompletion(
                                                (oo, ee) -> {
                                                    if (ee != null) {
                                                        service.logWarning(
                                                                "Failed to update ContainerState: %s. Error: %s",
                                                                containerState.documentSelfLink,
                                                                Utils.toString(ee));
                                                        containerState.customProperties
                                                                .remove(DISCOVERED_CONTAINER_UPDATED);
                                                        return;
                                                    }
                                                    service.logInfo(
                                                            "Discovered ContainerState: %s, was successfully updated.",
                                                            containerState.documentSelfLink);
                                                }));
                            }));
        }

        public void updateContainerPorts(ContainerState oldContainerState,
                ContainerState newContainerState) {
            oldContainerState.ports = oldContainerState.ports == null ?
                    new ArrayList<>() : oldContainerState.ports;
            newContainerState.ports = newContainerState.powerState == ContainerState.PowerState.RETIRED
                    && newContainerState.ports == null ?
                    new ArrayList<>() : newContainerState.ports;
            // ports are not collected or no changes to unexposed ports
            if (newContainerState.ports == null ||
                    newContainerState.ports.isEmpty() && oldContainerState.ports.isEmpty()) {
                service.logFine("Skipping updating ports for container [%s].",
                        oldContainerState.documentSelfLink);
                return;
            }

            // get port bindings host_ports
            Set<Long> newContainerHostPorts = newContainerState.ports
                    .stream()
                    .filter(p -> !StringUtil.isNullOrEmpty(p.hostPort)
                            && Integer.parseInt(p.hostPort) > 0)
                    .map(k -> (long) Integer.parseInt(k.hostPort))
                    .collect(Collectors.toSet());

            String hostPortProfileLink = HostPortProfileService
                    .getHostPortProfileLink(oldContainerState.parentLink);
            service.sendRequest(Operation.createGet(service, hostPortProfileLink)
                    .setCompletion((o, e) -> {
                        if (o.getStatusCode() == Operation.STATUS_CODE_NOT_FOUND ||
                                e instanceof CancellationException) {
                            service.logWarning("Cannot find host port profile [%s]",
                                    hostPortProfileLink);
                            return;
                        }
                        if (e != null) {
                            service.logWarning("Failed retrieving HostPortProfileState: "
                                    + hostPortProfileLink, Utils.toString(e));
                            return;
                        }

                        HostPortProfileService.HostPortProfileState profile =
                                o.getBody(HostPortProfileService.HostPortProfileState.class);

                        Set<Long> oldContainerHostPorts = HostPortProfileService.getAllocatedPorts(
                                profile, oldContainerState.documentSelfLink);

                        if (Arrays.equals(oldContainerHostPorts.toArray(),
                                newContainerHostPorts.toArray())) {
                            return;
                        }

                        HostPortProfileService.HostPortProfileReservationRequest request =
                                new HostPortProfileService.HostPortProfileReservationRequest();
                        request.mode = HostPortProfileService.HostPortProfileReservationRequestMode.UPDATE_ALLOCATION;
                        request.specificHostPorts = newContainerHostPorts;
                        request.containerLink = oldContainerState.documentSelfLink;

                        service.sendRequest(
                                Operation.createPatch(service, profile.documentSelfLink)
                                        .setBody(request)
                                        .setCompletion((op, ex) -> {
                                            if (ex != null) {
                                                service.logWarning(
                                                        "Failed updating port allocation for profile [%s] and container [%s]. Error: [%s]",
                                                        hostPortProfileLink,
                                                        oldContainerState.documentSelfLink,
                                                        Utils.toString(ex));
                                                return;
                                            }
                                        }));
                    }));
        }
    }
}
