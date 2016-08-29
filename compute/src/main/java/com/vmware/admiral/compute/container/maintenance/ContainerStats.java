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

import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceStats;
import com.vmware.xenon.common.ServiceStats.ServiceStat;
import com.vmware.xenon.common.Utils;

/**
 * Container stats objecting keeping the current operational metrics about a given Container.
 */
public class ContainerStats {
    public static final String KIND = Utils.buildKind(ContainerStats.class);

    public static final String FIELD_NAME_CPU_USAGE = "cpuUsage";
    public static final String FIELD_NAME_MEM_LIMIT = "memLimit";
    public static final String FIELD_NAME_MEM_USAGE = "memUsage";
    public static final String FIELD_NAME_NETWORK_IN = "networkIn";
    public static final String FIELD_NAME_NETWORK_OUT = "networkOut";
    public static final String FIELD_NAME_HEALTH_SUCCESS_COUNT = "healthSuccessCount";
    public static final String FIELD_NAME_HEALTH_FAILURE_COUNT = "healthFailureCount";
    public static final String FIELD_NAME_HEALTH_CHECK_SUCCESS = "healthCheckSuccess";
    public static final String FIELD_NAME_CONTAINER_STOPPED = "containerStopped";

    /**
     * A structured string identifier for the document type
     *
     * Infrastructure use only
     */
    public String documentKind = KIND;

    /** Percent of CPU utilization at a given moment */
    public double cpuUsage;

    /** Memory limit in bytes */
    public long memLimit;

    /** Current memory usage in bytes */
    public long memUsage;

    /** Received network traffic in bytes */
    public long networkIn;

    /** Sent network traffic in bytes */
    public long networkOut;

    /** count of how many successful health checks has been performed  */
    public int healthSuccessCount;

    /** count of how many failures in a row based on health check */
    public int healthFailureCount;

    /** indicator if the current health check is successful */
    public Boolean healthCheckSuccess;

    /** indicator if the container is stopped */
    public Boolean containerStopped;

    public void setStats(Service service) {
        if (cpuUsage != 0) {
            service.setStat(FIELD_NAME_CPU_USAGE, cpuUsage);
        }
        if (memUsage != 0) {
            service.setStat(ContainerStats.FIELD_NAME_MEM_USAGE, memUsage);
        }
        if (memLimit != 0) {
            service.setStat(ContainerStats.FIELD_NAME_MEM_LIMIT, memLimit);
        }
        if (networkIn != 0) {
            service.setStat(ContainerStats.FIELD_NAME_NETWORK_IN, networkIn);
        }
        if (networkOut != 0) {
            service.setStat(ContainerStats.FIELD_NAME_NETWORK_OUT, networkOut);
        }

        if (healthCheckSuccess != null) {
            service.setStat(ContainerStats.FIELD_NAME_HEALTH_SUCCESS_COUNT, healthSuccessCount);
            service.setStat(ContainerStats.FIELD_NAME_HEALTH_FAILURE_COUNT, healthFailureCount);
            service.setStat(ContainerStats.FIELD_NAME_HEALTH_CHECK_SUCCESS,
                    healthCheckSuccess ? 1 : 0);
        }
        if (containerStopped != null) {
            service.setStat(ContainerStats.FIELD_NAME_CONTAINER_STOPPED, containerStopped ? 1 : 0);
        }
    }

    public static ContainerStats transform(Service service) {
        ContainerStats containerStats = new ContainerStats();
        containerStats.cpuUsage = getValue(service, ContainerStats.FIELD_NAME_CPU_USAGE);
        containerStats.memUsage = (long) getValue(service, ContainerStats.FIELD_NAME_MEM_USAGE);
        containerStats.memLimit = (long) getValue(service, ContainerStats.FIELD_NAME_MEM_LIMIT);
        containerStats.networkIn = (long) getValue(service,
                ContainerStats.FIELD_NAME_NETWORK_IN);
        containerStats.networkOut = (long) getValue(service,
                ContainerStats.FIELD_NAME_NETWORK_OUT);
        containerStats.healthSuccessCount = (int) getValue(service,
                ContainerStats.FIELD_NAME_HEALTH_SUCCESS_COUNT);
        containerStats.healthFailureCount = (int) getValue(service,
                ContainerStats.FIELD_NAME_HEALTH_FAILURE_COUNT);
        containerStats.healthCheckSuccess = getValue(service,
                ContainerStats.FIELD_NAME_HEALTH_CHECK_SUCCESS) == 1 ? Boolean.TRUE : Boolean.FALSE;
        containerStats.containerStopped = getValue(service,
                ContainerStats.FIELD_NAME_CONTAINER_STOPPED) == 1 ? Boolean.TRUE : Boolean.FALSE;
        return containerStats;
    }

    public static ContainerStats transform(ServiceStats serviceStats) {
        ContainerStats containerStats = new ContainerStats();
        containerStats.cpuUsage = getValue(serviceStats, ContainerStats.FIELD_NAME_CPU_USAGE);
        containerStats.memUsage = (long) getValue(serviceStats, ContainerStats.FIELD_NAME_MEM_USAGE);
        containerStats.memLimit = (long) getValue(serviceStats, ContainerStats.FIELD_NAME_MEM_LIMIT);
        containerStats.networkIn = (long) getValue(serviceStats,
                ContainerStats.FIELD_NAME_NETWORK_IN);
        containerStats.networkOut = (long) getValue(serviceStats,
                ContainerStats.FIELD_NAME_NETWORK_OUT);
        containerStats.healthSuccessCount = (int) getValue(serviceStats,
                ContainerStats.FIELD_NAME_HEALTH_SUCCESS_COUNT);
        containerStats.healthFailureCount = (int) getValue(serviceStats,
                ContainerStats.FIELD_NAME_HEALTH_FAILURE_COUNT);
        containerStats.healthCheckSuccess = getValue(serviceStats,
                ContainerStats.FIELD_NAME_HEALTH_CHECK_SUCCESS) == 1 ? Boolean.TRUE : Boolean.FALSE;
        containerStats.containerStopped = getValue(serviceStats,
                ContainerStats.FIELD_NAME_CONTAINER_STOPPED) == 1 ? Boolean.TRUE : Boolean.FALSE;

        return containerStats;
    }

    private static double getValue(ServiceStats serviceStats, String fieldName) {
        ServiceStat serviceStat = serviceStats.entries.get(fieldName);
        if (serviceStat == null) {
            return 0;
        }
        return serviceStat.latestValue;
    }

    private static double getValue(Service service, String fieldName) {
        ServiceStat serviceStat = service.getStat(fieldName);
        if (serviceStat == null) {
            return 0;
        }
        return serviceStat.latestValue;
    }
}
