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
import static com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType.SECURITY_GROUP;
import static com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType.SUBNET;
import static com.vmware.admiral.request.compute.NetworkProfileQueryUtils.getConstraints;
import static com.vmware.admiral.request.compute.NetworkProfileQueryUtils.getSubnets;
import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfile.IsolationSupportType;
import com.vmware.admiral.compute.profile.NetworkProfileService.NetworkProfileExpanded;
import com.vmware.admiral.compute.profile.ProfileService.ProfileStateExpanded;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.photon.controller.model.Constraint.Condition;
import com.vmware.photon.controller.model.adapters.util.Pair;
import com.vmware.photon.controller.model.adapters.vsphere.CustomProperties;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.query.QueryUtils.QueryTop;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide affinity and network name
 * resolution in case the {@link ComputeDescription} specifies <code>networks</code> property.
 */
public class ComputeToNetworkAffinityHostFilter implements HostSelectionFilter<FilterContext> {
    private final ServiceHost host;
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

        Map<String, Set<HostSelection>> hostGroupsBySubnet; // all hosts in a subnet
        Map<String, Set<String>> hostSubnets; // all subnets connected to a host

        Map<String, Set<HostSelection>> hostGroupsByNetwork; // all hosts in a subnet
        Map<String, Set<String>> hostNetworks; // all subnets connected to a host

        Map<ComputeNetwork, Set<NetworkProfileExpanded>> profiles;
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
                        getData(context)
                                // pick (a group of) hosts
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

    public DeferredResult<InternalContext> getData(
            InternalContext context) {
        return getProfiles(context)
                // get the compute states
                .thenCompose(this::getHosts)
                // group them by subnet
                .thenCompose(this::groupHostsByConnectivityBySubnet)
                // group them by network
                .thenCompose(this::groupHostsByConnectivityByNetwork);
    }

    private DeferredResult<InternalContext> getProfiles(InternalContext context) {
        Set<ComputeNetwork> computeNetworks = context.contextNetworks.keySet();

        List<DeferredResult<Pair<ComputeNetwork, List<NetworkProfileExpanded>>>> drs = computeNetworks
                .stream().map(cn -> getProfilesByLinks(cn.profileLinks)
                        .thenApply(profiles -> Pair.of(cn, profiles))).collect(Collectors.toList());

        return DeferredResult.allOf(drs).thenApply(pairs -> {
            context.profiles = pairs.stream()
                    .collect(Collectors.toMap(p -> p.left, p -> new HashSet<>(p.right)));
            return context;
        });
    }

    private DeferredResult<List<NetworkProfileExpanded>> getProfilesByLinks(
            List<String> profileLinks) {
        List<DeferredResult<NetworkProfileExpanded>> profileStatesRequests = profileLinks.stream()
                .map(link -> getProfileByLink(link).thenApply(p -> p.networkProfile))
                .collect(Collectors.toList());

        return DeferredResult.allOf(profileStatesRequests);
    }

    private DeferredResult<ProfileStateExpanded> getProfileByLink(String link) {
        URI uri = ProfileStateExpanded.buildUri(UriUtils.buildUri(host, link));

        Operation get = Operation.createGet(uri).setReferer(host.getUri());
        return host.sendWithDeferredResult(get, ProfileStateExpanded.class);
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
    private DeferredResult<InternalContext> groupHostsByConnectivityBySubnet(InternalContext internalContext) {

        List<DeferredResult<Pair<ComputeState, Set<String>>>> getNetworksRequests = internalContext.hosts
                .stream()
                .map(computeState -> {
                    return getSubnetLinks(computeState)
                            .thenApply(networkLinks -> Pair.of(computeState, networkLinks));
                }).collect(Collectors.toList());

        return DeferredResult.allOf(getNetworksRequests).thenApply(pairs -> {

            internalContext.hostSubnets = new TreeMap<>();

            Map<String, Set<HostSelection>> result = new TreeMap<>();

            for (Pair<ComputeState, Set<String>> pair : pairs) {
                ComputeState computeState = pair.left;
                HostSelection hostSelection = internalContext.hostSelectionMap
                        .get(computeState.documentSelfLink);
                Set<String> networks = pair.right;

                internalContext.hostSubnets.put(computeState.documentSelfLink, networks);

                for (String networkLink : networks) {
                    result.putIfAbsent(networkLink, new TreeSet<HostSelection>(
                            (l, r) -> String.CASE_INSENSITIVE_ORDER
                                    .compare(l.hostLink, r.hostLink)));
                    result.get(networkLink).add(hostSelection);
                }
            }

            return result;
        }).thenApply(groups -> {
            internalContext.hostGroupsBySubnet = groups;
            return internalContext;
        });
    }

    /**
     * Group hosts by connectivity. For each host that the filter is given get the subnets/networks
     * that it is connected to. Then "reverse" the mapping i.e for each subnet/network get the hosts
     */
    private DeferredResult<InternalContext> groupHostsByConnectivityByNetwork(InternalContext internalContext) {

        List<DeferredResult<Pair<ComputeState, Set<String>>>> getNetworksRequests = internalContext.hosts
                .stream()
                .map(computeState -> {
                    return getNetworkLinks(computeState)
                            .thenApply(networkLinks -> Pair.of(computeState, networkLinks));
                }).collect(Collectors.toList());

        return DeferredResult.allOf(getNetworksRequests).thenApply(pairs -> {

            internalContext.hostNetworks = new TreeMap<>();

            Map<String, Set<HostSelection>> result = new TreeMap<>();

            for (Pair<ComputeState, Set<String>> pair : pairs) {
                ComputeState computeState = pair.left;
                HostSelection hostSelection = internalContext.hostSelectionMap
                        .get(computeState.documentSelfLink);
                Set<String> networks = pair.right;

                internalContext.hostNetworks.put(computeState.documentSelfLink, networks);

                for (String networkLink : networks) {
                    result.putIfAbsent(networkLink, new TreeSet<HostSelection>(
                            (l, r) -> String.CASE_INSENSITIVE_ORDER
                                    .compare(l.hostLink, r.hostLink)));
                    result.get(networkLink).add(hostSelection);
                }
            }

            return result;
        }).thenApply(groups -> {
            internalContext.hostGroupsByNetwork = groups;
            return internalContext;
        });
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
                return DeferredResult.allOf(allNetworkLinks).thenApply(links -> new TreeSet<>(links));
            }
        }

