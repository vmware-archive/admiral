/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.request.allocation.filter;

import static com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.DEFAULT_VOLUME_DRIVER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentFactoryService;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * container is clustered and there are local volumes attached to it. In this case, the target
 * containers should be placed in the same host in order to share the local volume.
 * NamedVolumeAffinityHostFilter cannot fulfill this condition when containers are created with
 * initial size greater than 1, because at the time the filter is applied, container states are
 * not yet created.
 */
public class ClusterLocalAffinityHostFilter
        implements HostSelectionFilter<PlacementHostSelectionTaskState> {
    private final ContainerDescription desc;
    private final ServiceHost host;
    private final List<String> volumeNames;
    private final Random randomIntegers = new Random();

    public ClusterLocalAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        this.host = host;
        this.desc = desc;
        this.volumeNames = VolumeUtil.extractVolumeNames(desc.volumes);
    }

    @Override
    public boolean isActive() {
        return (desc._cluster != null && desc._cluster > 1)
                && (desc.volumes != null && desc.volumes.length > 0);
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return Collections.emptyMap();
    }

    @Override
    public void filter(final PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {
        // Nothing to filter here.
        if (hostSelectionMap.size() <= 1) {
            host.log(Level.FINE, "Only one host in selection. ClusterLocalAffinityHostFilter filtering will be skipped.");
            callback.complete(hostSelectionMap, null);
            return;
        }

        String serviceLink = state.serviceTaskCallback != null
                ? state.serviceTaskCallback.serviceSelfLink
                : null;
        // Filter should be ignored on Reservation stage.
        if (serviceLink != null
                && serviceLink.startsWith(ReservationTaskFactoryService.SELF_LINK)) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        findVolumeDescriptions(state, hostSelectionMap, callback);
    }

    private void findVolumeDescriptions(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        if (volumeNames.isEmpty()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        final QueryTask q = QueryUtil.buildQuery(ContainerVolumeDescription.class, false);
        QueryUtil.addCaseInsensitiveListValueClause(q, ContainerVolumeDescription.FIELD_NAME_NAME,
                volumeNames);
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
                        if (volumeNames.contains(desc.name)) {
                            final DescName descName = new DescName();
                            descName.descLink = desc.documentSelfLink;
                            descName.descriptionName = desc.name;
                            descLinksWithNames.put(descName.descLink, descName);
                        }
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

        QueryTask.Query driverClause = new QueryTask.Query()
                .setTermPropertyName(ContainerVolumeState.FIELD_NAME_DRIVER)
                .setTermMatchValue(DEFAULT_VOLUME_DRIVER);

        String compositeComponentLinksItemField = QueryTask.QuerySpecification
                .buildCollectionItemName(
                        ContainerVolumeState.FIELD_NAME_COMPOSITE_COMPONENT_LINKS);
        List<String> cclValues = new ArrayList<>(
                Arrays.asList(UriUtils.buildUriPath(CompositeComponentFactoryService.SELF_LINK,
                        state.contextId)));

        // get count of matching volumes with local drivers attached
        QueryTask q = QueryUtil.buildQuery(ContainerVolumeState.class, false, driverClause);
        QueryUtil.addListValueClause(q, compositeComponentLinksItemField, cclValues);
        QueryUtil.addListValueClause(q, ContainerVolumeState.FIELD_NAME_DESCRIPTION_LINK,
                descLinksWithNames.keySet());
        QueryUtil.addCountOption(q);

        new ServiceDocumentQuery<ContainerVolumeState>(host, ContainerVolumeState.class)
                .query(q, (r) -> {
                    if (r.hasException()) {
                        host.log(
                                Level.WARNING,
                                "Exception while selecting container volumes with contextId [%s]. Error: [%s]",
                                state.contextId, r.getException().getMessage());
                        callback.complete(null, r.getException());
                    } else if (r.getCount() > 0) {
                        // if there are local volumes, select one hostSelection
                        final Map<String, HostSelection> filteredHostSelectionMap = new HashMap<>();
                        List<HostSelection> hostSelections = new ArrayList<>(hostSelectionMap.values());
                        HostSelection hostSelection = hostSelections.get(randomIntegers.nextInt(hostSelections.size()));
                        filteredHostSelectionMap.put(hostSelection.hostLink, hostSelection);
                        callback.complete(filteredHostSelectionMap, null);
                    } else {
                        callback.complete(hostSelectionMap, null);
                    }
                });
    }
}
