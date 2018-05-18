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

package com.vmware.admiral.adapter.pks.entities;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class PKSCluster {

    public static final String PARAMETER_MASTER_HOST = "kubernetes_master_host";
    public static final String PARAMETER_MASTER_PORT = "8443";
    public static final String PARAMETER_WORKER_PROXY_IP_ADDRESSES = "worker_haproxy_ip_addresses";
    public static final String PARAMETER_WORKER_INSTANCES = "kubernetes_worker_instances";
    public static final String PARAMETER_AUTHORIZATION_MODE = "authorization_mode";

    @SerializedName("name")
    public String name;

    @SerializedName("uuid")
    public String uuid;

    @SerializedName("plan_name")
    public String planName;

    @SerializedName("last_action")
    public String lastAction;

    @SerializedName("last_action_state")
    public String lastActionState;

    @SerializedName("last_action_description")
    public String lastActionDescription;

    @SerializedName("kubernetes_master_ips")
    public String[] masterIPs;

    @SerializedName("parameters")
    public Map<String, Object> parameters;

}
