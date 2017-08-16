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

package com.vmware.admiral.request.compute.allocation.filter;

import static com.vmware.photon.controller.model.adapterapi.EndpointConfigRequest.SUPPORT_DATASTORES;
import static com.vmware.photon.controller.model.constants.PhotonModelConstants.STORAGE_AVAILABLE_BYTES;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.profile.StorageProfileService.StorageProfile;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.monitoring.ResourceMetricsService.ResourceMetrics;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.DiskService.DiskState;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.StorageDescriptionService.StorageDescription;
import com.vmware.photon.controller.model.util.ClusterUtil;
import com.vmware.photon.controller.model.util.ClusterUtil.ServiceTypeCluster;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;
import com.vmware.xenon.services.common.ServiceUriPaths;

/**
 * Used to filter out hosts that do not satisfy the storage requirements.<p/>
 * Examples of storage requirements:
 * <ul>
 * <li>Host datastores should be able to fit the requested image and additional disks.</li>
 * <li>Specific datastores specified from the storage profile should be accessible from the
 * hosts.</li>
 * <li>etc.</li>
 * </ul>
 */
public class ComputeToStorageAffinityFilter implements HostSelectionFilter<FilterContext> {

    // Used to distinguish group links that point to host's datastore.
    public static final String PREFIX_DATASTORE = "datastore";

    private final ServiceHost host;
    private ComputeDescription desc;

    /**
     * Used to store results/computations between steps.
     */
    private static class Context {
        // Original filter context as passed to the {@link #filter()} method.
        FilterContext filterContext;

        // Map of selected hosts. This is an input parameter of the {@link #filter()} method.
        // It updated during the actual filtering by removing hosts that don't match storage
        // requirements.
        Map<String, HostSelection> hostSelectionMap;

        // Map of hosts ComputeStates objects matching the one from the hostSelectionMap.
        // Key - host document self link, value - ComputeState representing the host.
        Map<String, ComputeState> hosts;

        // Map with all related endpoints
        // key - endpoint document self link; value - EndpointState.
        Map<String, EndpointState> endpoints;

        // Mapping between hosts and the datastores connected to each one.
        Map<String, List<StorageDescription>> hostToDatastoresMap = new HashMap<>();

        // Flat map for all datastores connected to hosts.
        // key - datastore self link; value - StorageDescription.
        Map<String, StorageDescription> allDatastores = new HashMap<>();

        // Flat map for the available capacity for all datastores.
        // key - datastore self link; value - available capacity in bytes.
        Map<String, Double> datastoresAvailableBytes = new HashMap<>();

        // All storage profiles matching the endpointLinks collection.
        Collection<StorageProfile> storageProfiles;

        // The additional disks requested for the compute.
        Set<DiskState> computeDiskStates = Collections.emptySet();

        Context(FilterContext filterContext, Map<String, HostSelection> hostSelectionMap) {
            this.filterContext = filterContext;
            this.hostSelectionMap = hostSelectionMap;
        }
    }

    /**
     * Constructs the filter object.
     */
    public ComputeToStorageAffinityFilter(ServiceHost host, ComputeDescription desc) {
        this.host = host;
        this.desc = desc;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        // TODO [adimitrov]: Is this correct?
        return Collections.emptyMap();
    }

    @Override
    public void filter(FilterContext state, Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        Context context = new Context(state, hostSelectionMap);

        DeferredResult.completed(context)
                // Data gathering part
                .thenCompose(this::getDiskDescriptions)
                .thenCompose(this::getHosts)
                .thenCompose(this::getEndpoints)
                .thenCompose(this::getHostsDatastores)
                .thenCompose(this::getDatastoresAvailableCapacity)
                // TODO: The method below is not needed for now but illustrate the profiles
                // retrieval part.
                .thenCompose(this::getStorageProfiles)

                // Filtering part
                .thenCompose(this::filterByDatastoreAvailableCapacity)
                .whenComplete((ctx, e) -> {
                    if (e != null) {
                        callback.complete(null, e);
                    } else {
                        callback.complete(ctx.hostSelectionMap, null);
                    }
                });
    }

