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
import java.util.Arrays;
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
import com.vmware.admiral.compute.content.ConstraintConverter;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentState;
import com.vmware.admiral.compute.env.EnvironmentService.EnvironmentStateExpanded;
import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.photon.controller.model.Constraint.Condition;
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

    /** Collect environments that can satisfy all networks associated with VM. */
    public static void getEnvironmentsForComputeNics(ServiceHost host, URI referer,
            List<String> tenantLinks, String contextId, ComputeDescription computeDesc,
            BiConsumer<List<String>, Throwable> consumer) {
        if (computeDesc.networkInterfaceDescLinks == null || computeDesc.networkInterfaceDescLinks
                .isEmpty()) {
            consumer.accept(null, null);
            return;
        }
        getContextComputeNetworks(host, referer, tenantLinks, contextId, consumer,
                (retrievedNetworks) -> {
                    getNetworkEnvironments(host, referer, computeDesc,
                            retrievedNetworks, consumer);
                });
    }

    /** Select Subnet that is applicable for compute network interface. */
    public static void getSubnetForComputeNic(ServiceHost host, URI referer,
            List<String> tenantLinks, String contextId, NetworkInterfaceDescription nid,
            EnvironmentStateExpanded environmentState, BiConsumer<String, Throwable> consumer) {
        // Get all context networks
        getContextComputeNetworks(host, referer, tenantLinks, contextId,
                (contextNetworks, e) -> {
                    if (e != null) {
                        consumer.accept(null, e);
                        return;
                    }
                },
                (retrievedNetworks) -> {
                    // Find compute network for network interface
                    ComputeNetwork computeNetwork = retrievedNetworks.get(nid.name);
                    if (computeNetwork == null) {
                        consumer.accept(null, getContextNetworkNotFoundError(nid.name));
                        return;
                    }

                    List<String> constraints = new ArrayList<String>();

                    host.sendWithDeferredResult(
                            // Get network description of compute network
                            Operation.createGet(host, computeNetwork.descriptionLink)
                                    .setReferer(referer), ComputeNetworkDescription.class)
                            .thenCompose(networkDescription -> {
                                // Validate network description constraints against selected environment
                                Map<Condition, String> placementConstraints = TagQueryUtils
                                        .extractPlacementTagConditions(
                                                networkDescription.constraints,
                                                networkDescription.tenantLinks);
                                if (placementConstraints != null) {
                                    constraints.addAll(placementConstraints.keySet().stream()
                                            .map(c -> ConstraintConverter.encodeCondition(c).tag)
                                            .collect(Collectors.toList()));
                                }
                                return DeferredResult.completed(
                                        TagQueryUtils.filterByRequirements(placementConstraints,
                                                Arrays.asList(environmentState).stream(),
                                                env -> env.networkProfile.subnetStates.stream()
                                                        .map(s -> Pair.of(s, combineTags(environmentState, s))),
                                                null).findAny().orElse(null));
                            })
                            .whenComplete((subnetLink, ex) -> {
                                if (ex != null) {
                                    consumer.accept(null, ex);
                                } else if (subnetLink == null) {
                                    consumer.accept(null, new LocalizableValidationException(
                                            String.format(
                                                    "Selected environment '%s' doesn't satisfy network '%s' constraints %s.",
                                                    environmentState.name, nid.name, constraints),
                                            "compute.network.constraints.not.satisfied.by.environment",
                                            environmentState.name, nid.name, constraints));
                                } else {
                                    consumer.accept(subnetLink.documentSelfLink, null);
                                }
                            });
                });
    }

    /** Get Environments that can be used to provision compute networks. */
    public static void getEnvironmentsForNetworkDescription(ServiceHost host, URI referer,
            ComputeNetworkDescription networkDescription,
            BiConsumer<List<String>, Throwable> consumer) {
        selectEnvironmentsForNetworkDescriptionTenant(host, referer, networkDescription,
                networkDescription.tenantLinks, consumer);
    }

    private static void selectEnvironmentsForNetworkDescriptionTenant(ServiceHost host, URI referer,
            ComputeNetworkDescription networkDescription,
            List<String> tenantLinks, BiConsumer<List<String>, Throwable> consumer) {
        Set<String> environmentLinks = new HashSet<>();
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
                .addKindFieldClause(EnvironmentState.class);
        if (tenantLinks == null || tenantLinks.isEmpty()) {
            builder.addClause(QueryUtil.addTenantClause(tenantLinks));
        }

        QueryUtils.QueryByPages<EnvironmentState> query =
                new QueryUtils.QueryByPages<>(host, builder.build(), EnvironmentState.class,
                        QueryUtil.getTenantLinks(tenantLinks));
        query.queryLinks(env -> environmentLinks.add(env))
                .whenComplete(((v, e) -> {
                    if (e != null) {
                        consumer.accept(null, e);
                        return;
                    }
                    // If there no environments defined for the tenant, get system network profiles
                    if (environmentLinks.isEmpty() && tenantLinks != null && !tenantLinks
                            .isEmpty()) {
                        selectEnvironmentsForNetworkDescriptionTenant(host, referer,
                                networkDescription, null, consumer);
                        return;
                    }

                    // Get expanded environments
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
                        // Filter environments based on network constraints
                        Map<Condition, String> placementConstraints = TagQueryUtils
                                .extractPlacementTagConditions(networkDescription.constraints,
                                        networkDescription.tenantLinks);
                        List<String> selectedEnvironments = TagQueryUtils.filterByRequirements(
                                placementConstraints, all.stream(),
                                env -> env.networkProfile.subnetStates.stream()
                                        .map(s -> Pair.of(env, combineTags(env, s))), null)
                                .map(s -> s.documentSelfLink)
                                .collect(Collectors.toList());

                        if (placementConstraints != null && !placementConstraints.isEmpty()
                                && selectedEnvironments.isEmpty()) {
                            List<String> constraints = placementConstraints.keySet().stream()
                                    .map(c -> ConstraintConverter.encodeCondition(c).tag)
                                    .collect(Collectors.toList());
                            consumer.accept(null, new LocalizableValidationException(
                                    String.format(
                                            "Could not find any environments to satisfy all of network '%s' constraints %s.",
                                            networkDescription.name, constraints),
                                    "compute.network.no.environments.satisfy.constraints",
                                    networkDescription.name, constraints));
                        } else {
                            consumer.accept(selectedEnvironments, null);
                        }
                    });
                }));
    }

    private static void getNetworkEnvironments(ServiceHost host, URI referer,
            ComputeDescription computeDescription,
            Map<String, ComputeNetwork> contextComputeNetworks,
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
            all.removeIf(cn -> cn.environmentLinks == null || cn.environmentLinks.isEmpty());
            if (all.isEmpty()) {
                consumer.accept(null, null);
                return;
            }
            List<String> environmentLinks = all.get(0).environmentLinks;
            all.forEach(cn -> environmentLinks.retainAll(cn.environmentLinks));

            consumer.accept(environmentLinks, null);
        });
    }

    private static void getContextComputeNetworks(ServiceHost host, URI referer,
            List<String> tenantLinks, String contextId, BiConsumer<List<String>, Throwable> consumer,
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
                builder.build(), ComputeNetwork.class, tenantLinks);
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

    private static Set<String> combineTags(EnvironmentStateExpanded environment,
            SubnetState subnetState) {
        Set<String> tagLinks = new HashSet<>();
        if (environment.tagLinks != null) {
            tagLinks.addAll(environment.tagLinks);
        }
        if (environment.networkProfile.tagLinks != null) {
            tagLinks.addAll(environment.networkProfile.tagLinks);
        }
        if (subnetState.tagLinks != null) {
            tagLinks.addAll(subnetState.tagLinks);
        }

        return tagLinks;
    }
}
