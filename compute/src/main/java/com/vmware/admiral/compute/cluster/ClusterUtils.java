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

package com.vmware.admiral.compute.cluster;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.PlacementZoneUtil;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterStatus;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterType;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

public class ClusterUtils {

    public static ClusterStatus computeToClusterStatus(ComputeState computeState) {
        switch (computeState.powerState) {
        case ON:
            return ClusterStatus.ON;
        case OFF:
            return ClusterStatus.OFF;
        case UNKNOWN:
            return ClusterStatus.WARNING;
        case SUSPEND:
            return ClusterStatus.DISABLED;
        default:
            return ClusterStatus.WARNING;
        }
    }

    public static DeferredResult<List<ComputeState>> getHostsWihtinPlacementZone(
            String resourcePoolLink, String projectLink, ServiceHost host) {
        if (resourcePoolLink == null) {
            return null;
        }

        Query.Builder queryBuilder = Query.Builder.create()
                .addKindFieldClause(ComputeState.class)
                .addFieldClause(ComputeState.FIELD_NAME_RESOURCE_POOL_LINK, resourcePoolLink)
                .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                        ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true");

        if (projectLink != null && !projectLink.isEmpty()) {
            queryBuilder.addInCollectionItemClause(ComputeState.FIELD_NAME_TENANT_LINKS,
                    Collections.singletonList(projectLink), Occurance.MUST_OCCUR);
        }

        Query query = queryBuilder.build();

        QueryUtils.QueryByPages<ComputeState> queryHelper = new QueryUtils.QueryByPages<>(
                host, query, ComputeState.class, null);
        return queryHelper.collectDocuments(Collectors.toList());
    }

    public static void deletePZ(String pathPZId, Operation delete, ServiceHost host) {
        host.sendWithDeferredResult(
                Operation.createDelete(UriUtils.buildUri(host, pathPZId))
                        .setBody(new ElasticPlacementZoneConfigurationState())
                        .setReferer(host.getUri()),
                ElasticPlacementZoneConfigurationState.class)
                        .exceptionally(f -> {
                            if (f instanceof ServiceNotFoundException) {
                                return null;
                            } else {
                                throw new CompletionException(f);
                            }
                        })
                        .whenCompleteNotify(delete);
    }

    public static ClusterDto placementZoneAndItsHostsToClusterDto(
            ResourcePoolState resourcePoolState, List<ComputeState> computeStates) {

        ClusterDto ePZClusterDto = new ClusterDto();
        ePZClusterDto.documentSelfLink = UriUtils.buildUriPath(
                ClusterService.SELF_LINK,
                Service.getId(resourcePoolState.documentSelfLink));
        ePZClusterDto.name = resourcePoolState.name;
        ePZClusterDto.clusterCreationTimeMicros = PropertyUtils
                .getPropertyLong(resourcePoolState.customProperties,
                        ClusterService.CLUSTER_CREATION_TIME_MICROS_CUSTOM_PROP)
                .orElse(0L);
        ePZClusterDto.details = PropertyUtils.getPropertyString(resourcePoolState.customProperties,
                ClusterService.CLUSTER_DETAILS_CUSTOM_PROP).orElse("");
        ePZClusterDto.totalMemory = resourcePoolState.maxMemoryBytes == null ? 0
                : resourcePoolState.maxMemoryBytes;
        ePZClusterDto.memoryUsage = ePZClusterDto.totalMemory - PropertyUtils.getPropertyLong(
                resourcePoolState.customProperties,
                ContainerHostDataCollectionService.RESOURCE_POOL_AVAILABLE_MEMORY_CUSTOM_PROP)
                .orElse(0L);
        ePZClusterDto.cpuUsage = PropertyUtils.getPropertyDouble(
                resourcePoolState.customProperties,
                ContainerHostDataCollectionService.RESOURCE_POOL_CPU_USAGE_CUSTOM_PROP)
                .orElse(0.0);
        if (computeStates == null || computeStates.isEmpty()) {
            ePZClusterDto.type = PlacementZoneUtil
                    .isSchedulerPlacementZone(resourcePoolState)
                            ? ClusterType.VCH
                            : ClusterType.DOCKER;
            ePZClusterDto.status = ClusterStatus.DISABLED;
            ePZClusterDto.containerCount = 0;
            ePZClusterDto.totalCpu = 0.0;
        } else {
            if (PlacementZoneUtil.isSchedulerPlacementZone(resourcePoolState)) {
                ePZClusterDto.type = ClusterType.VCH;
                ePZClusterDto.address = computeStates.get(0).address;
                ePZClusterDto.totalCpu = PropertyUtils.getPropertyDouble(
                        computeStates.get(0).customProperties,
                        ContainerHostService.DOCKER_HOST_NUM_CORES_PROP_NAME)
                        .orElse(0.0);
            } else {
                ePZClusterDto.type = ClusterType.DOCKER;
                //not available for now
                ePZClusterDto.totalCpu = 0.0;
            }
            int containerCounter = 0;
            for (ComputeState computeState : computeStates) {
                if (!computeState.powerState.equals(computeStates.get(0).powerState)) {
                    ePZClusterDto.status = ClusterStatus.WARNING;
                }
                ePZClusterDto.nodeLinks.add(computeState.documentSelfLink);
                containerCounter += PropertyUtils.getPropertyInteger(
                        computeState.customProperties,
                        ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME)
                        .orElse(0);
            }
            if (ePZClusterDto.status == null) {
                ePZClusterDto.status = ClusterUtils.computeToClusterStatus(computeStates.get(0));
            }
            ePZClusterDto.containerCount = containerCounter;
        }
        return ePZClusterDto;
    }

    public static boolean hasPlacementZone(ComputeState hostState) {
        return hostState != null
                && hostState.resourcePoolLink != null
                && !hostState.resourcePoolLink.isEmpty();
    }

    public static String toClusterSelfLink(String placementZoneLink) {
        if (placementZoneLink == null) {
            return null;
        }

        if (placementZoneLink.startsWith(ResourcePoolService.FACTORY_LINK)) {
            return UriUtils.buildUriPath(ClusterService.SELF_LINK,
                    Service.getId(placementZoneLink));
        }

        throw new IllegalArgumentException(
                String.format("'%s' is not a placement zone link", placementZoneLink));
    }
}
