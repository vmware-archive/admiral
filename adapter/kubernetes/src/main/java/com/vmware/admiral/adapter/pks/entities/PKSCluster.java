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

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "Returned from the PKS API.")
public class PKSCluster {

    public static final String PARAMETER_MASTER_HOST = "kubernetes_master_host";
    public static final String PARAMETER_MASTER_PORT = "8443";
    public static final String PARAMETER_WORKER_PROXY_IP_ADDRESSES = "worker_haproxy_ip_addresses";
    public static final String PARAMETER_WORKER_INSTANCES = "kubernetes_worker_instances";
    public static final String PARAMETER_AUTHORIZATION_MODE = "authorization_mode";

    @SerializedName("name")
    @ApiModelProperty(
            value = "The name of the PKS Cluster.")
    public String name;

    @SerializedName("uuid")
    public String uuid;

    @SerializedName("plan_name")
    @ApiModelProperty(
            value = "The name of the plan, which defines the set of worker and master nodes.")
    public String planName;

    @SerializedName("last_action")
    @ApiModelProperty(
            value = "The last action performed over the cluster.",
            example = "CREATE")
    public String lastAction;

    @SerializedName("last_action_state")
    @ApiModelProperty(
            value = "The state of the last action performed over the cluster, which is either succeeded, " +
                    "failed or in progress.")
    public String lastActionState;

    @SerializedName("last_action_description")
    @ApiModelProperty(
            value = "Additional details for the last action performed over the cluster. (e.g. reason for failure)")
    public String lastActionDescription;

    @SerializedName("kubernetes_master_ips")
    @ApiModelProperty(
            value = "List of the master node IPs.")
    public String[] masterIPs;

    @SerializedName("parameters")
    @ApiModelProperty(
            value = "Contains the master host name, master port and other cluster information.")
    public Map<String, Object> parameters;

}
