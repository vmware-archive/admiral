/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.pks;

import java.util.HashMap;

public enum PKSOperationType {

    LIST_CLUSTERS("PKS.ListClusters"),
    GET_CLUSTER("PKS.GetCluster"),
    CREATE_USER("PKS.CreateUser"),
    CREATE_CLUSTER("PKS.CreateCluster"),
    DELETE_CLUSTER("PKS.DeleteCluster"),
    RESIZE_CLUSTER("PKS.ResizeCluster"),
    LIST_PLANS("PKS.ListPlans");

    public final String id;

    PKSOperationType(String id) {
        this.id = id;
    }

    private static final HashMap<String, PKSOperationType> operationsById = new HashMap<>();

    static {
        for (PKSOperationType opr : values()) {
            operationsById.put(opr.id, opr);
        }
    }

    public static PKSOperationType instanceById(String id) {
        if (id == null) {
            return null;
        }
        return operationsById.get(id);
    }

    public String getDisplayName() {
        return id.substring(id.lastIndexOf(".") + 1);
    }

    @Override
    public String toString() {
        return id;
    }

}
