/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.kubernetes.mock;

import com.vmware.admiral.adapter.kubernetes.ApiUtil;
import com.vmware.admiral.adapter.kubernetes.KubernetesRemoteApiClient;

/**
 * Constants for stub kubernetes host and services
 */
public interface KubernetesPathConstants {
    String BASE_PATH = "/stub/kubernetes";
    String BASE_FAILING_PATH = "/stub/failing-kubernetes";
    String PING = KubernetesRemoteApiClient.pingPath;
    String API_V1 = ApiUtil.API_PREFIX_V1;
    String NAMESPACES = API_V1 + "/namespaces";
    String PODS = "/pods";
    String NODES = API_V1 + "/nodes";
    String SERVICES = "/services";
    String DEPLOYMENTS = "/deployments";
    String REPLICATION_CONTROLLERS = "/replicationcontrollers";
    String REPLICA_SETS = "/replicasets";

    String DASHBOARD_PROXY_FOR_STATS = API_V1 +
            "/namespaces/kube-system/services/kubernetes-dashboard/proxy/api/v1/node/";
}
