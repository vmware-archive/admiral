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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/*-
 * If set to true, specifies that this network has been created outside of Compose.
 * 'external' cannot be used in conjunction with other network configuration keys (driver,
 * driver_opts, ipam).
 *
 * Furthermore 'external' can be serialized as just:
 *
 *   external: true
 *
 * or as:
 *
 *   external:
 *     name: actual-name-of-network
 *
 * See https://docs.docker.com/compose/compose-file/#/network-configuration-reference
 */
@JsonDeserialize(using = NetworkExternalDeserializer.class)
@JsonSerialize(using = NetworkExternalSerializer.class)
public class NetworkExternal {

    public Boolean value;

    public String name;
}
