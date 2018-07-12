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

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_STATUS_REMOVING_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_STATUS_RESIZING_PROP_NAME;
import static com.vmware.admiral.compute.ContainerHostUtil.isKubernetesHost;
import static com.vmware.admiral.compute.ContainerHostUtil.isVicHost;
import static com.vmware.admiral.compute.cluster.ClusterService.INITIAL_CLUSTER_STATUS_PROP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import com.vmware.admiral.common.util.ConfigurationUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ComputeConstants;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.PlacementZoneUtil;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterDto;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterStatus;
import com.vmware.admiral.compute.cluster.ClusterService.ClusterType;
import com.vmware.admiral.compute.container.ContainerHostDataCollectionService;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.ResourcePoolService;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.tasks.helpers.ResourcePoolQueryHelper;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.ODataQueryVisitor;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceDocumentDescription.Builder;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceHost.ServiceNotFoundException;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTaskUtils;
import com.vmware.xenon.services.common.ServiceUriPaths;

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

    public static DeferredResult<List<ComputeState>> getHostsWithinPlacementZone(
            String resourcePoolLink, String projectLink, ServiceHost host) {
        return getHostsWithinPlacementZone(resourcePoolLink, projectLink, null, host);
    }

    public static DeferredResult<List<ComputeState>> getHostsWithinPlacementZone(
            String resourcePoolLink, String projectLink, Operation get, ServiceHost host) {
        if (resourcePoolLink == null) {
            return null;
        }
        DeferredResult<List<ComputeState>> result = new DeferredResult<>();

        ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.createForResourcePool(host,
                resourcePoolLink);
        helper.setExpandComputes(true);
        helper.setAdditionalQueryClausesProvider(qb -> {
            qb.addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                    ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
        });

        if (projectLink != null && !projectLink.isEmpty()) {
            helper.setAdditionalQueryClausesProvider(qb -> {
                qb.addInCollectionItemClause(ComputeState.FIELD_NAME_TENANT_LINKS,
                        Collections.singletonList(projectLink), Occurance.MUST_OCCUR);
            });
        }

        if (get != null) {
            Map<String, String> query = UriUtils.parseUriQueryParams(get.getUri());

            String hostsFilter = query.getOrDefault(ClusterService.HOSTS_FILTER_QUERY_PARAM, null);

            if (ConfigurationUtil.isEmbedded()) {
                String rawCustomOptions = query.get(ClusterService.CUSTOM_OPTIONS_QUERY_PARAM);

                if (!Strings.isNullOrEmpty(rawCustomOptions)) {
                    rawCustomOptions = rawCustomOptions.replaceAll("[{}]", " ").trim();
                    if (!Strings.isNullOrEmpty(rawCustomOptions)) {
                        Map<String, String> properties = Splitter.on(",").withKeyValueSeparator("=")
                                .split(rawCustomOptions);
                        hostsFilter = properties.get(ClusterService.HOSTS_FILTER_QUERY_PARAM);
                    }
                }
            }

            if (hostsFilter != null) {
                ServiceDocumentDescription desc = Builder.create().buildDescription(
                        ComputeState.class);

                Set<String> expandedQueryPropertyNames = QueryTaskUtils
                        .getExpandedQueryPropertyNames(desc);
                Query q = new ODataQueryVisitor(expandedQueryPropertyNames).toQuery(hostsFilter);
                if (q != null) {
                    helper.setAdditionalQueryClausesProvider(qb -> qb.addClause(q));
                }
            }
        }

        helper.query((qr) -> {
            if (qr.error != null) {
                result.fail(qr.error);
            } else {
                result.complete(new ArrayList<>(qr.computesByLink.values()));
            }
        });

        return result;
    }

    public static void getHostsWithinPlacementZone(
            Operation get, ServiceHost host) {

        String clusterId = UriUtils.parseUriPathSegments(get.getUri(),
                ClusterService.CLUSTER_PATH_SEGMENT_TEMPLATE)
                .get(ClusterService.CLUSTER_ID_PATH_SEGMENT);
        String resourcePoolLink = UriUtils.buildUriPath(
                ResourcePoolService.FACTORY_LINK, clusterId);
        String projectLink = OperationUtil.extractProjectFromHeader(get);

        if (resourcePoolLink == null) {
            return;
        }

        Operation getRpOp = Operation.createGet(host, resourcePoolLink).setReferer(host.getUri());
        host.sendWithDeferredResult(getRpOp, ResourcePoolState.class)
                .thenCompose(currentRpState -> {
                    Query.Builder queryBuilder = Query.Builder.create()
                            .addClause(currentRpState.query)
                            .addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                                    ComputeConstants.COMPUTE_CONTAINER_HOST_PROP_NAME, "true");
                    if (projectLink != null && !projectLink.isEmpty()) {
                        queryBuilder.addInCollectionItemClause(ComputeState.FIELD_NAME_TENANT_LINKS,
                                Collections.singletonList(projectLink), Occurance.MUST_OCCUR);
                    }

                    String filter = UriUtils.getODataFilterParamValue(get.getUri());
                    if (filter != null) {
                        ServiceDocumentDescription desc = Builder.create().buildDescription(
                                ComputeState.class);

                        Set<String> expandedQueryPropertyNames = QueryTaskUtils
                                .getExpandedQueryPropertyNames(desc);
                        Query q = new ODataQueryVisitor(expandedQueryPropertyNames).toQuery(filter);
                        if (q != null) {
                            queryBuilder.addClause(q);
                        }
                    }
                    Query query = queryBuilder.build();
                    QueryTask queryTask = QueryUtil.buildQuery(ComputeState.class, true, query);
                    QueryUtil.addExpandOption(queryTask);

                    Integer limit = UriUtils.getODataLimitParamValue(get.getUri());
                    if (limit != null && limit > 0) {
                        queryTask.querySpec.resultLimit = limit;
                    } else {
                        queryTask.querySpec.resultLimit =
                                ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;
                    }
                    queryTask.documentExpirationTimeMicros = ServiceDocumentQuery
                            .getDefaultQueryExpiration();

                    return host.sendWithDeferredResult(Operation
                            .createPost(UriUtils.buildUri(host, ServiceUriPaths.CORE_QUERY_TASKS))
                            .setBody(queryTask)
                            .setReferer(host.getUri()), QueryTask.class);

                }).thenCompose(qrt -> {
                    if (qrt.results.nextPageLink != null) {
                        return host.sendWithDeferredResult(Operation
                                .createGet(UriUtils.buildUri(host, qrt.results.nextPageLink))
                                .setReferer(host.getUri()), QueryTask.class);
                    }
                    return DeferredResult.completed(qrt);
                }).thenAccept(queryPage -> {
                    get.setBody(queryPage.results);
                    get.complete();
                }).exceptionally(ex -> {
                    get.fail(ex);
                    return null;
                });
    }

    public static void deletePlacementZoneAndPlacement(String pathPZLink, String resourcePoolLink,
            Operation delete, ServiceHost host) {
        host.sendWithDeferredResult(
                Operation.createDelete(UriUtils.buildUri(host, pathPZLink))
                        .setBody(new ElasticPlacementZoneConfigurationState())
                        .setReferer(host.getUri()),
                ElasticPlacementZoneConfigurationState.class)
                .thenCompose(epz -> deletePlacementWithResourcePoolLink(resourcePoolLink, host))
                .exceptionally(f -> {
                    if (f instanceof ServiceNotFoundException) {
                        return null;
                    } else {
                        throw new CompletionException(f);
                    }
                })
                .whenCompleteNotify(delete);
    }

    public static DeferredResult<Operation> deletePlacementWithResourcePoolLink(
            String resourcePoolLink, ServiceHost host) {

        Query.Builder queryBuilder = Query.Builder.create()
                .addFieldClause(GroupResourcePlacementState.FIELD_NAME_RESOURCE_POOL_LINK,
                        resourcePoolLink);

        Query query = queryBuilder.build();
        QueryTask queryTask = QueryUtil.buildQuery(GroupResourcePlacementState.class, true, query);

        return host.sendWithDeferredResult(Operation
                .createPost(UriUtils.buildUri(host, ServiceUriPaths.CORE_QUERY_TASKS))
                .setBody(queryTask)
                .setReferer(host.getUri()), QueryTask.class)
                .thenCompose(qr -> {
                    if (qr == null || qr.results == null
                            || qr.results.documentLinks == null
                            || qr.results.documentLinks.size() != 1) {
                        throw new ServiceNotFoundException(
                                "Group placement not found for cluster id: "
                                        + Service.getId(resourcePoolLink));
                    }
                    return host.sendWithDeferredResult(
                            Operation.createDelete(
                                    UriUtils.buildUri(host, qr.results.documentLinks.get(0)))
                                    .setBody(new ElasticPlacementZoneConfigurationState())
                                    .setReferer(host.getUri()));
                });

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
        ClusterType type = PlacementZoneUtil.isSchedulerPlacementZone(resourcePoolState)
                ? ClusterType.VCH
                : ClusterType.DOCKER;
        ePZClusterDto.type = ClusterType
                .valueOf(PropertyUtils.getPropertyString(resourcePoolState.customProperties,
                        ClusterService.CLUSTER_TYPE_CUSTOM_PROP)
                        .orElse(type.toString()));
        if (computeStates == null || computeStates.isEmpty()) {
            ePZClusterDto.status = getInitialStatus(resourcePoolState);
            if (ePZClusterDto.status == null) {
                ePZClusterDto.status = ClusterStatus.DISABLED;
            }
            ePZClusterDto.containerCount = 0;
            ePZClusterDto.systemContainersCount = 0;
            ePZClusterDto.totalCpu = 0.0;
        } else {
            if (PlacementZoneUtil.isSchedulerPlacementZone(resourcePoolState)) {
                ePZClusterDto.address = computeStates.get(0).address;
                ePZClusterDto.totalCpu = PropertyUtils.getPropertyDouble(
                        computeStates.get(0).customProperties,
                        ContainerHostService.DOCKER_HOST_NUM_CORES_PROP_NAME)
                        .orElse(0.0);
            } else {
                //not available for now
                ePZClusterDto.totalCpu = 0.0;
            }

            if (isSingleHostSupportedCluster(ePZClusterDto) && computeStates.size() == 1) {
                Map<String, String> customProperties = computeStates.get(0).customProperties;
                ePZClusterDto.publicAddress = customProperties
                        .get(ContainerHostService.HOST_PUBLIC_ADDRESS_PROP_NAME);
            }

            int containerCounter = 0;
            int systemContainerCounter = 0;
            ePZClusterDto.nodes = new HashMap<>();
            for (ComputeState computeState : computeStates) {
                if (computeState.powerState != computeStates.get(0).powerState) {
                    ePZClusterDto.status = ClusterStatus.WARNING;
                }
                ePZClusterDto.nodeLinks.add(computeState.documentSelfLink);
                ePZClusterDto.nodes.put(computeState.documentSelfLink,
                        transformComputeForExpandedCluster(computeState));
                containerCounter += PropertyUtils.getPropertyInteger(
                        computeState.customProperties,
                        ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME)
                        .orElse(0);
                systemContainerCounter += PropertyUtils.getPropertyInteger(
                        computeState.customProperties,
                        ContainerHostService.NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME)
                        .orElse(0);
            }
            if (ePZClusterDto.status == null) {
                if (isPKSClusterBeingResized(computeStates.get(0))) {
                    ePZClusterDto.status = ClusterStatus.RESIZING;
                } else if (isPKSClusterBeingRemoved(computeStates.get(0))) {
                    ePZClusterDto.status = ClusterStatus.REMOVING;
                } else {
                    ePZClusterDto.status = ClusterUtils.computeToClusterStatus(computeStates.get(0));
                }
            }

            ePZClusterDto.containerCount = containerCounter;
            ePZClusterDto.systemContainersCount = systemContainerCounter;
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

    public static ComputeState transformComputeForExpandedCluster(ComputeState state) {
        // we would like to have access to the whole object in order to update it
        if (isVicHost(state) || isKubernetesHost(state)) {
            return state;
        }

        ComputeState outState = new ComputeState();
        // Cast before passing the compute state in order to use the
        // copyTo method with ServiceDocument instead of ResourceState.
        state.copyTo((ServiceDocument) outState);
        outState.address = state.address;
        outState.powerState = state.powerState;
        outState.name = state.name;
        return outState;
    }

    /**
     * Checks whether the specified cluster supports only a single host (e.g. is a VCH cluster).
     * Note that this is different from supporting multiple hosts but having only a single one added
     * to the cluster. This method will return <code>true</code> if and only if the cluster has
     * capacity for exactly one host regardless of the current state of the cluster (empty or has a
     * single host).
     *
     * @param cluster
     *            a {@link ClusterDto} with its type set
     * @return whether this cluster supports only a single host
     * @see ClusterType
     */
    public static boolean isSingleHostSupportedCluster(ClusterDto cluster) {
        return cluster != null && cluster.type == ClusterType.VCH;
    }

    /**
     * Checks if the cluster is of the specified type. The type filter designates
     * the cluster type. It can be preceded by "!" which indicates negation.
     */
    public static boolean filterByType(ClusterDto cluster, String typeFilter) {
        if (typeFilter == null) {
            return true;
        }

        boolean isFilterExclusive;
        if (isFilterExclusive = typeFilter.startsWith("!")) {
            typeFilter = typeFilter.substring(1);
        }
        ClusterType filter = ClusterType.valueOf(typeFilter);

        return isFilterExclusive ^ cluster.type == filter;
    }

    private static ClusterStatus getInitialStatus(ResourcePoolState resourcePoolState) {
        try {
            String s = resourcePoolState.customProperties.get(INITIAL_CLUSTER_STATUS_PROP);
            return ClusterStatus.valueOf(s);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean isPKSClusterBeingResized(ComputeState host) {
        return KubernetesUtil.isPKSManagedHost(host)
                && host.customProperties != null
                && host.customProperties.containsKey(PKS_CLUSTER_STATUS_RESIZING_PROP_NAME);
    }

    private static boolean isPKSClusterBeingRemoved(ComputeState host) {
        return KubernetesUtil.isPKSManagedHost(host)
                && host.customProperties != null
                && host.customProperties.containsKey(PKS_CLUSTER_STATUS_REMOVING_PROP_NAME);
    }
}
