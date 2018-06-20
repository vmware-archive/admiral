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

    String CLUSTER_NAME_PROP_NAME = "__clusterName";
    String VALIDATE_CONNECTION = "validate_connection";
    String CREDENTIALS_LINK = "credentials";
    String KUBE_CONFIG_PROP_NAME = "__kubeConfig";
    String KUBERNETES_MASTER_HOST_PROP_NAME = "kubernetes_master_host";
    String KUBERNETES_MASTER_PORT_PROP_NAME = "kubernetes_master_port";
}
