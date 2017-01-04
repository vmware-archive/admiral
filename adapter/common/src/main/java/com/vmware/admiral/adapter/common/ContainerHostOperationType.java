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

package com.vmware.admiral.adapter.common;

import java.util.HashMap;
import java.util.Map;

public enum ContainerHostOperationType {
    INFO("Host.Container.Info"),
    VERSION("Host.Container.Version"),
    PING("Host.Container.Ping"),
    LIST_CONTAINERS("Host.Container.ListContainers"),
    LIST_NETWORKS("Host.Network.ListNetworks"),
    LIST_VOLUMES("Host.Volume.ListVolumes"),
    STATS("Host.Container.Stats");

    ContainerHostOperationType(String id) {
        this.id = id;
    }

    private static final Map<String, ContainerHostOperationType> operationsById = new HashMap<>();

    static {
        for (ContainerHostOperationType opr : values()) {
            operationsById.put(opr.id, opr);
        }
    }

    public final String id;

    public static ContainerHostOperationType instanceById(String id) {
        if (id == null) {
            return null;
        }

        return operationsById.get(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
