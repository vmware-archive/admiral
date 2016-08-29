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

package com.vmware.admiral.compute.content.compose;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.compute.container.network.Ipam;

/**
 * Docker Compose network description
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerComposeNetwork {

    public String driver;

    public Map<String, String> driver_opts;

    public Ipam ipam;

    public NetworkExternal external;
}
