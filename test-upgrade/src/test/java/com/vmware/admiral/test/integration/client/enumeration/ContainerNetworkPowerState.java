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

package com.vmware.admiral.test.integration.client.enumeration;

/**
 * Container status indicating the running status of a container instance.
 */
public enum ContainerNetworkPowerState {

    UNKNOWN("unknown"), PROVISIONING("provisioning"), CONNECTED("connected"), RETIRED(
            "retired"), ERROR("error");

    public static final String KEY_PREFIX = "container.network.state.";

    private final String id;

    private ContainerNetworkPowerState(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
