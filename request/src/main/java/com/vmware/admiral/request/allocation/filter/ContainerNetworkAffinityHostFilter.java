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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;

import com.vmware.admiral.compute.container.network.ContainerNetworkDescriptionService.ContainerNetworkDescription;
import com.vmware.admiral.request.PlacementHostSelectionTaskService;
import com.vmware.xenon.common.ServiceHost;

/**
 * A filter implementing {@link HostSelectionFilter} in order to provide host selection in case
 * {@link ContainerNetworkDescription}s are going to be allocated and provisioned.
 */
public class ContainerNetworkAffinityHostFilter implements HostSelectionFilter {

    private static final String NONE = "NONE";

    private final ServiceHost host;
    private final ContainerNetworkDescription network;

    public ContainerNetworkAffinityHostFilter(ServiceHost host, ContainerNetworkDescription desc) {
        this.host = host;
        this.network = desc;
    }

    @Override
    public void filter(PlacementHostSelectionTaskService.PlacementHostSelectionTaskState state,
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {
        if (isActive()) {
            filterByClusterStoreAffinity(hostSelectionMap, callback);
        } else {
            callback.complete(hostSelectionMap, null);
        }
    }

    /*
     * This filter considers all the hosts equal, distinguishable only by whether they have a KV
     * store configured or not. At some point we may want to distinguish also special hosts (e.g.
     * Docker Swarm hosts, VIC hosts, etc.) which, as a single host, may be better alternative to
     * clusters of regular hosts sharing the same KV store.
     */
    protected void filterByClusterStoreAffinity(
            final Map<String, HostSelection> hostSelectionMap,
            final HostSelectionFilterCompletion callback) {

        /*
         * Transform the hostSelectionMap into a map where the keys group sets of hosts that share
         * the same KV store. This includes a special group with the key "NONE" for hosts without a
         * KV store configured.
         */

        Map<String, List<Entry<String, HostSelection>>> hostSelectionByKVStoreMap = new HashMap<>();
        Map<String, Integer> hostSelectionByKVStoreSizes = new HashMap<>();

        for (Entry<String, HostSelection> entry : hostSelectionMap.entrySet()) {

            // TODO - Some hosts should be discarded according to the type of network they support
            // and the type of network required by the description (e.g. overlay, bridge and so on).

            HostSelection hostSelection = entry.getValue();

            String clusterStoreKey = hostSelection.clusterStore;
            if (clusterStoreKey == null) {
                clusterStoreKey = NONE;
            }

            List<Entry<String, HostSelection>> set = hostSelectionByKVStoreMap.get(clusterStoreKey);
            if (set == null) {
                set = new ArrayList<>();
                hostSelectionByKVStoreMap.put(clusterStoreKey, set);
                hostSelectionByKVStoreSizes.put(clusterStoreKey, 0);
            }

            set.add(entry);

            hostSelectionByKVStoreSizes.put(clusterStoreKey,
                    hostSelectionByKVStoreSizes.get(clusterStoreKey) + 1);
        }

        // Separate the hosts without a KV store configured.

        List<Entry<String, HostSelection>> nones = hostSelectionByKVStoreMap.remove(NONE);
        hostSelectionByKVStoreSizes.remove(NONE);

        // Select the sets with more hosts thus next filters have bigger choice.

        List<String> selectedKeys = new ArrayList<>();

        Iterator<Entry<String, Integer>> itSorted = hostSelectionByKVStoreSizes.entrySet().stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).iterator();

        Integer max = 0;
        while (itSorted.hasNext()) {
            Entry<String, Integer> entry = itSorted.next();
            if (entry.getValue() >= max) {
                selectedKeys.add(entry.getKey());
                max = entry.getValue();
            } else {
                break;
            }
        }

        Map<String, HostSelection> hostSelectedMap = new HashMap<>();

        if (selectedKeys.isEmpty()) {
            /*
             * No hosts with KV store configured were found -> Pick one single host from the list of
             * hosts without a KV store configured.
             */

            // TODO - Picking one host randomly, it could pick the best single node available...

            if ((nones != null) && !nones.isEmpty()) {
                int chosen = new Random().nextInt(nones.size());
                Entry<String, HostSelection> entry = nones.get(chosen);
                hostSelectedMap.put(entry.getKey(), entry.getValue());
            }
        } else {
            /*
             * One or more sets (with the same max size) of hosts with the same KV store configured
             * were selected -> Pick one of the clusters.
             */

            // TODO - Picking one cluster randomly, it could pick the best cluster available...

            int chosen = new Random().nextInt(selectedKeys.size());
            List<Entry<String, HostSelection>> entries = hostSelectionByKVStoreMap
                    .get(selectedKeys.get(chosen));
            for (Entry<String, HostSelection> entry : entries) {
                hostSelectedMap.put(entry.getKey(), entry.getValue());
            }
        }

        // Complete the callback with the selected hosts...

        try {
            callback.complete(hostSelectedMap, null);
        } catch (Throwable e) {
            host.log(Level.WARNING, "Exception when completing callback. Error: [%s]",
                    e.getMessage());
            callback.complete(null, e);
        }
    }

    @Override
    public boolean isActive() {
        return network != null;
    }

    @Override
    public boolean hasEffectOnDependency() {
        return false;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return Collections.emptyMap();
    }
}
