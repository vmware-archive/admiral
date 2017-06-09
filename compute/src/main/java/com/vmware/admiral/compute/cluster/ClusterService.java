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

package com.vmware.admiral.compute.cluster;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.StatelessService;

public class ClusterService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.CLUSTERS;

    public enum ClusterType {
        DOCKER,
        VCH
    }

    public enum ClusterStatus {
        ON,
        OFF,
        DISABLED,
        WARNING
    }

    public static class ClusterDto {
        /** The name of a given cluster. */
        public String name;

        /** The type of hosts the cluster contains. */
        public ClusterType type;

        /** The status of the cluster. */
        public ClusterStatus status;

        /** (Optional) the address of the VCH cluster. */
        public String address;

        /** The number of containers in the cluster. */
        public long containerCount;

        public long totalMemory;

        public long memoryUsage;

        public long totalCpu;

        public long cpuUsage;
    }
}
