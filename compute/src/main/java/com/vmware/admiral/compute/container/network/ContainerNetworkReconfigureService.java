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

package com.vmware.admiral.compute.container.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigFactoryService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerHostNetworkConfigState;
import com.vmware.admiral.compute.container.ContainerHostNetworkConfigService.ContainerNetworkConfigState;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.ExposedServiceDescriptionService.ExposedServiceDescriptionState;
import com.vmware.admiral.compute.container.util.ExposeServiceProcessor;
import com.vmware.admiral.compute.container.util.ServiceLinkProcessor;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Service responsible to reconfigure the network after the network settings of a specific container was changed.
 * It will reconfigure the public service links of this container and the internal link configurations to
 * other containers that link to this one.
 *
 */
public class ContainerNetworkReconfigureService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.CONTAINER_NETWORK_RECONFIGURE;

    public static class ContainerNetworkReconfigureState {
        /** (required) The container state that has changed network settings that initiated the reconfigure task */
        public ContainerState containerState;
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.POST) {
            getHost().failRequestActionNotSupported(op);
            return;
        }

        handlePost(op);
    }

    @Override
    public void handlePost(Operation post) {
        ContainerNetworkReconfigureState state = post
                .getBody(ContainerNetworkReconfigureState.class);

        AssertUtil.assertNotNull(state.containerState, "containerState");

        retrieveExposedService(state.containerState, post);
    }

    private void retrieveExposedService(ContainerState container, Operation op) {
        ReconfigState collector = new ReconfigState();
        if (container.exposedServiceLink != null) {

            sendRequest(Operation.createGet(this, container.exposedServiceLink)
                    .setCompletion((o, e) -> {
                        if (e != null) {
                            op.fail(e);
                        }

                        collector.setExposedServiceState(o
                                .getBody(ExposedServiceDescriptionState.class));
                        fetchAllContainerStatesFromSameContext(container, collector, op);
                    }));
        } else {
            fetchAllContainerStatesFromSameContext(container, collector, op);
        }
    }

    private void fetchAllContainerStatesFromSameContext(ContainerState container,
            ReconfigState reconfigState,
            Operation op) {
        String compositeComponentLink = container.compositeComponentLink;

        if (compositeComponentLink == null) {
            logFine("Container %s does not have compositeComponentLink, no need to reconfigure network",
                    container.documentSelfLink);
            op.complete();
            return;
        }

        QueryTask contStateQuery = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK, compositeComponentLink);

        QueryUtil.addExpandOption(contStateQuery);

        new ServiceDocumentQuery<ContainerState>(getHost(), ContainerState.class)
                .query(
                        contStateQuery,
                        (r) -> {
                            try {
                                if (r.hasException()) {
                                    op.fail(r.getException());

                                } else if (r.hasResult()) {
                                    ContainerState result = r.getResult();
                                    if (container.documentSelfLink.equals(result.documentSelfLink)
                                            && container.documentVersion >= result.documentVersion) {
                                        reconfigState.putContainerState(container);
                                    } else {
                                        reconfigState.putContainerState(result);
                                    }
                                } else {
                                    Collection<String> containerDescriptionLinks = reconfigState
                                            .getContainerDescriptionLinks();
                                    if (containerDescriptionLinks.isEmpty()) {
                                        op.fail(new IllegalStateException(
                                                "No container states were found"));
                                        return;
                                    }

                                    fetchContainerDescriptions(container, reconfigState, op);
                                }
                            } catch (Exception e) {
                                op.fail(e);
                            }
                        });
    }

    private void fetchContainerDescriptions(ContainerState container,
            ReconfigState reconfigState, Operation op) {

        Collection<String> containerDescriptionLinks = reconfigState.getContainerDescriptionLinks();
        if (!containerDescriptionLinks.contains(container.descriptionLink)) {
            String errMsg = String
                    .format("Description links should contain the description link of the trigger container [%s]",
                            container.descriptionLink);
            op.fail(new IllegalStateException(errMsg));
            return;
        }

        QueryTask query = QueryUtil.buildQuery(ContainerDescription.class, false);
        QueryUtil.addExpandOption(query);
        QueryUtil.addListValueClause(query, ContainerDescription.FIELD_NAME_SELF_LINK,
                containerDescriptionLinks);

        new ServiceDocumentQuery<ContainerDescription>(getHost(), ContainerDescription.class)
                .query(query, (r) -> {
                    if (r.hasException()) {
                        op.fail(r.getException());
                    } else if (r.hasResult()) {
                        reconfigState.putContainerDescription(r.getResult());
                    } else {
                        fetchComputeHosts(container, reconfigState, op);
                    }
                });
    }

    private void fetchComputeHosts(ContainerState container, ReconfigState reconfigState,
            Operation op) {

        Collection<String> computeLinks = reconfigState.getComputeLinks();

        QueryTask depComputeQuery = QueryUtil.buildQuery(ComputeState.class, false);
        QueryUtil.addExpandOption(depComputeQuery);
        QueryUtil.addListValueClause(depComputeQuery, ComputeState.FIELD_NAME_SELF_LINK,
                computeLinks);

        new ServiceDocumentQuery<ComputeState>(getHost(), ComputeState.class)
                .query(depComputeQuery, (r) -> {
                    if (r.hasException()) {
                        op.fail(r.getException());
                    } else if (r.hasResult()) {
                        ComputeState computeState = r.getResult();
                        reconfigState.putComputeState(computeState);
                    } else {
                        processNetworkConfigs(container, reconfigState, op);
                    }
                });
    }

    private void processNetworkConfigs(ContainerState container, ReconfigState reconfigState,
            Operation op) {

        Map<String, ContainerHostNetworkConfigState> combinedNetworkConfigs = new HashMap<>();

        if (reconfigState.getExposedServiceState() != null) {
            ContainerDescription targetDescription = reconfigState
                    .getDescription(container.descriptionLink);
            Collection<ContainerState> targetContainerStates = reconfigState
                    .getContainerStatesByDescriptionLink(targetDescription.documentSelfLink);

            ExposeServiceProcessor exposeServiceProcessor = new ExposeServiceProcessor(
                    new ArrayList<>(targetContainerStates));

            for (ComputeState computeState : reconfigState.getComputeStates()) {
                exposeServiceProcessor.putComputeState(computeState);
            }

            Map<String, ContainerHostNetworkConfigState> hostNetworkConfigsPublicLinks = ContainerHostNetworkConfigService
                    .processHostNetworkConfigs(targetContainerStates,
                            reconfigState.getExposedServiceState(), exposeServiceProcessor);

            mergeNetworkConfigs(combinedNetworkConfigs, hostNetworkConfigsPublicLinks);
        }

        Map<String, ContainerHostNetworkConfigState> hostNetworkConfigsInternalLinks = getDependendContainersInternalLinks(
                container, reconfigState);
        mergeNetworkConfigs(combinedNetworkConfigs, hostNetworkConfigsInternalLinks);

        updateHostNetworkConfigurations(combinedNetworkConfigs, op);
    }

    private Map<String, ContainerHostNetworkConfigState> getDependendContainersInternalLinks(
            ContainerState container,
            ReconfigState reconfigState) {
        ContainerDescription targetDescription = reconfigState
                .getDescription(container.descriptionLink);
        Set<ContainerDescription> dependendDescriptions = reconfigState
                .getDependendDescriptions(targetDescription);

        Map<String, ContainerHostNetworkConfigState> combinedNetworkConfigs = new HashMap<>();

        for (ContainerDescription cd : dependendDescriptions) {
            Map<String, ContainerHostNetworkConfigState> networkConfigs = getInternalLinkNetworkConfigurationsForContainerDescription(
                    cd, reconfigState);
            mergeNetworkConfigs(combinedNetworkConfigs, networkConfigs);
        }

        return combinedNetworkConfigs;
    }

    private Map<String, ContainerHostNetworkConfigState> getInternalLinkNetworkConfigurationsForContainerDescription(
            ContainerDescription targetContainerDescription,
            ReconfigState reconfigState) {
        if (targetContainerDescription.links == null
                || targetContainerDescription.links.length == 0) {
            return Collections.emptyMap();
        }
        ServiceLinkProcessor processor = new ServiceLinkProcessor(targetContainerDescription.links);
        for (String dependencyName : processor.getDependencyNames()) {
            for (ContainerDescription cd : reconfigState.getContainerDescriptions()) {
                if (cd.name.equals(dependencyName)) {
                    processor.putDepContainerDescriptions(cd);

                    for (ContainerState cs : reconfigState
                            .getContainerStatesByDescriptionLink(cd.documentSelfLink)) {
                        processor.putDepContainerState(cs);
                        processor.putDepParentCompute(reconfigState.getComputeState(cs.parentLink));
                    }
                }
            }
        }

        Collection<ContainerState> containerStates = reconfigState
                .getContainerStatesByDescriptionLink(targetContainerDescription.documentSelfLink);
        return ContainerHostNetworkConfigService
                .processHostNetworkConfigs(containerStates, processor);
    }

    private void updateHostNetworkConfigurations(
            Map<String, ContainerHostNetworkConfigState> hostNetworkConfigs,
            Operation op) {

        List<Operation> operations = new ArrayList<>();

        for (Entry<String, ContainerHostNetworkConfigState> entry : hostNetworkConfigs
                .entrySet()) {
            String hostId = entry.getKey();
            ContainerHostNetworkConfigState patchNetworkConfig = entry.getValue();

            if (patchNetworkConfig.containerNetworkConfigs.isEmpty()) {
                continue;
            }

            String hostNetworkConfigLink = UriUtils.buildUriPath(
                    ContainerHostNetworkConfigFactoryService.SELF_LINK, hostId);

            operations.add(Operation
                    .createPatch(this, hostNetworkConfigLink)
                    .setBody(patchNetworkConfig));
        }

        if (operations.isEmpty()) {
            op.complete();
            return;
        }
        OperationJoin.create(operations).setCompletion((ops, exs) -> {
            if (exs != null) {
                op.fail(new IllegalStateException("Failure patching host network config: "
                        + Utils.toString(exs), null));
                return;
            }
            op.complete();
        }).sendWith(this);
    }

    private static void mergeNetworkConfigs(Map<String, ContainerHostNetworkConfigState> target,
            Map<String, ContainerHostNetworkConfigState> source) {
        for (Entry<String, ContainerHostNetworkConfigState> entry : source.entrySet()) {
            String hostKey = entry.getKey();
            ContainerHostNetworkConfigState targetNetworkConfigState = target.get(hostKey);
            if (targetNetworkConfigState == null) {
                targetNetworkConfigState = new ContainerHostNetworkConfigState();
                targetNetworkConfigState.containerNetworkConfigs = new HashMap<>();
                target.put(hostKey, targetNetworkConfigState);
            }

            mergeContainerNetworkConfigs(targetNetworkConfigState.containerNetworkConfigs,
                    entry.getValue().containerNetworkConfigs);
        }
    }

    private static void mergeContainerNetworkConfigs(
            Map<String, ContainerNetworkConfigState> target,
            Map<String, ContainerNetworkConfigState> source) {
        for (Entry<String, ContainerNetworkConfigState> entry : source.entrySet()) {
            String containerKey = entry.getKey();
            ContainerNetworkConfigState targetContainerNetworkConfigState = target
                    .get(containerKey);
            if (targetContainerNetworkConfigState == null) {
                targetContainerNetworkConfigState = new ContainerNetworkConfigState();
                target.put(containerKey, targetContainerNetworkConfigState);
            }

            ContainerNetworkConfigState sourceContainerNetworkConfigState = entry.getValue();
            if (sourceContainerNetworkConfigState.internalServiceNetworkLinks != null) {
                if (targetContainerNetworkConfigState.internalServiceNetworkLinks == null) {
                    targetContainerNetworkConfigState.internalServiceNetworkLinks = new HashSet<>();
                }
                targetContainerNetworkConfigState.internalServiceNetworkLinks
                        .addAll(sourceContainerNetworkConfigState.internalServiceNetworkLinks);
            }

            if (sourceContainerNetworkConfigState.publicServiceNetworkLinks != null) {
                if (targetContainerNetworkConfigState.publicServiceNetworkLinks == null) {
                    targetContainerNetworkConfigState.publicServiceNetworkLinks = new HashSet<>();
                }
                targetContainerNetworkConfigState.publicServiceNetworkLinks
                        .addAll(sourceContainerNetworkConfigState.publicServiceNetworkLinks);
            }
        }
    }

    private static class ReconfigState {
        private final Map<String, ComputeState> computeStates = new HashMap<>();
        private final Map<String, ContainerState> containerStates = new HashMap<>();
        private final Map<String, ContainerDescription> containerDescriptions = new HashMap<>();
        private ExposedServiceDescriptionState exposedServiceState;

        public ReconfigState() {
        }

        public void putComputeState(ComputeState computeState) {
            computeStates.put(computeState.documentSelfLink, computeState);
        }

        public ComputeState getComputeState(String computeStateLink) {
            return computeStates.get(computeStateLink);
        }

        public Collection<ComputeState> getComputeStates() {
            return computeStates.values();
        }

        public void putContainerState(ContainerState containerState) {
            containerStates.put(containerState.documentSelfLink, containerState);
        }

        public void putContainerDescription(ContainerDescription containerDescription) {
            containerDescriptions.put(containerDescription.documentSelfLink, containerDescription);
        }

        public Collection<String> getContainerDescriptionLinks() {
            return containerStates.values().stream().map((state) -> state.descriptionLink)
                    .collect(Collectors.toSet());
        }

        public Collection<ContainerDescription> getContainerDescriptions() {
            return containerDescriptions.values();
        }

        public Collection<ContainerState> getContainerStatesByDescriptionLink(String descriptionLink) {
            return containerStates.values().stream()
                    .filter((cs) -> cs.descriptionLink.equals(descriptionLink))
                    .collect(Collectors.toList());
        }

        public Collection<String> getComputeLinks() {
            return containerStates.values().stream().map((state) -> state.parentLink)
                    .collect(Collectors.toSet());
        }

        public ContainerDescription getDescription(String descriptionLink) {
            return containerDescriptions.get(descriptionLink);
        }

        public void setExposedServiceState(ExposedServiceDescriptionState exposedServiceState) {
            this.exposedServiceState = exposedServiceState;
        }

        public ExposedServiceDescriptionState getExposedServiceState() {
            return exposedServiceState;
        }

        public Set<ContainerDescription> getDependendDescriptions(
                ContainerDescription targetDescription) {
            return containerDescriptions.values().stream().filter((cd) -> {
                if (cd.links != null) {
                    for (String link : cd.links) {
                        String service = link.split(":")[0];
                        if (service.equals(targetDescription.name)) {
                            return true;
                        }
                    }
                }

                return false;
            }).collect(Collectors.toSet());
        }
    }
}
