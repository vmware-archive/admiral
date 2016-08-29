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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.vmware.admiral.common.DeploymentProfileConfig;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ExposedServiceDescriptionService.ExposedServiceDescriptionState;
import com.vmware.admiral.compute.container.ShellContainerExecutorService.ShellContainerExecutorState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * This service is responsible for persisting and managing network host configuration.
 * The configuration is stored as a collection of strings in format src-container-name:dest-service-port:dest-host-ip:dest-host-port.
 * When this configuration is updated, the service takes care of notifying the network agent on the given host.
 * This service always shows the latest network configuration and keeps the network agent configuration in sync.
 *
 * @see {@value ContainerDescriptionFactoryService#NETWORK_AGENT_CONTAINER_DESCRIPTION_LINK}
 */
public class ContainerHostNetworkConfigService extends StatefulService {

    public static class ContainerHostNetworkConfigState extends ServiceDocument {
        /**
         * (Required) A map of {@link com.vmware.admiral.compute.container.ContainerService.ContainerState#documentSelfLink}
         *  to it's internal container to container and public network configuration
         */
        public Map<String, ContainerNetworkConfigState> containerNetworkConfigs;
        public boolean remove;

        @Override
        public String toString() {
            return "ContainerHostNetworkConfigState [containerNetworkConfigs="
                    + containerNetworkConfigs + ", remove=" + remove + "]";
        }

    }

    public static class ContainerNetworkConfigState {
        /**
         * Internal container to service configuration in the form of
         * src-container-name:dest-service-port:dest-host-ip:dest-host-port.
         */
        public Set<String> internalServiceNetworkLinks;
        /**
         * External public to service configuration by hostname in the form
         * of hostname:dest-host-ip:dest-host-port
         */
        public Set<String> publicServiceNetworkLinks;

        @Override
        public String toString() {
            return "ContainerNetworkConfigState [internalServiceNetworkLinks="
                    + internalServiceNetworkLinks + ", publicServiceNetworkLinks="
                    + publicServiceNetworkLinks + "]";
        }

    }

    public static final String RECONFIGURE_APP_NAME = "/agent/proxyreconfigure";

    public ContainerHostNetworkConfigService() {
        super(ContainerHostNetworkConfigState.class);
        super.toggleOption(ServiceOption.PERSISTENCE, true);
        super.toggleOption(ServiceOption.REPLICATION, true);
        super.toggleOption(ServiceOption.OWNER_SELECTION, true);
        super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleCreate(Operation post) {
        if (!checkForBody(post)) {
            post.complete();
            return;
        }

        ContainerHostNetworkConfigState postBody = post
                .getBody(ContainerHostNetworkConfigState.class);

        if (postBody.remove) {
            post.fail(new IllegalArgumentException("remove parameter cannot be set on POST"));
        } else if (postBody.containerNetworkConfigs == null
                || postBody.containerNetworkConfigs.isEmpty()) {
            post.complete();
        } else {
            reconfigureHostAgent(postBody, post);
        }
    }

    @Override
    public void handlePatch(Operation patch) {
        if (!checkForBody(patch)) {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            patch.complete();
            return;
        }

        ContainerHostNetworkConfigState currentState = getState(patch);
        ContainerHostNetworkConfigState patchBody = patch
                .getBody(ContainerHostNetworkConfigState.class);

        boolean changed = false;

        if (currentState.containerNetworkConfigs == null) {
            currentState.containerNetworkConfigs = new HashMap<>();
        }
        if (patchBody.containerNetworkConfigs == null) {
            patchBody.containerNetworkConfigs = new HashMap<String, ContainerHostNetworkConfigService.ContainerNetworkConfigState>();
        }

        for (Entry<String, ContainerNetworkConfigState> entry : patchBody.containerNetworkConfigs
                .entrySet()) {
            ContainerNetworkConfigState currentContainerConfigState = currentState.containerNetworkConfigs
                    .get(entry.getKey());
            if (patchBody.remove
                    && (currentContainerConfigState == null || entry.getValue() == null)) {
                changed |= (currentState.containerNetworkConfigs.remove(entry.getKey()) != null);
            } else {
                if (currentContainerConfigState == null) {
                    currentContainerConfigState = new ContainerNetworkConfigState();
                    currentState.containerNetworkConfigs.put(entry.getKey(),
                            currentContainerConfigState);
                }

                if (currentContainerConfigState.internalServiceNetworkLinks == null) {
                    currentContainerConfigState.internalServiceNetworkLinks = new HashSet<>();
                }

                if (currentContainerConfigState.publicServiceNetworkLinks == null) {
                    currentContainerConfigState.publicServiceNetworkLinks = new HashSet<>();
                }

                changed |= mergeContainerNetworkConfig(currentContainerConfigState,
                        entry.getValue(), patchBody.remove);
            }
        }

        if (changed) {
            reconfigureHostAgent(currentState, patch);
        } else {
            patch.setStatusCode(Operation.STATUS_CODE_NOT_MODIFIED);
            patch.complete();
        }
    }

    private boolean mergeContainerNetworkConfig(ContainerNetworkConfigState currentState,
            ContainerNetworkConfigState patchState, boolean remove) {

        boolean changed = false;

        if (remove) {
            if (patchState.internalServiceNetworkLinks != null) {
                changed |= currentState.internalServiceNetworkLinks
                        .removeAll(patchState.internalServiceNetworkLinks);
            }

            if (patchState.publicServiceNetworkLinks != null) {
                changed |= currentState.publicServiceNetworkLinks
                        .removeAll(patchState.publicServiceNetworkLinks);
            }
        } else {
            if (patchState.internalServiceNetworkLinks != null) {
                currentState.internalServiceNetworkLinks.clear();
            } else {
                patchState.internalServiceNetworkLinks = new HashSet<>();
            }

            if (patchState.publicServiceNetworkLinks != null) {
                currentState.publicServiceNetworkLinks.clear();
            } else {
                patchState.publicServiceNetworkLinks = new HashSet<>();
            }

            changed |= currentState.internalServiceNetworkLinks
                    .addAll(patchState.internalServiceNetworkLinks);

            changed |= currentState.publicServiceNetworkLinks
                    .addAll(patchState.publicServiceNetworkLinks);
        }

        return changed;
    }

    private void reconfigureHostAgent(ContainerHostNetworkConfigState configState, Operation op) {

        if (DeploymentProfileConfig.getInstance().isTest()) {
            // skip the actual network reconfiguration because the network agent is not installed in test mode
            logWarning("No network reconfiguration is performed in test mode...");
            op.complete();
            return;
        }

        String hostId = getSelfId();

        String networkContainerLink = SystemContainerDescriptions
                .getSystemContainerSelfLink(
                        SystemContainerDescriptions.AGENT_CONTAINER_NAME, hostId);

        List<String> command = new ArrayList<>();
        command.add(RECONFIGURE_APP_NAME);

        Set<String> internalServiceNetworkLinks = new HashSet<>();
        Set<String> publicServiceNetworkLinks = new HashSet<>();
        for (ContainerNetworkConfigState containerConfigState : configState.containerNetworkConfigs
                .values()) {
            if (containerConfigState.internalServiceNetworkLinks != null) {
                internalServiceNetworkLinks
                        .addAll(containerConfigState.internalServiceNetworkLinks);
            }
            if (containerConfigState.publicServiceNetworkLinks != null) {
                publicServiceNetworkLinks.addAll(containerConfigState.publicServiceNetworkLinks);
            }
        }

        for (String link : internalServiceNetworkLinks) {
            command.add("-i");
            command.add(link);
        }

        for (String link : publicServiceNetworkLinks) {
            command.add("-p");
            command.add(link);
        }

        ShellContainerExecutorState executorState = new ShellContainerExecutorState();
        executorState.command = command.toArray(new String[command.size()]);

        URI executeUri = UriUtils.buildUri(getHost(), ShellContainerExecutorService.SELF_LINK);

        executeUri = UriUtils.extendUriWithQuery(executeUri,
                ShellContainerExecutorService.CONTAINER_LINK_URI_PARAM, networkContainerLink);

        logInfo("Reconfigure host networking [%s] with command [%s]", hostId, command);

        sendRequest(Operation
                .createPost(executeUri)
                .setBody(executorState)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Failed to reconfigure host networking [%s] with command %s. Error: %s",
                                        hostId, command,
                                        Utils.toString(e));
                                op.fail(e);
                                return;
                            }

                            logInfo("Reconfigure host networking [%s] success", hostId);
                            op.complete();
                        }));
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ContainerHostNetworkConfigState template = (ContainerHostNetworkConfigState) super
                .getDocumentTemplate();

        template.containerNetworkConfigs = new HashMap<>();

        ContainerNetworkConfigState containerConfigState = new ContainerNetworkConfigState();
        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("happy_knuth:80:10.0.0.1:32782");
        containerConfigState.internalServiceNetworkLinks.add("happy_knuth:3306:10.0.0.1:32783");

        template.containerNetworkConfigs.put(
                UriUtils.buildUriPath(ContainerFactoryService.SELF_LINK, "happy_knuth"),
                containerConfigState);

        containerConfigState = new ContainerNetworkConfigState();

        containerConfigState.internalServiceNetworkLinks = new HashSet<>();
        containerConfigState.internalServiceNetworkLinks.add("awesome_pasteur:3306:10.0.0.1:32783");

        template.containerNetworkConfigs.put(
                UriUtils.buildUriPath(ContainerFactoryService.SELF_LINK, "awesome_pasteur"),
                containerConfigState);

        return template;
    }

    public static Map<String, ContainerHostNetworkConfigState> processHostNetworkConfigs(
            Collection<ContainerState> containerStates, InternalServiceLinkProcessor processor) {

        Map<String, ContainerHostNetworkConfigState> hostNetworkConfigs = new HashMap<>();
        for (ContainerState container : containerStates) {
            // On a lot of places there are assumptions that names is only one, maybe we should make that clear?
            String containerName = container.names.get(0);
            String hostId = Service.getId(container.parentLink);

            ContainerHostNetworkConfigState hostNetworkConfig = hostNetworkConfigs.get(hostId);
            if (hostNetworkConfig == null) {
                hostNetworkConfig = new ContainerHostNetworkConfigState();
                hostNetworkConfig.containerNetworkConfigs = new HashMap<>();
                hostNetworkConfigs.put(hostId, hostNetworkConfig);
            }

            Set<String> serviceLinks = processor
                    .generateInternalServiceLinksByContainerName(containerName);

            if (serviceLinks.isEmpty()) {
                continue;
            }

            ContainerNetworkConfigState containerConfigState = new ContainerNetworkConfigState();
            containerConfigState.internalServiceNetworkLinks = serviceLinks;

            hostNetworkConfig.containerNetworkConfigs.put(container.documentSelfLink,
                    containerConfigState);
        }

        return hostNetworkConfigs;
    }

    public static Map<String, ContainerHostNetworkConfigState> processHostNetworkConfigs(
            Collection<ContainerState> containerStates,
            ExposedServiceDescriptionState exposedServiceState,
            PublicServiceLinkProcessor processor) {
        Map<String, ContainerHostNetworkConfigState> hostNetworkConfigs = new HashMap<>();

        Set<String> publicServiceNetworkLinks = new HashSet<>();
        for (ServiceAddressConfig config : exposedServiceState.addressConfigs) {
            publicServiceNetworkLinks.addAll(processor.generatePublicServiceLinks(config));
        }

        for (ContainerState containerState : containerStates) {
            String hostId = Service.getId(containerState.parentLink);

            ContainerHostNetworkConfigState hostNetworkConfig = hostNetworkConfigs.get(hostId);
            if (hostNetworkConfig == null) {
                hostNetworkConfig = new ContainerHostNetworkConfigState();
                hostNetworkConfig.containerNetworkConfigs = new HashMap<>();
                hostNetworkConfigs.put(hostId, hostNetworkConfig);
            }

            ContainerNetworkConfigState containerConfigState = new ContainerNetworkConfigState();
            containerConfigState.publicServiceNetworkLinks = publicServiceNetworkLinks;

            hostNetworkConfig.containerNetworkConfigs.put(containerState.documentSelfLink,
                    containerConfigState);
        }

        return hostNetworkConfigs;
    }

    public static interface InternalServiceLinkProcessor {
        Set<String> generateInternalServiceLinksByContainerName(String containerName);
    }

    public static interface PublicServiceLinkProcessor {
        Set<String> generatePublicServiceLinks(ServiceAddressConfig config);
    }
}
