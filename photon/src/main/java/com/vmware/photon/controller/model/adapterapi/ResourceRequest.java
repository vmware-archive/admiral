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

import com.vmware.xenon.common.UriUtils;

/**
 * Base class for all Request types to the Adapters.
 */
public abstract class ResourceRequest {

    /**
     * The URI of resource instance in whose context this request is initiated
     */
    public URI resourceReference;

    /**
     * URI reference to calling task.
     */
    public URI taskReference;

    /**
     * Value indicating whether the service should treat this as a mock request and complete the
     * work flow without involving the underlying compute host infrastructure.
     */
    public boolean isMockRequest;

    /**
     * A method build URI to a given resource by it's relative link.
     *
     * @param resourceLink
     *            the link to a given resource.
     * @return returns the full URI the resource under passed resourceLink parameter.
     */
    public URI buildUri(String resourceLink) {
        return UriUtils.buildUri(this.resourceReference, resourceLink);
    }

    /**
     * Returns the link to the resourceReference.
     */
    public String resourceLink() {
        return this.resourceReference.getPath();
    }

    /**
     * Returns the link to the calling Task.
     */
    public String taskLink() {
        return this.taskReference.getPath();
    }
}
