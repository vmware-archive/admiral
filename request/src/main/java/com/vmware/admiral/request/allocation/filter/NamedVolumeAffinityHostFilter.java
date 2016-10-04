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

package com.vmware.admiral.request.allocation.filter;

import static com.vmware.admiral.compute.ContainerHostService.DOCKER_HOST_PLUGINS_VOLUME_PROP_NAME;
import static com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.DEFAULT_VOLUME_DRIVER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * A filter implementing {@link HostSelectionFilter} aimed to provide host selection for containers
 * with named volumes. Based on the volume drivers of the linked named volumes the selection
 * algorithm will filter out the docker hosts that do not have the driver installed on them.
 */
public class NamedVolumeAffinityHostFilter
        implements HostSelectionFilter<PlacementHostSelectionTaskState> {

    private final ServiceHost host;
    private List<String> volumeNames;

    public NamedVolumeAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        this.host = host;
        this.volumeNames = extractVolumeNames(desc.volumes);
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        if (!isActive()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        findVolumeDescriptions(state, hostSelectionMap, callback);
    }

    @Override
    public boolean isActive() {
        return !volumeNames.isEmpty();
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return isActive() ? volumeNames.stream().collect(
                Collectors.toMap(p -> p, p -> new AffinityConstraint(p)))
                : Collections.emptyMap();
    }

    private void findVolumeDescriptions(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        final QueryTask q = QueryUtil.buildQuery(ContainerVolumeDescription.class, false);

        QueryUtil.addListValueClause(q, ContainerVolumeDescription.FIELD_NAME_NAME, volumeNames);
        QueryUtil.addExpandOption(q);

        final Map<String, DescName> descLinksWithNames = new HashMap<>();
        new ServiceDocumentQuery<>(host, ContainerVolumeDescription.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.WARNING,
                                "Exception while filtering volume descriptions. Error: [%s]",
                                r.getException().getMessage());
                        callback.complete(null, r.getException());
                    } else if (r.hasResult()) {
                        final ContainerVolumeDescription desc = r.getResult();
                        final DescName descName = new DescName();
                        descName.descLink = desc.documentSelfLink;
                        descName.descriptionName = desc.name;
                        descLinksWithNames.put(descName.descLink, descName);
                    } else {
                        if (descLinksWithNames.isEmpty()) {
                            callback.complete(hostSelectionMap, null);
                        } else {
                            findContainerVolumes(state, hostSelectionMap, descLinksWithNames,
                                    callback);
                        }
                    }
                });
    }

    private void findContainerVolumes(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, Map<String, DescName> descLinksWithNames,
            HostSelectionFilterCompletion callback) {

        QueryTask q = QueryUtil.buildPropertyQuery(ContainerVolumeState.class,
                ContainerVolumeState.FIELD_NAME_COMPOSITE_COMPONENT_LINK, UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, state.contextId));
        q.taskInfo.isDirect = false;
        QueryUtil.addExpandOption(q);

        QueryUtil.addListValueClause(q,
                ContainerVolumeState.FIELD_NAME_DESCRIPTION_LINK, descLinksWithNames.keySet());

        final Map<String, Set<String>> requiredDrivers = new HashMap<>();
        new ServiceDocumentQuery<ContainerVolumeState>(host, ContainerVolumeState.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        host.log(
                                Level.WARNING,
                                "Exception while selecting container volumes with contextId [%s]. Error: [%s]",
                                state.contextId, r.getException().getMessage());
                        callback.complete(null, r.getException());
                    } else if (r.hasResult()) {
                        ContainerVolumeState volumeState = r.getResult();
                        final DescName descName = descLinksWithNames
                                .get(volumeState.descriptionLink);
                        descName.addContainerNames(
                                Collections.singletonList(volumeState.name));
                        requiredDrivers.computeIfAbsent(volumeState.driver, v -> new HashSet<>())
                                .add(descName.descriptionName);

                        for (HostSelection hs : hostSelectionMap.values()) {
                            hs.addDesc(descName);
                        }
                    } else {
                        filterBySupportedVolumeDrivers(state, requiredDrivers, hostSelectionMap,
                                callback);
                    }
                });
    }

    private void filterBySupportedVolumeDrivers(PlacementHostSelectionTaskState state,
            Map<String, Set<String>> requiredDrivers, Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        hostSelectionMap = hostSelectionMap.entrySet().stream()
                .filter(host -> supportsDrivers(requiredDrivers.keySet(), host.getValue()))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));

        if (hostSelectionMap.isEmpty()) {
            String errMsg = String.format("No hosts found supporting the '%s' volume drivers.",
                    requiredDrivers.toString());
            callback.complete(null, new HostSelectionFilterException(errMsg));
            return;
        }

        Set<String> localVolumeNames = requiredDrivers.get(DEFAULT_VOLUME_DRIVER);
        if (localVolumeNames != null) {
            // find container that share the same local volumes with us
            // and have hosts already assigned
            queryContainersDescs(state, localVolumeNames, hostSelectionMap, callback);
        } else {
            try {
                callback.complete(hostSelectionMap, null);
            } catch (Throwable e) {
                host.log(Level.WARNING, "Exception when completing callback. Error: [%s]",
                        e.getMessage());
                callback.complete(null, e);
            }
        }
    }

    private void queryContainersDescs(PlacementHostSelectionTaskState state,
            Set<String> volumeNames, Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        QueryTask descQueryTask = QueryUtil.buildQuery(ContainerDescription.class, false);

        String volumeItemField = QueryTask.QuerySpecification.buildCollectionItemName(
                ContainerDescription.FIELD_NAME_VOLUMES);

        QueryUtil.addListValueClause(descQueryTask, volumeItemField,
                volumeNames.stream().map(v -> v + "*").collect(Collectors.toSet()),
                MatchType.WILDCARD);
        QueryUtil.addExpandOption(descQueryTask);

        List<String> containersDescLinks = new ArrayList<>();
        new ServiceDocumentQuery<>(host, ContainerDescription.class)
                .query(descQueryTask,
                        (r) -> {
                            if (r.hasException()) {
                                String errMsg = String.format(
                                        "Exception while filtering container descriptions"
                                        + " with local volume names %s. Error: [%s]",
                                        volumeNames.toString(),
                                        r.getException().getMessage());
                                callback.complete(null, new HostSelectionFilterException(errMsg));
                            } else if (r.hasResult()) {
                                containersDescLinks.add(r.getResult().documentSelfLink);
                            } else {
                                // there are no containers that share our local volumes
                                if (containersDescLinks.isEmpty()) {
                                    callback.complete(hostSelectionMap, null);
                                    return;
                                }

                                queryContainers(state, containersDescLinks, hostSelectionMap,
                                        callback);
                            }
                        });

    }

    private void queryContainers(PlacementHostSelectionTaskState state,
            List<String> containersDescLinks, Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        QueryTask q = QueryUtil.buildPropertyQuery(ContainerState.class,
                ContainerState.FIELD_NAME_COMPOSITE_COMPONENT_LINK, UriUtils.buildUriPath(
                        CompositeComponentFactoryService.SELF_LINK, state.contextId));
        q.taskInfo.isDirect = false;
        QueryUtil.addExpandOption(q);

        QueryUtil.addListValueClause(q,
                ContainerState.FIELD_NAME_DESCRIPTION_LINK, containersDescLinks);

        final List<String> parentLinks = new ArrayList<>();
        new ServiceDocumentQuery<>(host, ContainerState.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        host.log(
                                Level.WARNING,
                                "Exception while filtering container states. Error: [%s]",
                                r.getException().getMessage());
                        callback.complete(null, r.getException());
                    } else if (r.hasResult()) {
                        String link = r.getResult().parentLink;
                        if (link != null) {
                            parentLinks.add(link);
                        }
                    } else {
                        if (parentLinks.isEmpty()) {
                            // other containers that share our local volumes do not have
                            // hosts assigned; we can choose whichever host from the list
                            callback.complete(hostSelectionMap, null);
                        } else if (parentLinks.size() > 1) {
                            // there are multiple containers that share our local volumes
                            // but are placed on different hosts -> placement is impossible
                            callback.complete(null, new HostSelectionFilterException(
                                    "Detected multiple containers sharing local volumes"
                                            + " but placed on different hosts."));
                        } else {
                            HostSelection host = hostSelectionMap.get(parentLinks.get(0));
                            if (host == null) {
                                callback.complete(null, new HostSelectionFilterException(
                                        "Unable to place containers sharing local volumes"
                                                + " on the same host."));
                            } else {
                                callback.complete(Collections.singletonMap(
                                        parentLinks.get(0), host), null);
                            }
                        }
                    }
                });
    }

    private boolean supportsDrivers(Set<String> requiredDrivers, HostSelection hostSelection) {
        if (hostSelection.plugins != null) {
            @SuppressWarnings("unchecked")
            List<String> supportedDrivers = Utils.getJsonMapValue(hostSelection.plugins,
                    DOCKER_HOST_PLUGINS_VOLUME_PROP_NAME, List.class);
            return supportedDrivers.containsAll(requiredDrivers);
        }

        return false;
    }

    private List<String> extractVolumeNames(String[] volumes) {
        if (volumes == null || volumes.length == 0) {
            return Collections.emptyList();
        }

        return Arrays.stream(volumes)
                .map((v) -> extractVolumeName(v))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String extractVolumeName(String volume) {
        String hostPart = volume.split(":/")[0];
        // a mount point starts with either / | ~ | . | ..
        if (!hostPart.isEmpty() &&  !hostPart.matches("^(/|~|\\.|\\.\\.).*$")) {
            return hostPart;
        }
        return null;
    }
}