        // otherwise, query the network in the same region as the host
        return getNetworksForHost(computeState);
    }

    private DeferredResult<Set<String>> getNetworksForHost(ComputeState computeState) {
        DeferredResult<Set<String>> networkLinksDr = new DeferredResult<>();

        Query.Builder builder = Query.Builder.create().addKindFieldClause(NetworkState.class);
        // TODO https://jira-hzn.eng.vmware.com/browse/VCOM-1299
        // remove the if when the vMhost.regionId becomes mandatory
        if (computeState.regionId != null) {
            builder.addFieldClause(SubnetState.FIELD_NAME_REGION_ID, computeState.regionId);
        }

        Query query = builder.build();

        QueryUtils.QueryByPages<NetworkState> queryByPages = new QueryUtils.QueryByPages<>(host, query,
                NetworkState.class, computeState.tenantLinks, computeState.endpointLink);

        Set<String> networkLinks = new TreeSet<>();
        queryByPages.queryLinks(subnetLink -> {
            networkLinks.add(subnetLink);
        }).whenComplete(((aVoid, throwable) -> {
            if (throwable != null) {
                networkLinksDr.fail(throwable);
            } else {
                networkLinksDr.complete(networkLinks);
            }
        }));

        return networkLinksDr;
    }

    private Map<String, HostSelection> pickHosts(InternalContext context) {

        Map<String, Set<HostSelection>> allHostGroups = new TreeMap<>();
        for (Map.Entry<ComputeNetwork, ComputeNetworkDescription> entry : context.contextNetworks
                .entrySet()) {

            ComputeNetworkDescription desc = entry.getValue();
            ComputeNetwork network = entry.getKey();

            Set<NetworkProfileExpanded> profiles = context.profiles.get(network);

            Map<String, Set<HostSelection>> allHostsForNetwork;

            /**
             * If the network type is isolated, we split the profiles in two groups. Isolation by
             * subnet and isolation by security group. Then for both groups we filter the hosts and
             * finally return a union of the two groups of hosts.
             */
            if (desc.networkType == ISOLATED) {
                allHostsForNetwork = new TreeMap<>();

                Map<IsolationSupportType, List<NetworkProfileExpanded>> profilesByType = profiles
                        .stream().collect(Collectors.groupingBy(p -> p.isolationType));

                // isolation by security group
                List<NetworkProfileExpanded> securityGroupProfiles = profilesByType
                        .get(SECURITY_GROUP);

                if (securityGroupProfiles != null) {
                    TreeMap<String, Set<HostSelection>> securityGroupHosts = filterHostsBySubnet(
                            context,
                            desc, network, securityGroupProfiles);

                    allHostsForNetwork.putAll(securityGroupHosts);
                }

                // isolation by subnet
                List<NetworkProfileExpanded> subnetProfiles = profilesByType.get(SUBNET);
                if (subnetProfiles != null) {
                    TreeMap<String, Set<HostSelection>> subnetHosts = filterHostsByNetwork(context,
                            subnetProfiles);
                    allHostsForNetwork.putAll(subnetHosts);
                }

            } else {
                allHostsForNetwork = filterHostsBySubnet(context, desc, network, profiles);
            }

            if (allHostGroups.isEmpty()) {
                allHostGroups.putAll(allHostsForNetwork);
            } else {
                allHostGroups.keySet().retainAll(allHostsForNetwork.keySet());
            }
        }

        List<Set<HostSelection>> groups = new ArrayList<>(allHostGroups.values());

        //sort descending
        groups.sort((g1, g2) -> Integer.compare(g2.size(), g1.size()));

        int max = groups.get(0).size();

        List<Set<HostSelection>> largestGroups = groups.stream().filter(g -> g.size() == max)
                .collect(Collectors.toList());

        /**
         * If there is more than one largest group, pick one based on the hash of the context id
         * We want to make sure that when the conditions are the same we pick the same group for
         * each compute description that is part of this request
         */
        int groupIdx = Math.abs(context.filterContext.contextId.hashCode() % largestGroups.size());

        Set<HostSelection> hostGroup = largestGroups.get(groupIdx);

        Set<String> filteredHostLinks = hostGroup.stream()
                .map(cs -> cs.hostLink)
                .collect(Collectors.toSet());

        Map<String, HostSelection> result = new LinkedHashMap<>(context.hostSelectionMap);
        result.keySet().retainAll(filteredHostLinks);

        return result;
    }

    private TreeMap<String, Set<HostSelection>> filterHostsByNetwork(InternalContext context,
            List<NetworkProfileExpanded> subnetProfiles) {
        Set<String> networks = new HashSet<>();
        for (NetworkProfileExpanded subnetProfile : subnetProfiles) {
            String networkLink = subnetProfile.isolationNetworkLink;

            Set<HostSelection> hostSelections = context.hostGroupsByNetwork
                    .get(networkLink);

            if (hostSelections != null) {
                hostSelections.forEach(
                        h -> h.addNetworkResource(subnetProfile.documentSelfLink, networkLink));
                networks.add(networkLink);
            }
        }

        TreeMap<String, Set<HostSelection>> subnetHosts = new TreeMap<>(
                context.hostGroupsByNetwork);
        subnetHosts.keySet().retainAll(networks);
        return subnetHosts;
    }

    private TreeMap<String, Set<HostSelection>> filterHostsBySubnet(InternalContext context,
            ComputeNetworkDescription desc, ComputeNetwork network,
            Collection<NetworkProfileExpanded> profiles) {

        Set<String> subnetLinks = new HashSet<>();
        for (NetworkProfileExpanded profile : profiles) {

            Map<Condition, String> constraints = getConstraints(desc);
            Set<SubnetState> subnets = getSubnets(network, profile, constraints);

            for (SubnetState subnet : subnets) {
                Set<HostSelection> hostSelections = context.hostGroupsBySubnet
                        .get(subnet.documentSelfLink);

                if (hostSelections != null) {
                    hostSelections.forEach(
                            h -> h.addNetworkResource(profile.documentSelfLink,
                                    subnet.documentSelfLink));
                    subnetLinks.add(subnet.documentSelfLink);
                }
            }
        }

        TreeMap<String, Set<HostSelection>> hosts = new TreeMap<>(
                context.hostGroupsBySubnet);
        hosts.keySet().retainAll(subnetLinks);
        return hosts;
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

        QueryByPages<SubnetState> queryByPages = new QueryByPages<>(host, query,
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

    private DeferredResult<Set<String>> getSubnetLinks(ComputeState computeState) {

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
                return DeferredResult.allOf(allNetworkLinks)
                        .thenCompose(networkLinks -> getSubnetLinksByNetworkLinks(networkLinks,
                                computeState.tenantLinks));
            }
        }

        // otherwise, query the subnets in the same region and availability zone as the host
        return getSubnetsForHost(computeState);
    }

    private DeferredResult<Set<String>> getSubnetLinksByNetworkLinks(List<String> networkLinks,
            List<String> tenantLinks) {
        DeferredResult<Set<String>> subnetLinksDr = new DeferredResult<>();

        Query.Builder builder = Query.Builder.create().addKindFieldClause(SubnetState.class)
                .addInClause(SubnetState.FIELD_NAME_NETWORK_LINK, networkLinks);
        Query query = builder.build();

        QueryUtils.QueryByPages<SubnetState> queryByPages = new QueryUtils.QueryByPages<>(host, query,
                SubnetState.class, tenantLinks);

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

    private DeferredResult<ResourceGroupState> getResourceGroup(String link) {
        Operation get = Operation.createGet(host, link).setReferer(host.getUri());

        return host.sendWithDeferredResult(get, ResourceGroupState.class);
    }

}
