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

package com.vmware.admiral.request.compute;

import static com.vmware.admiral.request.utils.RequestUtils.FIELD_NAME_CONTEXT_ID_KEY;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentStateExpanded;
import com.vmware.admiral.compute.env.NetworkProfileService.NetworkProfile;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

public class NetworkProfileQueryUtils {
    public static final String NO_NIC_VM = "__noNICVM";

    /** Collect network profile constraints for all networks associated with compute. */
    public static void getNetworkProfileConstraintsForComputeNics(ServiceHost host, URI referer,
            String contextId, ComputeDescription computeDesc, BiConsumer<Set<String>, Throwable> consumer) {
        if (computeDesc.networkInterfaceDescLinks == null || computeDesc.networkInterfaceDescLinks
                .isEmpty()) {
            consumer.accept(null, null);
            return;
        }
        getContextComputeNetworks(host, referer, contextId, consumer,
                (retrievedNetworks) -> {
                    getNetworkConstraints(host, referer, computeDesc,
                            retrievedNetworks, consumer);
                });
    }

    /** Select Subnet that is applicable for compute network interface. */
    public static void getSubnetForComputeNic(ServiceHost host, URI referer,
            String contextId, NetworkInterfaceDescription nid, EnvironmentStateExpanded environmentState,
            BiConsumer<String, Throwable> consumer) {
        // Get all context networks
        getContextComputeNetworks(host, referer, contextId,
                (contextNetworks, e) -> {
                    if (e != null) {
                        consumer.accept(null, e);
                        return;
                    }
                },
                // Get possible subnet profiles for the network interface
                (retrievedNetworks) -> {
                    ComputeNetwork computeNetwork = retrievedNetworks.get(nid.name);
                    if (computeNetwork == null) {
                        consumer.accept(null, getContextNetworkNotFoundError(nid.name));
                        return;
                    }
                    // TODO: filter by requirements
                    SubnetState subnetState =
                            computeNetwork.subnetLinks == null
                                    || computeNetwork.subnetLinks.isEmpty() ?
                                    environmentState.networkProfile.subnetStates.iterator()
                                            .next() :
                                    environmentState.networkProfile.subnetStates.stream()
                                            .filter(sl -> computeNetwork.subnetLinks
                                                    .contains(sl.documentSelfLink))
                                            .findAny().orElse(null);

                    if (subnetState == null) {
                        consumer.accept(null, new LocalizableValidationException(
                                String.format(
                                        "Selected environment '%s' doesn't satisfy network '%s' requirements.",
                                        environmentState.name, nid.name),
                                "compute.network.subnet.profile.not.found",
                                environmentState.name, nid.name));
                    }

                    consumer.accept(subnetState.documentSelfLink, null);
                });
    }

    /** Get subnetStates that can be used to provision compute networks. */
    public static void getSubnetsForNetworkDescription(ServiceHost host, URI referer,
            ComputeNetworkDescription networkDescription,
            BiConsumer<List<String>, Throwable> consumer) {
        selectSubnetsForNetworkDescriptionTenant(host, referer,
                networkDescription.tenantLinks, consumer);
    }

    private static void selectSubnetsForNetworkDescriptionTenant(ServiceHost host, URI referer,
            List<String> tenantLinks, BiConsumer<List<String>, Throwable> consumer) {

        Set<String> environmentLinks = new HashSet<>();
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(EnvironmentState.class)
                .addClause(QueryUtil.addTenantClause(tenantLinks));
        QueryUtils.QueryByPages<EnvironmentState> query =
                new QueryUtils.QueryByPages<>(host, builder.build(), EnvironmentState.class, null);
        query.queryLinks(env -> environmentLinks.add(env))
                .whenComplete(((v, e) -> {
                    if (e != null) {
                        consumer.accept(null, e);
                        return;
                    }
                    // If there no network profiles defined for the tenant, get system network profiles
                    if (environmentLinks.isEmpty() && tenantLinks != null && !tenantLinks
                            .isEmpty()) {
                        selectSubnetsForNetworkDescriptionTenant(host, referer,
                                null,
                                consumer);
                        return;
                    }

                    DeferredResult<List<EnvironmentStateExpanded>> result = DeferredResult.allOf(
                            environmentLinks.stream()
                                    .map(envLink -> {
                                        Operation op = Operation.createGet(
                                                EnvironmentStateExpanded.buildUri(UriUtils.buildUri(
                                                        host, envLink)))
                                                .setReferer(referer);

                                        return host.sendWithDeferredResult(op,
                                                EnvironmentStateExpanded.class);
                                    })
                                    .collect(Collectors.toList()));

                    result.whenComplete((all, ex) -> {
                        if (ex != null) {
                            consumer.accept(null, ex);
                            return;
                        }
                        // TODO: filter by requirements
                        List<String> subnetLinks = all.stream()
                                .flatMap(env -> env.networkProfile.subnetStates
                                        .stream()
                                        .map(s -> s.documentSelfLink))
                                .collect(Collectors.toList());

                        consumer.accept(subnetLinks, null);
                    });
                }));
    }

