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

package com.vmware.admiral.request.compute.allocation.filter;

import static com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.NetworkType.ISOLATED;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide affinity and network name
 * resolution in case the {@link ComputeDescription} specifies <code>networks</code> property.
 */
public class ComputeToNetworkAffinityHostFilter implements HostSelectionFilter<FilterContext> {
    private final ServiceHost host;
    @SuppressWarnings("unused")
    private List<String> tenantLinks;
    private ComputeDescription desc;

    public static final String PREFIX_NETWORK = "network";

    private Map<String, AffinityConstraint> affinityConstraints;

    /**
     * Used to store results/computations between steps. Make sure to not use HashMaps and HashSets
     * so that things are as deterministic as possible
     */
    private static class InternalContext {
        FilterContext filterContext;
        Map<String, HostSelection> hostSelectionMap;
        Map<ComputeNetwork, ComputeNetworkDescription> contextNetworks;
        Set<ComputeState> hosts;
        Map<String, Set<ComputeState>> hostGroups;
    }

    public ComputeToNetworkAffinityHostFilter(ServiceHost host, ComputeDescription desc) {
        this.host = host;
        this.desc = desc;
        this.tenantLinks = desc.tenantLinks;
    }

    @Override
    public boolean isActive() {
        return desc.networkInterfaceDescLinks != null && desc.networkInterfaceDescLinks.size() > 0;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        if (!isActive()) {
            return Collections.emptyMap();
        }

        if (affinityConstraints != null) {
            return affinityConstraints;
        }

        CompletableFuture<Map<String, DescName>> f = new CompletableFuture<>();
        loadNicDescs((m, t) -> {
            if (t != null) {
                f.completeExceptionally(t);
            } else {
                host.log(Level.INFO, "Network affinity map component: %s [%s].", desc.name, m);
                f.complete(m);
            }
        });
        try {
            affinityConstraints = f.get(120, TimeUnit.SECONDS).entrySet().stream().collect(
                    Collectors.toMap(p -> p.getKey(), p -> new AffinityConstraint(p.getKey())));
        } catch (TimeoutException e) {
            host.log(Level.WARNING, "Timeout loading network definitions.");
            affinityConstraints = Collections.emptyMap();
        } catch (Exception e) {
            host.log(Level.WARNING, "Error loading network definitions, reason:%s", e.getMessage());
            affinityConstraints = Collections.emptyMap();
        }

        return affinityConstraints;
    }

