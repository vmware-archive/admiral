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

package com.vmware.admiral.compute.kubernetes.entities.services;

import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;

/**
 * Service is a named abstraction of software service (for example, mysql) consisting of local
 * port (for example 3306) that the proxy listens on, and the selector that determines
 * which pods will answer requests sent through the proxy.
 */
public class Service extends BaseKubernetesObject {

    /**
     * Spec defines the behavior of a service.
     */
    public ServiceSpec spec;

    /**
     * Most recently observed status of the service. Populated by the system. Read-only.
     */
    public ServiceStatus status;
}
