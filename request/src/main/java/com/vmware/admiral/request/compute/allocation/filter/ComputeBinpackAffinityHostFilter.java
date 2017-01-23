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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService;
import com.vmware.admiral.request.allocation.filter.AffinityConstraint;
import com.vmware.admiral.request.allocation.filter.HostSelectionFilter;
import com.vmware.admiral.request.compute.ComputeReservationTaskService;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;


/**
*
*  A filter implementing {@link HostSelectionFilter} aimed to provide host selection based on Placement - BINPACK.
*
*  Algorithm will filter out the docker hosts leaving only the most loaded in terms of memory one.
*   1) Let h ∈ { h(1)....h(n-1), h(n) }
*   2) P(h) = min{h(1)...h(n)}
*   3) ∃! h: P(h)
*
*   Constraint (1) means that most suitable host belongs to set of hosts which have been filtered from other affinity filters.
*   Constraint (2) means that hosts will be sorted by available memory in ascending order. Host with smallest available memory will be returned.
*   Constraint (3) means there is exactly one host such that P(h)  is true.
*
*/
public class ComputeBinpackAffinityHostFilter implements HostSelectionFilter<FilterContext> {

    private static final Long MINIMAL_AVAILABLE_MEMORY_IN_BYTES = 3000000000L; // 3 GB

    private static final String DAILY_MEMORY_USED_BYTES = "daily.memoryUsedBytes";

    private final ServiceHost host;

    private Map<String, Double> memoryByCompute = new ConcurrentHashMap<>();

    public ComputeBinpackAffinityHostFilter(ServiceHost host, ComputeDescription desc) {
        this.host = host;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Map<String, AffinityConstraint> getAffinityConstraints() {
        return Collections.emptyMap();
    }

    @Override
    public void filter(FilterContext state,
            Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        // Nothing to filter here.
        if (hostSelectionMap.size() <= 1) {
            host.log(Level.INFO, "Only one host in selection. BinPack filtering will be skipped.");
            callback.complete(hostSelectionMap, null);
            return;
        }

        String serviceLink = state.serviceLink;
        // Filter should be ignored on Reservation stage.
        if (serviceLink != null
                && serviceLink.startsWith(ComputeReservationTaskService.FACTORY_LINK)) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        String resourcePoolLink = state.resourcePoolLinks.get(0);
        filterBasedOnBinpackPolicy(resourcePoolLink, hostSelectionMap, callback);
    }

    public void filterBasedOnBinpackPolicy(String resourcePoolLink,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        URI uri = UriUtils.buildUri(host, String.format("%s/%s",
                ElasticPlacementZoneConfigurationService.SELF_LINK, resourcePoolLink));

        host.sendRequest(Operation.createGet(uri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {

                    if (ex != null) {
                        host.log(Level.WARNING, Utils.toString(ex));
                        callback.complete(hostSelectionMap, null);
                        return;
                    }

                    ElasticPlacementZoneConfigurationState epz = o
                            .getBody(ElasticPlacementZoneConfigurationState.class);
                    if (epz != null && epz.epzState != null
                            && epz.epzState.placementPolicy == ElasticPlacementZoneService.PlacementPolicy.BINPACK) {

                        collectStats(resourcePoolLink, hostSelectionMap, callback);

                    } else {
                        callback.complete(hostSelectionMap, null);
                    }

                }));
    }

    private void collectStats(String resourcePoolLink,
            Map<String, HostSelection> hostSelectionMap, HostSelectionFilterCompletion callback) {

        List<Operation> statsOperations = new ArrayList<>();

        for (String computeStateLink : hostSelectionMap.keySet()) {
            statsOperations.add(createCollectStatsOperation(computeStateLink));
        }

        OperationJoin hostsStatsOperation = OperationJoin
                .create(statsOperations);

        hostsStatsOperation.setCompletion((ops, failures) -> {
            if (failures != null) {
                host.log(Level.SEVERE, "Failure retrieve statistics: ",
                        Utils.toString(failures));
                // Don't return here. If cannot get stats from some of the hosts shouldn't block the
                // process?
            }

            // Check if some of the hosts doesn't provide statistics.
            if (memoryByCompute.size() != hostSelectionMap.size()) {
                // TODO Decide whether to fail or just log the exception.
                host.log(Level.SEVERE, "Some of the hosts doesn't provide statistics.");
            }

            // Get the host with highest memory usage.
            returnMaxLoadedHost(hostSelectionMap, callback);

        });

        hostsStatsOperation.sendWith(host);

    }

    private Operation createCollectStatsOperation(String computeLink) {

        host.log(Level.INFO, String.format("Start collecting stats from host: %s", computeLink));

        URI computeStatsUri = UriUtils.buildStatsUri(host, computeLink);

        return Operation.createGet(computeStatsUri)
                .setReferer(host.getUri())
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        host.log(Level.WARNING, Utils.toString(ex));

                        return;
                    }

                    // Get available memory and put it in "memoryByCompute" map.
                    ServiceStats serviceStats = o.getBody(ServiceStats.class);
                    Map<String, ServiceStat> stats = serviceStats.entries;
                    if (stats != null && !stats.isEmpty()) {
                        ServiceStat serviceStat = stats.get(DAILY_MEMORY_USED_BYTES);
                        memoryByCompute.put(computeLink, serviceStat.accumulatedValue);

                    } else {
                        host.log(Level.SEVERE,
                                String.format("Stats for [%s] are empty!", computeLink));
                    }
                });

    }

    // Get max loaded in terms of memory host.
    private void returnMaxLoadedHost(Map<String, HostSelection> hostSelectionMap,
            HostSelectionFilterCompletion callback) {

        // This should never happen.
        if (memoryByCompute.isEmpty()) {
            callback.complete(hostSelectionMap, null);
            return;
        }

        Map<String, HostSelection> result = new LinkedHashMap<>();
        Map<String, Double> sortedMap = new LinkedHashMap<>();

        // Sort map ascending based on available memory.
        memoryByCompute.entrySet().stream()
                .sorted(Map.Entry.<String, Double> comparingByValue())
                .forEachOrdered(x -> sortedMap.put(x.getKey(), x.getValue()));

        // Traverse trough sorted hosts to memory map and find first max loaded which has at least 3
        // GB memory available.
        Optional<Entry<String, Double>> hostToMemoryEntry = sortedMap.entrySet().stream()
                .filter(obj -> obj.getValue() > MINIMAL_AVAILABLE_MEMORY_IN_BYTES).findFirst();

        if (!hostToMemoryEntry.isPresent()) {
            callback.complete(null, new Throwable("All hosts are overloaded."));
            return;
        }

        String mostLoadedHost = hostToMemoryEntry.get().getKey();

        result.put(mostLoadedHost, hostSelectionMap.get(mostLoadedHost));
        callback.complete(result, null);
    }
}
