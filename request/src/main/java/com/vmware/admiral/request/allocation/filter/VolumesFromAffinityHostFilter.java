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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;
import com.vmware.admiral.request.utils.RequestUtils;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case the
 * {@link ContainerDescription} specifies <code>volumesFrom</code> property. Based on the dependent
 * container spec name specified in <code>volumesFrom</code>, the same host will be selected since
 * <code>volumesFrom</code> defines dependency on the same host.
 */
public class VolumesFromAffinityHostFilter extends BaseAffinityHostFilter {
    private static final String READ_ONLY_SUFFIX = ":ro";
    private static final String READ_WRITE_SUFFIX = ":rw";
    private final String[] volumesFrom;
    private final String containerDescriptionName;

    public VolumesFromAffinityHostFilter(ServiceHost host, ContainerDescription desc) {
        super(host, ContainerDescription.FIELD_NAME_VOLUMES_FROM);
        this.volumesFrom = desc.volumesFrom;
        containerDescriptionName = desc.name;
    }

    @Override
    public void filter(
            PlacementHostSelectionTaskService.PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        if (!isActive()
                && state.getCustomProperty(RequestUtils.CLUSTERING_OPERATION_CUSTOM_PROP) == null) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        host.log(
                Level.INFO,
                "Filter for containerDesc property [%s], value: [%s] and contextId: [%s] is active for placement host selection task: %s",
                affinityPropertyName, getAffinity(), state.contextId, state.documentSelfLink);

        findContainerDescriptions(state, hostSelectionMap, callback, getDescQuery());
    }

    @Override
    public boolean isActive() {
        return hasOutgoingAffinities();
    }

    @Override
    protected QueryTask getDescQuery() {
        //Get all container descriptions whose names are in the volumes from of this one
        //and all container descriptions who have volumes from to this one
        return getBidirectionalDescQuery(ContainerDescription.FIELD_NAME_VOLUMES_FROM,
                containerDescriptionName + "*", getAffinity());
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!hasOutgoingAffinities()) {
            return Collections.emptyMap();
        }

        HashMap<String, AffinityConstraint> affinityConstraints = new HashMap<>(volumesFrom.length);
        for (String volumeFrom : volumesFrom) {
            final AffinityConstraint constraint = new AffinityConstraint();
            if (volumeFrom.endsWith(READ_ONLY_SUFFIX) ||
                    volumeFrom.endsWith(READ_WRITE_SUFFIX)) {
                constraint.name = volumeFrom.substring(0,
                        volumeFrom.length() - READ_ONLY_SUFFIX.length());
            } else {
                constraint.name = volumeFrom;
            }
            affinityConstraints.put(constraint.name, constraint);
        }

        return affinityConstraints;
    }

    protected boolean hasOutgoingAffinities() {
        return volumesFrom != null && volumesFrom.length > 0;
    }

    @Override
    protected Map<String, HostSelection> applyAffinityConstraints(
            final PlacementHostSelectionTaskService.PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> initHostSelectionMap,
            final Map<String, HostSelection> filteredHostSelectionMap) {
        if (filteredHostSelectionMap.isEmpty()) {
            final String errMsg = String.format(
                    "No containers found for filter [%s] and value of [%s] for contextId [%s].",
                    affinityPropertyName, getAffinity(), state.contextId);
            throw new HostSelectionFilterException(errMsg, "request.volumes.from.filter.no.containers",
                    affinityPropertyName, getAffinity(), state.contextId);
        } else if (filteredHostSelectionMap.size() > 1) {
            final String errMsg = String
                    .format("Container host selection size [%s] based on filter: [links] with values: [%s] and contextId [%s] is not expected to be more than 1.",
                            filteredHostSelectionMap.size(), getAffinity(), state.contextId);
            throw new HostSelectionFilterException(errMsg, "request.volumes.from.filter.many.hosts",
                    filteredHostSelectionMap.size(), getAffinity(), state.contextId);
        }

        return filteredHostSelectionMap;
    }

    @Override
    protected void completeWhenNoContainerDescriptionsFound(
            final PlacementHostSelectionTaskService.PlacementHostSelectionTaskState state,
            final Map<String, HostSelection> filteredHostSelectionMap,
            final Map<String, DescName> containerDescLinksWithNames,
            final HostSelectionFilterCompletion callback) {
        final String errMsg = String.format(
                "No container descriptions with %s [%s].",
                affinityPropertyName, getAffinity());
        if (hasOutgoingAffinities()) {
            callback.complete(null, new HostSelectionFilterException(errMsg,
                    "request.volumes.from.filter.no.container-descriptions",
                    affinityPropertyName, getAffinity()));
        } else {
            callback.complete(filteredHostSelectionMap, null);
        }
    }
}
