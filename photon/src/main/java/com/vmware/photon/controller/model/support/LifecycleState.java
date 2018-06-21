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

package com.vmware.photon.controller.model.support;

/**
 * Lifecycle state of a resource.
 * <p>
 * Note: a resource might not support all of the available states.
 */
public enum LifecycleState {
    /**
     * The resource is being provisioned.
     */
    PROVISIONING,
    /**
     * The resource is ready for use.
     */
    READY,
    /**
     * The resource is currently suspended.
     */
    SUSPEND,
    /**
     * The resource is currently stopped.
     */
    STOPPED,
    /**
     * The resource is retired.
     */
    RETIRED
}
