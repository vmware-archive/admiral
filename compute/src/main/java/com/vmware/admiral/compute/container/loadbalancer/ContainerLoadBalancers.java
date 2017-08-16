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

public class ContainerLoadBalancers {
    public static final String LOAD_BALANCER_CONTAINER_NAME_PREFIX = "admiral_load_balancer";
    public static final String LOAD_BALANCER_IMAGE_NAME = System.getProperty(
            "admiral.management.images.load-balancer.name", "vmware/load_balancer:1.0.0");
    public static final String LOAD_BALANCER_TAR_FILENAME = "load_balancer";
    public static final String LOAD_BALANCER_IMAGE_REFERENCE = System.getProperty(
            "admiral.management.images.load-balancer.reference", LOAD_BALANCER_TAR_FILENAME + "" +
                    ".tar.xz");
    /* Custom property used to backtrace the ContainerLoadBalancerDescription from the
    * ContainerDescription
    */
    public static final String CONTAINER_LOAD_BALANCER_DESCRIPTION_LINK = "lb-description-link";
}
