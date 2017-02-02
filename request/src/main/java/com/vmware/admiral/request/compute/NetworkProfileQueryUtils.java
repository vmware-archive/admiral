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
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.tuple.Pair;

import com.vmware.admiral.compute.network.ComputeNetworkDescriptionService.ComputeNetworkDescription;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkInterfaceDescriptionService.NetworkInterfaceDescription;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

public class NetworkProfileQueryUtils {

    /** Collect network profile constraints for all networks associated with compute */
    public static void getComputeNetworkProfileConstraints(ServiceHost host, URI referer, String contextId,
            ComputeDescription computeDesc, BiConsumer<Set<String>, Throwable> consumer) {
        if (computeDesc.networkInterfaceDescLinks == null || computeDesc.networkInterfaceDescLinks
                .isEmpty()) {
            consumer.accept(null, null);
            return;
        }
        getContextComputeNetworks(host, referer, contextId, consumer,
                (retrievedNetworks) -> getNetworkConstraints(host, referer, computeDesc,
                        retrievedNetworks, consumer));
    }

    private static void getNetworkConstraints(ServiceHost host, URI referer,
            ComputeDescription computeDescription, HashMap<String, ComputeNetwork> contextComputeNetworks,
            BiConsumer<Set<String>, Throwable> consumer) {
        DeferredResult<List<ComputeNetwork>> result = DeferredResult.allOf(
                computeDescription.networkInterfaceDescLinks.stream()
                        .map(nicDescLink -> {
                            Operation op = Operation.createGet(host, nicDescLink).setReferer(referer);

                            return host.sendWithDeferredResult(op,
                                    NetworkInterfaceDescription.class);
                        })
                        .map(nid -> nid.thenCompose(nic -> {
                            ComputeNetwork computeNetwork = contextComputeNetworks.get(nic.name);
                            if (computeNetwork == null) {
                                throw new LocalizableValidationException(
                                        String.format("Could not find context network component with name '%s'.", nic.name),
                                        "compute.network.component.not.found", nic.name);

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
            all.removeIf(cn -> cn.networkProfileLinks == null || cn.networkProfileLinks.isEmpty());
            if (all.isEmpty()) {
                consumer.accept(null, null);
                return;
            }
            Set<String> networkProfileLinks = all.get(0).networkProfileLinks;
            all.forEach(cn -> networkProfileLinks.retainAll(cn.networkProfileLinks));
            consumer.accept(networkProfileLinks, null);
        });
    }

    private static void getContextComputeNetworks(ServiceHost host, URI referer, String contextId,
            BiConsumer<Set<String>, Throwable> consumer,
            Consumer<HashMap<String, ComputeNetwork>> callbackFunction) {
        HashMap<String, ComputeNetwork> contextNetworks = new HashMap<>();
        if (StringUtil.isNullOrEmpty(contextId)) {
            callbackFunction.accept(contextNetworks);
            return;
        }

        // Get all ComputeNetworks that have the same context id
        List<ComputeNetwork> computeNetworks = new ArrayList<>();
        QueryTask.Query.Builder builder = QueryTask.Query.Builder.create()
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
                                DeferredResult<Pair<String, ComputeNetwork>> r =
                                        new DeferredResult<>();
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
}
