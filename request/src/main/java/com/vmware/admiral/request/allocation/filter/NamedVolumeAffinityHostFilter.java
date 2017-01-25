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
import java.util.function.Consumer;
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
import com.vmware.admiral.request.utils.RequestUtils;
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
    private List<String> tenantLinks;

    public NamedVolumeAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        this.host = host;
        this.volumeNames = extractVolumeNames(desc.volumes);
        this.tenantLinks = desc.tenantLinks;
    }

    @Override
    public void filter(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        if (!isActive()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        prefilterByExternalVolumesLocation(state, hostSelectionMap,
                () -> findVolumeDescriptions(state, hostSelectionMap, callback),
                (e) -> callback.complete(null, e));
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

    private void prefilterByExternalVolumesLocation(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap,
            Runnable successCallback,
            Consumer<Throwable> failureCallback) {

        // query for external volumes that match the required volume names and drivers by the
        // currently processed container. Choose one volume state for each external volume and
        // filter the host map leaving only those hosts that belong to the selected volumes

        // Note: this approach assumes that the external volumes are already created with the
        // required plug-ins and there is no naming conflicts between volumes created with different
        // plug-ins. The more complex approach of querying the actual volume descriptions associated
        // with the current container cannot be applied because vRA does not provide a composite
        // description for application provisioning.

        final QueryTask volumeQuery = QueryUtil.buildQuery(ContainerVolumeState.class, false);

        QueryUtil.addListValueClause(volumeQuery, ContainerVolumeState.FIELD_NAME_NAME,
                volumeNames);

        if (tenantLinks != null && !tenantLinks.isEmpty()) {
            volumeQuery.querySpec.query
                    .addBooleanClause(QueryUtil.addTenantGroupAndUserClause(tenantLinks));
        }

        QueryUtil.addExpandOption(volumeQuery);

        Map<String, List<ContainerVolumeState>> externalVolumesByName = new HashMap<>();
        new ServiceDocumentQuery<>(host, ContainerVolumeState.class)
                .query(volumeQuery, (r) -> {
                    if (r.hasException()) {
                        host.log(Level.WARNING,
                                "Exception while querying for volume states. Error: [%s]",
                                r.getException().getMessage());
                        failureCallback.accept(r.getException());
                    } else if (r.hasResult()) {
                        ContainerVolumeState volume = r.getResult();
                        if (volume.external != null && volume.external) {
                            externalVolumesByName
                                    .computeIfAbsent(volume.name, v -> new ArrayList<>())
                                    .add(volume);
                        }
                    } else {
                        if (externalVolumesByName.isEmpty()) {
                            // assume the container is not associated with any external volume
                            successCallback.run();
                            return;
                        }

                        // remove the existing volumes from the list of required ones
                        volumeNames.removeAll(externalVolumesByName.keySet());

                        selectExternalVolumes(state, externalVolumesByName, hostSelectionMap,
                                successCallback);
                    }
                });
    }

    private void selectExternalVolumes(PlacementHostSelectionTaskState state,
            Map<String, List<ContainerVolumeState>> externalVolumesByName,
            Map<String, HostSelection> hostSelectionMap,
            Runnable callback) {

        // choose an existing volume state for each external volume that is attached to the current
        // container. Use the volume parentLinks to filter the hosts that don't belong to the chosen
        // external volumes

        Set<String> hostLinks = new HashSet<>();

        for (List<ContainerVolumeState> volumes: externalVolumesByName.values()) {
            ContainerVolumeState selectedVolume = pickOnePerContext(state, volumes);
            hostLinks.addAll(selectedVolume.parentLinks);
        }

        hostSelectionMap.entrySet().removeIf(e -> !hostLinks.contains(e.getKey()));
        callback.run();
    }

    private ContainerVolumeState pickOnePerContext(PlacementHostSelectionTaskState state,
            List<ContainerVolumeState> volumes) {

        // In case there are multiple volumes with the same name but on different hosts and another
        // container must be attached to the current external volume, make sure the same single
        // external volume is chosen for each such container

        int idx = Math
                .abs(state.customProperties.get(RequestUtils.FIELD_NAME_CONTEXT_ID_KEY).hashCode()
                        % volumes.size());
        return volumes.get(idx);
    }

    private void findVolumeDescriptions(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        if (volumeNames.isEmpty()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

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
            Map<String, HostSelection> hostSelectionMap,
            Map<String, DescName> descLinksWithNames,
            HostSelectionFilterCompletion callback) {

        QueryTask q = QueryUtil.buildQuery(ContainerVolumeState.class, false);

        String compositeComponentLinksItemField = QueryTask.QuerySpecification
                .buildCollectionItemName(
                        ContainerVolumeState.FIELD_NAME_COMPOSITE_COMPONENT_LINKS);
        List<String> cclValues = new ArrayList<>(
                Arrays.asList(UriUtils.buildUriPath(CompositeComponentFactoryService.SELF_LINK,
                        state.contextId)));
        QueryUtil.addListValueClause(q, compositeComponentLinksItemField, cclValues);

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
                        descName.addResourceNames(
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
            callback.complete(null, new HostSelectionFilterException(errMsg,
                    "request.volumes.filter.hosts.unavailable", requiredDrivers.toString()));
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
                                callback.complete(null, new HostSelectionFilterException(errMsg,
                                        "request.volumes.filter.container-descriptions.query",
                                        volumeNames.toString(), r.getException().getMessage()));
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
                                            + " but placed on different hosts.",
                                            "request.volumes.filter.multiple.containers"));
                        } else {
                            HostSelection host = hostSelectionMap.get(parentLinks.get(0));
                            if (host == null) {
                                callback.complete(null, new HostSelectionFilterException(
                                        "Unable to place containers sharing local volumes"
                                                + " on the same host.",
                                                "request.volumes.filter.no.host"));
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
