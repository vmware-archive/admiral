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

package com.vmware.admiral.compute.kubernetes.entities;

/**
 * Lifecycle describes actions that the management system should take in response to container
 * lifecycle events. For the PostStart and PreStop lifecycle handlers, management of the container
 * blocks until the action is complete, unless the container process fails, in which
 * case the handler is aborted.
 */
public class Lifecycle {

    /**
     * PostStart is called immediately after a container is created.
     * If the handler fails, the container is terminated and restarted according to its restart policy.
     */
    public Handler podStart;

    /**
     * PreStop is called immediately before a container is terminated.
     * The container is terminated after the handler completes.
     */
    public Handler preStop;
}
