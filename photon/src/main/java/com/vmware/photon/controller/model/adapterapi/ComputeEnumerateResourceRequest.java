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

import java.net.URI;

/**
 * Request to enumerate instantiated resources. The {@code resourceReference} value is the URI to
 * the parent compute host.
 */
public class ComputeEnumerateResourceRequest extends ResourceRequest {

    /**
     * Uri reference of the resource pool.
     */
    public String resourcePoolLink;

    /**
     * Enumeration Action Start, stop, refresh.
     */
    public EnumerationAction enumerationAction;

    /**
     * Reference to the management endpoint of the compute provider.
     */
    public URI adapterManagementReference;

    /**
     * If set to true, the adapter must not delete the missing resources, but set their
     * {@link com.vmware.photon.controller.model.resources.ComputeService.ComputeState#lifecycleState}
     * field to
     * {@link com.vmware.photon.controller.model.resources.ComputeService.LifecycleState#RETIRED}
     */
    public boolean preserveMissing;

    /**
     * Link reference to the cloud account endpoint of this host
     */
    public String endpointLink;

    /**
     * Return a key to uniquely identify enumeration for endpoint and resource pool instance.
     */
    public String getEnumKey() {
        return "endpoint:[" + this.endpointLink + "],pool:[" + this.resourcePoolLink + "]";
    }
}
