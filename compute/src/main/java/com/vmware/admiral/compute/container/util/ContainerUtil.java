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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.LogConfig;
import com.vmware.admiral.compute.container.PortBinding;
import com.vmware.admiral.compute.container.SystemContainerDescriptions;
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

    public static ContainerDescription createContainerDescription(ContainerState state) {

        ContainerDescription containerDescription = new ContainerDescription();

        containerDescription.documentSelfLink = state.descriptionLink;
        containerDescription.documentDescription = state.documentDescription;
        containerDescription.tenantLinks = state.tenantLinks;
        //there are some corner cases (mostly in tests) when the state might be missing the image
        containerDescription.image = state.image == null ? NOT_KNOWN_IMAGE : state.image;
        containerDescription.cpuShares = state.cpuShares;
        containerDescription.instanceAdapterReference = state.adapterManagementReference;
        containerDescription.env = state.env;
        containerDescription.command = state.command;
        containerDescription.name = state.names != null ? state.names.get(0)
                : DISCOVERED_CONTAINER_DESC + UUID.randomUUID().toString();
        containerDescription.customProperties = state.customProperties;
        containerDescription.parentDescriptionLink = state.parentLink;
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

        return containerDescription;
    }

    /**
     * Method checks if ContainerState is mapped to ContainerDescription which is "Discovered". The
     * only difference between this method and the one in SystemContainerDescriptions is that this
     * one will return false if check is against system container.
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

        @SuppressWarnings("unchecked")
        public void updateDiscoveredContainerDesc(ContainerState containerState,
                ContainerState newState,
                ContainerDescription containerDesc) {
            if (!isDiscoveredContainer(containerState)) {
                return;
            }

            if (newState.attributes == null || newState.attributes.isEmpty()) {
                return;
            }

            if (containerState.customProperties != null
                    && containerState.customProperties.containsKey(DISCOVERED_CONTAINER_UPDATED)) {
                return;
            }

            if (containerDesc == null) {
                getDiscoveredContainerDescription(containerState.descriptionLink,
                        (containerDescription) -> this.updateDiscoveredContainerDesc(
                                containerState,
                                newState,
                                containerDescription));
                return;
            }

            ContainerDescription patch = new ContainerDescription();

            newState.attributes
                    .forEach((k, v) -> {

                        switch (k) {

                        case CONFIG_PROPERTY:
                            patch.hostname = getJsonValue(v, HOSTNAME_PROPERTY);
                            patch.domainName = getJsonValue(v, DOMAIN_NAME_PROPERTY);
                            patch.user = getJsonValue(v, USER_PROPERTY);
                            patch.workingDir = getJsonValue(v, WORKING_DIR_PROPERTY);
                            break;

                        case HOST_CONFIG_PROPERTY:

                            Map<String, String> logConfigProperty = Utils.getJsonMapValue(v,
                                    LOG_CONFIG_PROPERTY, Map.class);
                            if (!logConfigProperty.isEmpty()) {
                                try {
                                    LogConfig logConfig = new LogConfig();
                                    logConfig.type = logConfigProperty.get(TYPE_PROPERTY);
                                    logConfig.config = Utils.getJsonMapValue(
                                            logConfigProperty.toString(), CONFIG_PROPERTY,
                                            Map.class);
                                    patch.logConfig = logConfig;
                                } catch (Exception e) {
                                    service.logWarning(
                                            "Failed to retrieve value for LogConfig of ContainerState: %s. Exception: %s",
                                            containerState.documentSelfLink,
                                            Utils.toString(e));
                                }
                            }

                            patch.publishAll = Utils.getJsonMapValue(v, PUBLISH_ALL_PORTS_PROPERTY,
                                    Boolean.class);
                            break;
                        default:
                            break;
                        }
                    });

            if (containerState.customProperties == null) {
                containerState.customProperties = new HashMap<String, String>();
            }
            containerState.customProperties.put(DISCOVERED_CONTAINER_UPDATED,
                    Boolean.TRUE.toString());

            patch.customProperties = containerState.customProperties;

            service.sendRequest(Operation
                    .createPatch(service, containerDesc.documentSelfLink)
                    .setBody(patch)
                    .setCompletion(
                            (o, e) -> {
                                if (e != null) {
                                    service.logWarning(
                                            "Failed to update ContainerDescription: %s. Error: %s",
                                            containerDesc.documentSelfLink, Utils.toString(e));
                                    return;
                                }
                                service.logInfo(
                                        "Discovered ContainerDescription: %s, was successfully updated.",
                                        containerDesc.documentSelfLink);

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

        private void getDiscoveredContainerDescription(String containerDescriptionLink,
                Consumer<ContainerDescription> callbackFunction) {
            service.sendRequest(Operation.createGet(service, containerDescriptionLink)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            service.logWarning(
                                    "Failure retrieving description state: " + Utils.toString(e));
                            return;
                        }

                        ContainerDescription desc = o.getBody(ContainerDescription.class);
                        callbackFunction.accept(desc);
                    }));
        }

        private String getJsonValue(Object json, String key) {
            String result = null;
            try {
                result = Utils.getJsonMapValue(json, key, String.class);
            } catch (Exception e) {
                service.logWarning(
                        "Failed to retrieve value for key: %s. Exception: %s",
                        key,
                        Utils.toString(e));
            }
            return result == null ? "" : result;
        }

    }
}
