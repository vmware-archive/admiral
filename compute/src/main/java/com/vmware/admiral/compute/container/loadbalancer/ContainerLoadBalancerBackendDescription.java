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

package com.vmware.admiral.compute.container.loadbalancer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Container load balancer backend, represents an endpoint to which the traffic should be
 * redirected.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContainerLoadBalancerBackendDescription {

    /**
     * Name of the linked service
     */
    public String service;

    public int port;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ContainerLoadBalancerBackendDescription that = (ContainerLoadBalancerBackendDescription) o;

        return new EqualsBuilder()
                .append(service, that.service)
                .append(port, that.port)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(service)
                .append(port)
                .toHashCode();
    }
}
