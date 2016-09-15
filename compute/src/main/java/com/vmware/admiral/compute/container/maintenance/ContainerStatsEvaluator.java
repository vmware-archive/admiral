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

package com.vmware.admiral.compute.container.maintenance;

import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import com.vmware.xenon.common.Utils;

/**
 * Calculate the container stats based on the json data coming from the Docker hosts.
 */
public class ContainerStatsEvaluator {

    private static final String CONTAINER_STOPPED_TIME = "0001-01-01T00:00:00Z";

    /**
     * Parse the json stats value and return ContainerStats state with the calculated values from the json field.
     *
     * @return ContainerStats with parsed and calculated stats value.
     */
    public static ContainerStats calculateStatsValues(String statsJson) {
        ContainerStats state = new ContainerStats();
        if (statsJson == null || statsJson.isEmpty()) {
            return state;
        }

        Map<String, JsonElement> stats = parseStats(statsJson);

        calculateCpuUsage(state, stats);

        setMemoryUsage(state, stats);

        setNetworkUsage(state, stats);

        setContainerStopped(state, stats);

        return state;
    }

    private static Map<String, JsonElement> parseStats(String statsJson) {
        Map<String, JsonElement> stats = null;
        try {
            stats = Utils.fromJson(statsJson, new TypeToken<Map<String, JsonElement>>() {
            }.getType());
        } catch (Exception e) {
            Utils.logWarning("Error parsing container stats: [%s]. Error: %s", statsJson,
                    Utils.toString(e));
        }
        return stats;
    }

    private static void setNetworkUsage(ContainerStats state, Map<String, JsonElement> stats) {
        try {
            JsonElement jsonElement = stats.get("network");
            if (jsonElement == null) {
                return;
            }
            JsonObject network = jsonElement.getAsJsonObject();
            if (network == null) {
                return;
            }
            JsonElement netInValue = network.get("rx_bytes");
            if (netInValue != null) {
                state.networkIn = netInValue.getAsLong();
            }

            JsonElement netOutValue = network.get("tx_bytes");
            if (netOutValue != null) {
                state.networkOut = netOutValue.getAsLong();
            }
        } catch (Exception e) {
            Utils.logWarning("Error during container stats network usage parsing: %s",
                    Utils.toString(e));
        }
    }

    private static void setMemoryUsage(ContainerStats state, Map<String, JsonElement> stats) {
        try {
            JsonElement jsonElement = stats.get("memory_stats");
            if (jsonElement == null) {
                return;
            }
            JsonObject memory_stats = jsonElement.getAsJsonObject();
            JsonElement limitValue = memory_stats.get("limit");
            if (limitValue != null) {
                state.memLimit = limitValue.getAsLong();
            }
            JsonElement usage = memory_stats.get("usage");
            if (usage != null) {
                state.memUsage = usage.getAsLong();
            }
        } catch (Exception e) {
            Utils.logWarning("Error during container stats memory usage parsing: %s",
                    Utils.toString(e));
        }
    }

    // Calculate Docker container CPU percentage usage as implemented by the command line tool -
    // https://github.com/docker/docker/blob/master/api/client/stats.go#L195
    private static void calculateCpuUsage(ContainerStats state, Map<String, JsonElement> stats) {
        try {
            JsonElement cpu_stats_json = stats.get("cpu_stats");
            if (cpu_stats_json == null || cpu_stats_json.isJsonNull()) {
                Utils.logWarning("cpu_stats is null.");
                return;
            }
            JsonObject cpu_stats = cpu_stats_json.getAsJsonObject();

            JsonElement systemCpuUsageValue = cpu_stats.get("system_cpu_usage");
            if (systemCpuUsageValue == null || systemCpuUsageValue.isJsonNull()) {
                Utils.logWarning("system_cpu_usage is null.");
                return;
            }
            long system_cpu_usage = systemCpuUsageValue.getAsLong();

            JsonElement cpu_usage_json = cpu_stats.get("cpu_usage");
            if (cpu_usage_json == null || cpu_usage_json.isJsonNull()) {
                Utils.logWarning("cpu_usage is null.");
                return;
            }
            JsonObject cpu_usage = cpu_usage_json.getAsJsonObject();

            JsonElement totalUsageValue = cpu_usage.get("total_usage");
            if (totalUsageValue == null || totalUsageValue.isJsonNull()) {
                Utils.logWarning("totalUsageValue is null.");
                return;
            }
            long total_usage = totalUsageValue.getAsLong();

            JsonElement percpu_usage_json = cpu_usage.get("percpu_usage");
            if (percpu_usage_json == null || percpu_usage_json.isJsonNull()) {
                Utils.logWarning("percpu_usage is null.");
                return;
            }
            JsonArray percpu_usage = percpu_usage_json.getAsJsonArray();

            JsonElement precpu_stats_json = stats.get("precpu_stats");
            if (precpu_stats_json == null || precpu_stats_json.isJsonNull()) {
                Utils.logWarning("precpu_stats is null.");
                return;
            }
            JsonObject precpu_stats = precpu_stats_json.getAsJsonObject();

            JsonElement system_cpu_usage_json = precpu_stats.get("system_cpu_usage");
            if (system_cpu_usage_json == null || system_cpu_usage_json.isJsonNull()) {
                Utils.logWarning("system_cpu_usage is null.");
                return;
            }
            long presystem_cpu_usage = system_cpu_usage_json.getAsLong();

            JsonElement precpu_usage_json = precpu_stats.get("cpu_usage");
            if (precpu_usage_json == null || precpu_usage_json.isJsonNull()) {
                Utils.logWarning("precpu_usage is null.");
                return;
            }
            JsonObject precpu_usage = precpu_usage_json.getAsJsonObject();

            JsonElement pretotal_usage_json = precpu_usage.get("total_usage");
            if (pretotal_usage_json == null || pretotal_usage_json.isJsonNull()) {
                Utils.logWarning("total_usage is null.");
                return;
            }

            long pretotal_usage = pretotal_usage_json.getAsLong();

            long cpuDelta = total_usage - pretotal_usage;
            long systemDelta = system_cpu_usage - presystem_cpu_usage;

            if (systemDelta > 0 && cpuDelta > 0) {
                double cpuUsage = (((double) cpuDelta / systemDelta) * percpu_usage.size()) * 100.0;
                state.cpuUsage = Math.round(cpuUsage * 100d) / 100d;
            }

        } catch (Exception e) {
            Utils.logWarning("Error during container stats CPU usage calculations: %s",
                    Utils.toString(e));
        }
    }

    private static void setContainerStopped(ContainerStats state, Map<String, JsonElement> stats) {
        try {
            JsonElement read_json = stats.get("read");
            if (read_json == null || read_json.isJsonNull()) {
                Utils.logWarning("read is null.");
                return;
            }
            String read = read_json.getAsString();
            state.containerStopped = Boolean.FALSE;
            if (CONTAINER_STOPPED_TIME.equals(read)) {
                state.containerStopped = Boolean.TRUE;
            }
        } catch (Exception e) {
            Utils.logWarning("Error during container stats status calculations: %s",
                    Utils.toString(e));
        }
    }
}