    @Override
    public void filter(final FilterContext filterContext,
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {

        if (!isActive()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        InternalContext internalContext = new InternalContext();
        internalContext.filterContext = filterContext;
        internalContext.hostSelectionMap = hostSelectionMap;

        getContextNetworks(internalContext).thenCompose(context -> {
            // if there are no networks in the blueprint, do nothing
            if (context.contextNetworks.isEmpty()) {
                host.log(Level.INFO,
                        "Filter: %s, contextId: %s Skipping filter because there are no networks found. Returning the full set of hosts",
                        getClass().getName(), filterContext.contextId);
                return DeferredResult.completed(context.hostSelectionMap);
            } else {
                return
                        // get the compute states
                        getHosts(context)
                        // group them by subnet/network
                        .thenCompose(this::groupHostsByConnectivity)
                        // pick a (group of) hosts
                        .thenApply(this::pickHosts);
            }
        }).whenComplete((result, e) -> {
            if (e != null) {
                callback.complete(null, e);
            } else {
                callback.complete(result, null);
            }
        });
    }

    /**
     * Get the networks and their descriptions part of the blueprint
     */
    private DeferredResult<InternalContext> getContextNetworks(InternalContext internalContext) {

        DeferredResult<Map<ComputeNetwork, ComputeNetworkDescription>> deferredResult = new DeferredResult<>();

        Query.Builder builder = Query.Builder.create().addKindFieldClause(ComputeNetwork.class);
        builder.addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                FIELD_NAME_CONTEXT_ID_KEY, internalContext.filterContext.contextId);

        QueryUtils.QueryByPages<ComputeNetwork> query = new QueryUtils.QueryByPages<>(host,
                builder.build(), ComputeNetwork.class, tenantLinks);

        List<ComputeNetwork> computeNetworks = new ArrayList<>();
        query.queryDocuments(ns -> computeNetworks.add(ns)).whenComplete((v, e) -> {
            if (e != null) {
                deferredResult.fail(e);
            }
        }).thenAccept(__ -> {
            Map<ComputeNetwork, ComputeNetworkDescription> result = new HashMap<>();

            List<DeferredResult<Void>> getDescriptions = computeNetworks.stream().map(cn -> {
                Operation op = Operation.createGet(this.host, cn.descriptionLink)
                        .setReferer(host.getUri());

                return host.sendWithDeferredResult(op, ComputeNetworkDescription.class)
                        .thenAccept(d -> result.put(cn, d));
            }).collect(Collectors.toList());

            DeferredResult.allOf(getDescriptions).whenComplete((___, e) -> {
                if (e == null) {
                    deferredResult.complete(result);
                } else {
                    deferredResult.fail(e);
                }
            });
        });

        return deferredResult.thenApply(networks -> {
            internalContext.contextNetworks = networks;
            return internalContext;
        });
    }

    private DeferredResult<InternalContext> getHosts(InternalContext context) {
        List<DeferredResult<ComputeState>> getHostRequests = context.hostSelectionMap.keySet()
                .stream().map(hostLink -> getHost(hostLink)).collect(Collectors.toList());

        return DeferredResult.allOf(getHostRequests).thenApply(hosts -> {


            TreeSet<ComputeState> computeStates = new TreeSet<>((c1, c2) -> {
                return String.CASE_INSENSITIVE_ORDER
                        .compare(c1.documentSelfLink, c2.documentSelfLink);
            });
            computeStates.addAll(hosts);
            context.hosts = computeStates;
            return context;
        });
    }

    /**
     * Group hosts by connectivity. For each host that the filter is given get the subnets/networks
     * that it is connected to. Then "reverse" the mapping i.e for each subnet/network get the hosts
     */
    private DeferredResult<InternalContext> groupHostsByConnectivity(InternalContext internalContext) {

        List<DeferredResult<Pair<ComputeState, Set<String>>>> getNetworksRequests = internalContext.hosts
                .stream()
                .map(computeState -> {
                    return getNetworkLinks(computeState)
                            .thenApply(networkLinks -> Pair.of(computeState, networkLinks));
                }).collect(Collectors.toList());

        return DeferredResult.allOf(getNetworksRequests).thenApply(pairs -> {

            Map<String, Set<ComputeState>> result = new TreeMap<>();

            for (Pair<ComputeState, Set<String>> pair : pairs) {
                ComputeState computeState = pair.left;
                Set<String> networks = pair.right;

                for (String networkLink : networks) {
                    result.putIfAbsent(networkLink, new TreeSet<ComputeState>(
                            (l, r) -> String.CASE_INSENSITIVE_ORDER
                                    .compare(l.documentSelfLink, r.documentSelfLink)));
                    result.get(networkLink).add(computeState);
                }
            }

            return result;
        }).thenApply(groups -> {
            internalContext.hostGroups = groups;
            return internalContext;
        });
    }

    private Map<String, HostSelection> pickHosts(InternalContext context) {
        // Pick the largest group of hosts
        Map<String, Set<ComputeState>> hostGroups = context.hostGroups;

        // workaround for isolated use case for now
        if (context.contextNetworks.values().stream().filter(d -> d.networkType == ISOLATED).count()
                > 0 && context.hostGroups.isEmpty()) {
            return context.hostSelectionMap;
        }

        List<Set<ComputeState>> groups = new ArrayList<>(hostGroups.values());

        //sort descending
        groups.sort((g1, g2) -> Integer.compare(g2.size(), g1.size()));

        int max = groups.get(0).size();

        List<Set<ComputeState>> largestGroups = groups.stream().filter(g -> g.size() == max)
                .collect(Collectors.toList());

        /**
         * If there is more than one largest group, pick one based on the hash of the context id
         * We want to make sure that when the conditions are the same we pick the same group for
         * each compute description that is part of this request
         */

        int groupIdx = Math.abs(context.filterContext.contextId.hashCode() % largestGroups.size());

        Set<ComputeState> hostGroup = largestGroups.get(groupIdx);

        Set<String> filteredHostLinks = hostGroup.stream()
                .map(cs -> cs.documentSelfLink)
                .collect(Collectors.toSet());

        Map<String, HostSelection> result = new TreeMap<>(context.hostSelectionMap);
        result.keySet().retainAll(filteredHostLinks);

        return result;
    }

    private DeferredResult<ComputeState> getHost(String link) {
        Operation get = Operation.createGet(this.host, link).setReferer(host.getUri());

        return host.sendWithDeferredResult(get, ComputeState.class);
    }

    private DeferredResult<Set<String>> getSubnetsForHost(ComputeState vMhost) {
        DeferredResult<Set<String>> subnetLinksDr = new DeferredResult<>();

        Query.Builder builder = Query.Builder.create().addKindFieldClause(SubnetState.class);
        // TODO https://jira-hzn.eng.vmware.com/browse/VCOM-1299
        // remove the if when the vMhost.regionId becomes mandatory
        if (vMhost.regionId != null) {
            builder.addFieldClause(SubnetState.FIELD_NAME_REGION_ID, vMhost.regionId);
        }

        if (vMhost.zoneId != null) {
            builder.addFieldClause(SubnetState.FIELD_NAME_ZONE_ID, vMhost.zoneId);
        }

        Query query = builder.build();


        QueryUtils.QueryByPages<SubnetState> queryByPages = new QueryUtils.QueryByPages(host, query,
                SubnetState.class, vMhost.tenantLinks, vMhost.endpointLink);

        Set<String> subnetLinks = new TreeSet<>();
        queryByPages.queryLinks(subnetLink -> {
            subnetLinks.add(subnetLink);
        }).whenComplete(((aVoid, throwable) -> {
            if (throwable != null) {
                subnetLinksDr.fail(throwable);
            } else {
                subnetLinksDr.complete(subnetLinks);
            }
        }));

        return subnetLinksDr;
    }

    private final void loadNicDescs(BiConsumer<Map<String, DescName>, Throwable> callback) {
        Query query = Query.Builder.create()
                .addKindFieldClause(NetworkInterfaceDescription.class)
                .addInClause(ResourceState.FIELD_NAME_SELF_LINK, desc.networkInterfaceDescLinks)
                .build();

        QueryTop<NetworkInterfaceDescription> queryNids = new QueryTop<>(host, query,
                NetworkInterfaceDescription.class, null)
                .setMaxResultsLimit(desc.networkInterfaceDescLinks.size());
        queryNids.collectDocuments(Collectors.toMap(d -> d.name, d -> {
            DescName descName = new DescName();
            descName.descLink = d.documentSelfLink;
            descName.descriptionName = d.name;
            return descName;
        })).whenComplete((map, t) -> callback.accept(map, t));

    }

    private DeferredResult<Set<String>> getNetworkLinks(ComputeState computeState) {

        // Some adapters populate the networks in the computeState's groupLinks, so try to get them from there
        if (computeState.groupLinks != null) {
            List<DeferredResult<String>> allNetworkLinks = computeState.groupLinks.stream()
                    .filter(link -> link
                            .contains(ResourceGroupService.FACTORY_LINK + "/" + PREFIX_NETWORK))
                    .map(link -> getResourceGroup(link).thenApply(g -> {
                        if (g.customProperties != null) {
                            return g.customProperties.get(CustomProperties.TARGET_LINK);
                        }
                        return null;
                    }))
                    .collect(Collectors.toList());

            if (!allNetworkLinks.isEmpty()) {
                return DeferredResult.allOf(allNetworkLinks).thenApply(networkLinks -> {
                    return networkLinks.stream().filter(link -> link != null)
                            .collect(Collectors.toCollection(() -> new TreeSet<>()));
                });
            }
        }

        // otherwise, query the subnets in the same region and availability zone as the host
        return getSubnetsForHost(computeState);
    }

    private DeferredResult<ResourceGroupState> getResourceGroup(String link) {
        Operation get = Operation.createGet(host, link).setReferer(host.getUri());

        return host.sendWithDeferredResult(get, ResourceGroupState.class);
    }

}
