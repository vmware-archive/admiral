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

/**
 * Set of constants related with PKS
 */
public interface PKSConstants {

    String PKS_CLUSTER_NAME_PROP_NAME = "__pksClusterName";
    String PKS_ENDPOINT_PROP_NAME = "__pksEndpoint";
    String VALIDATE_CONNECTION = "validate_connection";

    String CREDENTIALS_LINK = "credentials";
    String KUBE_CONFIG_PROP_NAME = "__kubeConfig";

    String PKS_MASTER_HOST_FIELD = "kubernetes_master_host";
    String PKS_MASTER_PORT_FIELD = "kubernetes_master_port";
    String PKS_WORKER_INSTANCES_FIELD = "kubernetes_worker_instances";
    String PKS_AUTHORIZATION_MODE_FIELD = "authorization_mode";
    String PKS_WORKER_HAPROXY_FIELD = "worker_haproxy_ip_addresses";
    String PKS_PLAN_NAME_FIELD = "plan_name";

    String PKS_LAST_ACTION_CREATE = "CREATE";
    String PKS_LAST_ACTION_UPDATE = "UPDATE";
    String PKS_LAST_ACTION_DELETE = "DELETE";

    String PKS_LAST_ACTION_STATE_SUCCEEDED = "succeeded";
    String PKS_LAST_ACTION_STATE_IN_PROGRESS = "in progress";
    String PKS_LAST_ACTION_STATE_FAILED = "failed";

    String PKS_CLUSTER_UUID_PROP_NAME = "__pksClusterUUID";
    String PKS_CLUSTER_PLAN_NAME_PROP_NAME = "__pksPlanName";
    String PKS_CLUSTER_EXISTS_PROP_NAME = "__clusterExists";

    String PKS_CLUSTER_STATUS_RESIZING_PROP_NAME = "__pksClusterStatusResizing";
    String PKS_CLUSTER_STATUS_REMOVING_PROP_NAME = "__pksClusterStatusRemoving";

    String PKS_ENDPOINT_QUERY_PARAM_NAME = "endpointLink";
    String PKS_CLUSTER_QUERY_PARAM_NAME = "cluster";

    String PKS_PREFER_MASTER_IP_PROP_NAME = "__preferMasterIP";
    String PKS_MASTER_NODES_IPS_PROP_NAME = "__masterNodesIPs";
}
