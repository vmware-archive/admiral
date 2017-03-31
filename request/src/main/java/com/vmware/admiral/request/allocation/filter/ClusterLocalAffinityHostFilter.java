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
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.admiral.compute.container.CompositeDescriptionService.CompositeDescription;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService;
import com.vmware.admiral.compute.container.volume.ContainerVolumeDescriptionService.ContainerVolumeDescription;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.compute.container.volume.VolumeUtil;
import com.vmware.admiral.request.PlacementHostSelectionTaskService.PlacementHostSelectionTaskState;
import com.vmware.admiral.request.ReservationTaskFactoryService;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification;

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
                && (volumeNames != null && volumeNames.size() > 0);
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

        if (volumeNames.isEmpty()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        if (VolumeUtil.isContainerRequest(state.customProperties)) {
            findVolumeDescriptionsByLinks(state, hostSelectionMap, callback, null);
        } else {
            findVolumeDescriptionsByComponent(state, hostSelectionMap, callback);
        }
    }

    private void findVolumeDescriptionsByLinks(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback,
            List<String> containerVolumeLinks) {

        final QueryTask q = QueryUtil.buildQuery(ContainerVolumeDescription.class, false);
        QueryUtil.addCaseInsensitiveListValueClause(q, ContainerVolumeDescription.FIELD_NAME_NAME,
                volumeNames);
        if (containerVolumeLinks != null) {
            QueryUtil.addListValueClause(q,
                    ContainerVolumeDescription.FIELD_NAME_SELF_LINK,
                    containerVolumeLinks);
        } else {
            QueryTask.Query contextClause = new QueryTask.Query()
                    .setTermPropertyName(QuerySpecification.buildCompositeFieldName(
                            ResourceState.FIELD_NAME_CUSTOM_PROPERTIES,
                            RequestUtils.FIELD_NAME_CONTEXT_ID_KEY))
                    .setTermMatchValue(state.contextId);
            q.querySpec.query.addBooleanClause(contextClause);
        }
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

    private void findVolumeDescriptionsByComponent(PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {
        String compositeComponentLink = UriUtils
                .buildUriPath(CompositeComponentFactoryService.SELF_LINK, state.contextId);
        host.sendRequest(Operation.createGet(UriUtils.buildUri(host, compositeComponentLink))
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.WARNING,
                                "Exception while getting CompositeComponent. Error: [%s]",
                                ex.getMessage());
                        callback.complete(null, ex);
                        return;
                    }
                    CompositeComponent body = o.getBody(CompositeComponent.class);
                    host.sendRequest(Operation.createGet(UriUtils.buildUri(host, body.compositeDescriptionLink))
                            .setReferer(host.getUri())
                            .setCompletion((o2, ex2) -> {
                                if (ex2 != null) {
                                    host.log(Level.WARNING,
                                            "Exception while getting CompositeDescription. Error: [%s]",
                                            ex2.getMessage());
                                    callback.complete(null, ex2);
                                    return;
                                }
                                List<String> containerVolumeLinks = new ArrayList<>();
                                CompositeDescription descBody = o2.getBody(CompositeDescription.class);
                                for (String descriptionLink : descBody.descriptionLinks) {
                                    if (descriptionLink.startsWith(ContainerVolumeDescriptionService.FACTORY_LINK)) {
                                        containerVolumeLinks.add(descriptionLink);
                                    }
                                }
                                if (containerVolumeLinks.isEmpty()) {
                                    callback.complete(hostSelectionMap, null);
                                    return;
                                }
                                findVolumeDescriptionsByLinks(state, hostSelectionMap, callback,
                                        containerVolumeLinks);
                            }));
                }));
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
