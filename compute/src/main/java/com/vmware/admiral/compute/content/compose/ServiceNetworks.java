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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import com.vmware.admiral.compute.container.ServiceNetwork;

/*-
 * Networks to join, referencing entries under the top-level networks key.
 *
 * Furthermore 'networks' can be serialized as just:
 *
 *   networks:
 *     - some-network
 *     - other-network
 *
 * or with aliases and/or IPs as:
 *
 *   networks:
 *     some-network:
 *       aliases:
 *         - alias1
 *         _ alias3
 *     other-network:
 *       aliases:
 *         - alias2
 *       ipv4_address: 172.16.238.10
 *       ipv6_address: 2001:3984:3989::10
 *
 * See https://docs.docker.com/compose/compose-file/#/networks
 */
@JsonDeserialize(using = ServiceNetworksDeserializer.class)
@JsonSerialize(using = ServiceNetworksSerializer.class)
public class ServiceNetworks {

    public String[] values;

    public Map<String, ServiceNetwork> valuesMap;
}
