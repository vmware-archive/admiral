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

import java.util.Map;

/**
 * Request to create/destroy a security group instance on a given compute.
 */
public class SecurityGroupInstanceRequest extends ResourceRequest {

    /**
     * Instance Request type.
     */
    public enum InstanceRequestType {
        CREATE, DELETE
    }

    /**
     * Destroy or create a security group instance on the given compute.
     */
    public InstanceRequestType requestType;

    /**
     * Link to secrets.
     */
    public String authCredentialsLink;

    /**
     * The resource pool the security group exists in.
     */
    public String resourcePoolLink;

    /**
     * Custom properties related to the security group instance.
     */
    public Map<String, String> customProperties;
}