    // Load additional disks if attached to the ComputeDescription.
    private DeferredResult<Context> getDiskDescriptions(Context ctx) {
        AssertUtil.assertNotNull(desc, "desc");

        if (desc.diskDescLinks == null || desc.diskDescLinks.isEmpty()) {
            return DeferredResult.completed(ctx);
        }

        List<DeferredResult<DiskState>> disksDRs = desc.diskDescLinks.stream()
                .map(link -> Operation.createGet(this.host, link).setReferer(host.getUri()))
                .map(getOp -> host.sendWithDeferredResult(getOp, DiskState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(disksDRs)
                .thenApply(diskStates -> {
                    // Sort by larger disks first.
                    ctx.computeDiskStates = new TreeSet<>((o1, o2) ->
                            Long.compare(o2.capacityMBytes, o1.capacityMBytes));
                    ctx.computeDiskStates.addAll(diskStates);
                    return ctx;
                });
    }

    // Load all hosts objects.
    private DeferredResult<Context> getHosts(Context ctx) {
        AssertUtil.assertNotNull(ctx.hostSelectionMap, "ctx.hostSelectionMap");

        List<DeferredResult<ComputeState>> getHostRequests = ctx.hostSelectionMap.keySet().stream()
                .map(hostLink -> Operation.createGet(this.host, hostLink).setReferer(host.getUri()))
                .map(getOp -> host.sendWithDeferredResult(getOp, ComputeState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(getHostRequests)
                .thenApply(hosts -> {
                    ctx.hosts = hosts.stream()
                            .collect(Collectors.toMap(h -> h.documentSelfLink,
                                    Function.identity()));;
                    return ctx;
                });
    }

    private DeferredResult<Context> getEndpoints(Context ctx) {
        List<DeferredResult<EndpointState>> getEndpointRequests = ctx.hosts.values().stream()
                .map(hostState -> hostState.endpointLink)
                .distinct()
                .map(link -> Operation.createGet(this.host, link).setReferer(host.getUri()))
                .map(getOp -> host.sendWithDeferredResult(getOp, EndpointState.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(getEndpointRequests)
                .thenApply(endpoints -> {
                    ctx.endpoints = endpoints.stream().collect(
                            Collectors.toMap(e -> e.documentSelfLink,
                                    Function.identity()));

                    return ctx;
                });
    }

    // Load all datastores connected to each host.
    private DeferredResult<Context> getHostsDatastores(Context ctx) {
        AssertUtil.assertNotNull(ctx.hosts, "ctx.hosts");

        List<DeferredResult<Context>> drs = ctx.hosts.values().stream()
                .map(hostState -> getHostDatastores(ctx, hostState))
                .collect(Collectors.toList());

        return DeferredResult.allOf(drs)
                .thenApply(contexts -> ctx);
    }

    // Load all datastores connected to a specific host.
    private DeferredResult<Context> getHostDatastores(Context ctx, ComputeState hostState) {
        // Some adapters populate the datastores in the computeState's groupLinks, so try to get
        // them from there
        if (hostState.groupLinks != null) {
            List<DeferredResult<String>> dsLinksDR = hostState.groupLinks.stream()
                    .filter(link -> link.contains(ResourceGroupService.FACTORY_LINK + "/" +
                            PREFIX_DATASTORE))
                    .map(this::getResourceGroupTargetLink)
                    .collect(Collectors.toList());

            if (!dsLinksDR.isEmpty()) {
                return DeferredResult.allOf(dsLinksDR)
                        .thenCompose(this::getDatastoresByLinks)
                        .thenApply(datastores -> {
                            ctx.hostToDatastoresMap.put(hostState.documentSelfLink, datastores);

                            // Initialize ctx.allDatastores also.
                            for (StorageDescription datastore : datastores) {
                                ctx.allDatastores.put(datastore.documentSelfLink, datastore);
                            }

                            return ctx;
                        });
            }
        }

        // otherwise, query the datastores in the same region and availability zone as the host
        return getDatastoresByRegion(hostState, ctx);
    }

    // Loads the target link from the custom properties of a specified resource group.
    private DeferredResult<String> getResourceGroupTargetLink(String resourceGroupLink) {
        Operation getOp = Operation.createGet(host, resourceGroupLink).setReferer(host.getUri());
        return host.sendWithDeferredResult(getOp, ResourceGroupState.class)
                .thenApply(rg -> {
                    if (rg.customProperties != null) {
                        return rg.customProperties.get(CustomProperties.TARGET_LINK);
                    }
                    return null;
                });
    }

    // Load available capacity for each datastore.
    private DeferredResult<Context> getDatastoresAvailableCapacity(Context ctx) {
        AssertUtil.assertNotNull(ctx.allDatastores, "allDatastores");

        List<DeferredResult<Context>> drs = ctx.allDatastores.values().stream()
                .map(datastore -> getDatastoreAvailableCapacity(ctx, datastore.documentSelfLink))
                .collect(Collectors.toList());

        return DeferredResult.allOf(drs)
                .thenApply(ignored -> ctx);
    }

    // Load available capacity for a single datastore.
    // Available capacity is stored as a metric linked to the datastore.
    private DeferredResult<Context> getDatastoreAvailableCapacity(Context ctx, String selfLink) {
        Query.Builder builder = Query.Builder.create(Occurance.SHOULD_OCCUR);
        builder.addKindFieldClause(ResourceMetrics.class);
        builder.addCompositeFieldClause(ResourceMetrics.FIELD_NAME_CUSTOM_PROPERTIES,
                ResourceMetrics.PROPERTY_RESOURCE_LINK, selfLink);
        builder.addRangeClause(QueryTask.QuerySpecification.buildCompositeFieldName(
                ResourceMetrics.FIELD_NAME_ENTRIES, STORAGE_AVAILABLE_BYTES),
                QueryTask.NumericRange.createDoubleRange(0.0, Double.MAX_VALUE, true, true));

        QueryTask qTask = QueryTask.Builder.createDirectTask()
                .addOption(QueryOption.SORT)
                .addOption(QueryOption.TOP_RESULTS)
                .addOption(QueryOption.INCLUDE_ALL_VERSIONS)
                .addOption(QueryOption.EXPAND_CONTENT)
                .orderDescending(ServiceDocument.FIELD_NAME_SELF_LINK,
                        ServiceDocumentDescription.TypeName.STRING)
                .setResultLimit(1)
                .setQuery(builder.build()).build();
        qTask.documentExpirationTimeMicros =
                Utils.getNowMicrosUtc() + QueryUtils.TEN_MINUTES_IN_MICROS;

        URI postUri = UriUtils
                .buildUri(ClusterUtil.getClusterUri(host, ServiceTypeCluster.METRIC_SERVICE),
                        ServiceUriPaths.CORE_LOCAL_QUERY_TASKS);
        return host.sendWithDeferredResult(
                Operation.createPost(postUri).setReferer(host.getUri()).setBody(qTask))
                .thenApply(operation -> {
                            QueryTask body = operation.getBody(QueryTask.class);
                            if (body.results.documentCount > 0) {
                                ResourceMetrics metric = Utils.fromJson(
                                        body.results.documents.values().iterator().next(),
                                        ResourceMetrics.class);
                                Double availableBytes = metric.entries
                                        .getOrDefault(STORAGE_AVAILABLE_BYTES, Double.MAX_VALUE);
                                ctx.datastoresAvailableBytes.put(selfLink, availableBytes);
                            }

                            return ctx;
                        }
                );
    }

    // Load a list of datastores by their document self links.
    private DeferredResult<List<StorageDescription>> getDatastoresByLinks(List<String> dsLinks) {
        List<DeferredResult<StorageDescription>> datastoresDR = dsLinks.stream()
                .filter(Objects::nonNull)
                .map(link -> Operation.createGet(host, link).setReferer(host.getUri()))
                .map(getOp -> host.sendWithDeferredResult(getOp, StorageDescription.class))
                .collect(Collectors.toList());

        return DeferredResult.allOf(datastoresDR);
    }

    // Load all datastores available in the region and zone of the specified host.
    private DeferredResult<Context> getDatastoresByRegion(ComputeState hostState,
            Context ctx) {

        Query.Builder builder = Query.Builder.create().addKindFieldClause(StorageDescription.class);
        // TODO https://jira-hzn.eng.vmware.com/browse/VCOM-1299
        // remove the if when the vMhost.regionId becomes mandatory
        if (hostState.regionId != null) {
            builder.addFieldClause(StorageDescription.FIELD_NAME_REGION_ID, hostState.regionId);
        }

        Query query = builder.build();

        QueryUtils.QueryByPages<StorageDescription> queryByPages = new QueryUtils.QueryByPages(host,
                query,
                StorageDescription.class, hostState.tenantLinks, hostState.endpointLink);

        return queryByPages
                .collectDocuments(Collectors.toList())
                .thenApply(datastores -> {
                    ctx.hostToDatastoresMap.put(hostState.documentSelfLink, datastores);

                    // Initialize ctx.allDatastores also.
                    for (StorageDescription datastore : datastores) {
                        ctx.allDatastores.put(datastore.documentSelfLink, datastore);
                    }

                    return ctx;
                });
    }

    // Load storage profiles based on the hosts endpoint links.
    private DeferredResult<Context> getStorageProfiles(Context ctx) {
        List<String> endpointLinks = ctx.hosts.values().stream()
                .map(hostState -> hostState.endpointLink)
                .distinct()
                .collect(Collectors.toList());

        Query.Builder builder = Query.Builder.create().addKindFieldClause(StorageProfile.class);
        builder.addInClause(StorageProfile.FIELD_NAME_ENDPOINT_LINK, endpointLinks);

        Query query = builder.build();

        QueryUtils.QueryByPages<StorageProfile> queryByPages =
                new QueryUtils.QueryByPages(host, query, StorageProfile.class, desc.tenantLinks);

        return queryByPages.collectDocuments(Collectors.toList())
                .thenApply(storageProfiles -> {
                    ctx.storageProfiles = storageProfiles;
                    return ctx;
                });
    }

    // Filter hosts selection by datastores available capacity.
    private DeferredResult<Context> filterByDatastoreAvailableCapacity(Context ctx) {
        List<String> matchingHostSelfLinks = ctx.hosts.values().stream()
                .filter(host -> hasEnoughDatastoreAvailableCapacity(ctx,
                        host.documentSelfLink))
                .map(hostState -> hostState.documentSelfLink)
                .collect(Collectors.toList());

        ctx.hosts.values().forEach(hostState -> {
            if (!matchingHostSelfLinks.contains(hostState.documentSelfLink)) {
                ctx.hostSelectionMap.remove(hostState.documentSelfLink);
            }
        });

        return DeferredResult.completed(ctx);
    }

    // Starts with the largest disks and tries to fit them in the datastores with least available
    // capacity.
    private boolean hasEnoughDatastoreAvailableCapacity(Context ctx, String hostSelfLink) {

        ComputeState host = ctx.hosts.get(hostSelfLink);
        AssertUtil.assertNotNull(host, "host");

        EndpointState endpoint = ctx.endpoints.get(host.endpointLink);
        AssertUtil.assertNotNull(endpoint, "endpoint");

        Boolean supportsDatastores = Boolean.valueOf(endpoint.endpointProperties.getOrDefault(
                SUPPORT_DATASTORES,
                Boolean.FALSE.toString()));

        if (supportsDatastores.booleanValue() == false) {
            // The endpoint doesn't support datastores concept.
            return true;
        }

        List<StorageDescription> datastores = ctx.hostToDatastoresMap.get(hostSelfLink);
        // Sort datastores so the most filled one is on top.
        datastores.sort((o1, o2) -> {
            Double freeSpace1 = ctx.datastoresAvailableBytes.getOrDefault(o1.documentSelfLink,
                    Double.MAX_VALUE);
            Double freeSpace2 = ctx.datastoresAvailableBytes.getOrDefault(o2.documentSelfLink,
                    Double.MAX_VALUE);
            return Double.compare(freeSpace1, freeSpace2);
        });

        // TODO [adimitrov]: image disks should also be included in the algorithm.
        // Currently there is no such information enumerated.
        Map<String, Double> actualAvailability = new HashMap<>();
        for (DiskState disk : ctx.computeDiskStates) {
            boolean datastoreFound = false;
            for (StorageDescription datastore : datastores) {
                // Get current datastore available MBs.
                Double availability = actualAvailability.getOrDefault(
                        datastore.documentSelfLink,
                        ctx.datastoresAvailableBytes.get(datastore.documentSelfLink));

                // No available capacity retrieved -> assume unlimited capacity.
                if (availability == null) {
                    availability = Double.MAX_VALUE;
                }

                if (disk.capacityMBytes * 1024 < availability) {
                    // The disk can fit in this datastore.
                    datastoreFound = true;

                    // Update actual available capacity.
                    actualAvailability.put(datastore.documentSelfLink, availability - disk
                            .capacityMBytes * 1024);
                }
            }

            if (!datastoreFound) {
                this.host.log(Level.INFO, "Host with name [%s] and self link [%s] does not "
                        + "have datastores free capacity to fit a disk with size [%d] MB.",
                        host.name,
                        host.documentSelfLink,
                        disk.capacityMBytes);
                return false;
            }
        }

        return true;
    }
}
