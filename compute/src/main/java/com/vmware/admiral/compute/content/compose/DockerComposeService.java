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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * Docker Compose service description
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DockerComposeService {

    public String image;

    @JsonDeserialize(converter = StringOrListToArrayConverter.class)
    public String[] command;

    public String user;

    public Long mem_limit;

    public Long memswap_limit;

    public Integer cpu_shares;

    public String[] dns;

    public String[] dns_search;

    @JsonDeserialize(converter = ArrayOrDictionaryToArrayConverter.class)
    public String[] environment;

    public String[] entrypoint;

    public String[] volumes;

    public String volume_driver;

    public String working_dir;

    public Boolean privileged;

    public String hostname;

    public String domainname;

    public String[] extra_hosts;

    public String[] ports;

    public Logging logging;

    public String[] links;

    public String[] volumes_from;

    public String[] cap_add;

    public String[] cap_drop;

    public String restart;

    public String network_mode;

    public ServiceNetworks networks;

    public String pid;

    public String[] devices;

    public String[] depends_on;

    public String container_name;
}
