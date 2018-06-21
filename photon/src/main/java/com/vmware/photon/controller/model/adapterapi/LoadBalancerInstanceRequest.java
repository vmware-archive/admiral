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

package com.vmware.photon.controller.model.adapterapi;

/**
 * Request to create/delete a load balancer instance.
 */
public class LoadBalancerInstanceRequest extends ResourceRequest {
    /**
     * Type of an instance request.
     */
    public enum InstanceRequestType {
        CREATE, UPDATE, DELETE
    }

    /**
     * The type of the instance request.
     */
    public InstanceRequestType requestType;
}