    private static void getNetworkConstraints(ServiceHost host, URI referer,
            ComputeDescription computeDescription,
            Map<String, ComputeNetwork> contextComputeNetworks,
            BiConsumer<Set<String>, Throwable> consumer) {
        getNetworkSubnetConstraints(host, referer, computeDescription,
                contextComputeNetworks,
                (subnetLinks, e) -> {
                    if (e != null) {
                        consumer.accept(null, e);
                        return;
                    }
                    // Get NetworkProfile links of Subnets
                    Set<String> networkProfileLinks = new HashSet<String>();
                    Builder builder = Builder.create()
                            .addKindFieldClause(NetworkProfile.class)
                            .addClause(QueryUtil.addListValueClause(QueryTask.QuerySpecification
                                            .buildCollectionItemName(
                                                    NetworkProfile.FIELD_NAME_SUBNET_LINKS),
                                    subnetLinks, QueryTask.QueryTerm.MatchType.TERM));
                    QueryUtils.QueryByPages<NetworkProfile> query =
                            new QueryUtils.QueryByPages<>(host, builder.build(),
                                    NetworkProfile.class,
                                    null);
                    query.queryLinks(np -> networkProfileLinks.add(np)).whenComplete((v, ex) -> {
                        consumer.accept(networkProfileLinks, ex);
                    });
                });
    }

    private static void getNetworkSubnetConstraints(ServiceHost host, URI referer,
            ComputeDescription computeDescription, Map<String, ComputeNetwork> contextComputeNetworks,
            BiConsumer<List<String>, Throwable> consumer) {
        DeferredResult<List<ComputeNetwork>> result = DeferredResult.allOf(
                computeDescription.networkInterfaceDescLinks.stream()
                        .map(nicDescLink -> {
                            Operation op = Operation.createGet(host, nicDescLink)
                                    .setReferer(referer);

                            return host.sendWithDeferredResult(op,
                                    NetworkInterfaceDescription.class);
                        })
                        .map(nidr -> nidr.thenCompose(nid -> {
                            if (nid.customProperties != null
                                    && nid.customProperties.containsKey(NO_NIC_VM)) {
                                // VM was requested without NIC then simply return a no constraint
                                // compute network
                                return DeferredResult.completed(new ComputeNetwork());
                            }

                            ComputeNetwork computeNetwork = contextComputeNetworks.get(nid.name);
                            if (computeNetwork == null) {
                                throw getContextNetworkNotFoundError(nid.name);
                            }
                            return DeferredResult.completed(computeNetwork);
                        }))
                        .collect(Collectors.toList()));

        result.whenComplete((all, e) -> {
            if (e != null) {
                consumer.accept(null, e);
                return;
            }

            // Remove networks that don't have any constraints
            all.removeIf(cn -> cn.subnetLinks == null || cn.subnetLinks.isEmpty());
            if (all.isEmpty()) {
                consumer.accept(null, null);
                return;
            }
            List<String> subnetLinks = all.get(0).subnetLinks;
            all.forEach(cn -> subnetLinks.retainAll(cn.subnetLinks));

            consumer.accept(subnetLinks, null);
        });
    }

    private static void getContextComputeNetworks(ServiceHost host, URI referer, String contextId,
            BiConsumer<Set<String>, Throwable> consumer,
            Consumer<Map<String, ComputeNetwork>> callbackFunction) {
        Map<String, ComputeNetwork> contextNetworks = new HashMap<>();
        if (StringUtils.isBlank(contextId)) {
            callbackFunction.accept(contextNetworks);
            return;
        }

        // Get all ComputeNetworks that have the same context id
        List<ComputeNetwork> computeNetworks = new ArrayList<>();
        Builder builder = Builder.create()
                .addKindFieldClause(ComputeNetwork.class);
        builder.addCompositeFieldClause(ComputeState.FIELD_NAME_CUSTOM_PROPERTIES,
                FIELD_NAME_CONTEXT_ID_KEY, contextId);
        QueryUtils.QueryByPages<ComputeNetwork> query = new QueryUtils.QueryByPages<>(
                host,
                builder.build(), ComputeNetwork.class, null);
        query.queryDocuments(ns -> computeNetworks.add(ns)).whenComplete((v, e) -> {
            if (e != null) {
                consumer.accept(null, e);
                return;
            }
            // Get ComputeNetworkDescription of every network
            List<DeferredResult<Pair<String, ComputeNetwork>>> list = computeNetworks
                    .stream()
                    .map(cn -> host.sendWithDeferredResult(
                            Operation.createGet(host, cn.descriptionLink).setReferer(referer),
                            ComputeNetworkDescription.class)
                            .thenCompose(cnd -> {
                                DeferredResult<Pair<String, ComputeNetwork>> r = new DeferredResult<>();
                                r.complete(Pair.of(cnd.name, cn));
                                return r;
                            }))
                    .collect(Collectors.toList());
            // Create a map of ComputeNetworkDescription.name to ComputeNetworkState
            DeferredResult.allOf(list).whenComplete((all, t) -> {
                all.forEach(p -> contextNetworks.put(p.getKey(), p.getValue()));
                callbackFunction.accept(contextNetworks);
            });
        });
    }

    private static LocalizableValidationException getContextNetworkNotFoundError(
            String networkInterfaceName) {
        return new LocalizableValidationException(
                String.format(
                        "Could not find context network component with name '%s'.",
                        networkInterfaceName),
                "compute.network.component.not.found", networkInterfaceName);
    }
}
