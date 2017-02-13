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

public enum VolumeOperationType {

    CREATE("Volume.Create"),
    LIST_VOLUMES("Volume.List"),
    DELETE("Volume.Delete"),
    INSPECT("Volume.Inspect"),
    DISCOVER_VMDK_DATASTORE("Volume.DiscoverVmdkDatastore");

    VolumeOperationType(String id) {
        this.id = id;
    }

    private static final Map<String, VolumeOperationType> operationsById = new HashMap<>();

    static {
        for (VolumeOperationType opr : values()) {
            operationsById.put(opr.id, opr);
        }
    }

    public final String id;

    public static VolumeOperationType instanceById(String id) {
        if (id == null) {
            return null;
        }
        return operationsById.get(id);
    }

    public static String extractDisplayName(String id) {
        return id.substring(id.lastIndexOf(".") + 1);
    }

    @Override
    public String toString() {
        return id;
    }

}
