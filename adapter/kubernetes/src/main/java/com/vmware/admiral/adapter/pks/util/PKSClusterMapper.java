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

package com.vmware.admiral.adapter.pks.util;

import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_AUTHORIZATION_MODE_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_CLUSTER_NAME_PROP_NAME;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_HOST_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_MASTER_PORT_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_PLAN_NAME_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_WORKER_HAPROXY_FIELD;
import static com.vmware.admiral.adapter.pks.PKSConstants.PKS_WORKER_INSTANCES_FIELD;

import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.adapter.pks.entities.PKSCluster;
import com.vmware.xenon.common.Utils;

public class PKSClusterMapper {

    private PKSClusterMapper() {
    }

    public static PKSCluster fromMap(Map<String, String> map) {

        PKSCluster c = new PKSCluster();
        c.name = map.get(PKS_CLUSTER_NAME_PROP_NAME);
        c.planName = map.get(PKS_PLAN_NAME_FIELD);

        c.parameters = new HashMap<>(8);
        c.parameters.put(PKS_MASTER_HOST_FIELD, map.get(PKS_MASTER_HOST_FIELD));
        if (map.containsKey(PKS_MASTER_PORT_FIELD)) {
            c.parameters.put(PKS_MASTER_PORT_FIELD, getAsInt(map, PKS_MASTER_PORT_FIELD));
        }
        c.parameters.put(PKS_WORKER_INSTANCES_FIELD, getAsInt(map, PKS_WORKER_INSTANCES_FIELD));
        c.parameters.put(PKS_WORKER_HAPROXY_FIELD, map.get(PKS_WORKER_HAPROXY_FIELD));
        c.parameters.put(PKS_AUTHORIZATION_MODE_FIELD, map.get(PKS_AUTHORIZATION_MODE_FIELD));

        return c;
    }

    private static Integer getAsInt(Map<String, String> map, String key) {
        String s;
        if (map != null && (s = map.get(key)) != null) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                Utils.logWarning("Error parsing '%s' for key %s to integer, reason: %s",
                        s, key, e.getMessage());
            }
        }
        return null;
    }

}
