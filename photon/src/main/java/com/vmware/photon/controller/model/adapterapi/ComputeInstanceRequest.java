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
 * Request to create a compute host. The host reference provides detailed
 * information on the host type and required services to complete the request.
 */
public class ComputeInstanceRequest extends ResourceRequest {

    /**
     * Instance request type.
     */
    public enum InstanceRequestType {
        CREATE,
        DELETE,
        VALIDATE_CREDENTIALS
    }

    /**
     * Request type.
     */
    public InstanceRequestType requestType;

    /**
     * Auth credentials. Used for validation of a host.
     */
    public String authCredentialsLink;

    /**
     * Region Id.
     */
    public String regionId;
}
