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
 * Request to enumerate images per end-point. The {@code resourceReference} value is the URI to the
 * end-point.
 */
public class ImageEnumerateRequest extends ResourceRequest {

    /**
     * Image enumeration request type.
     */
    public enum ImageEnumerateRequestType {
        /**
         * Instruct the adapter to enumerate images that are public/global for all end-points of
         * passed end-point type.
         */
        PUBLIC,
        /**
         * Instruct the adapter to enumerate images that are private/specific for passed end-point.
         */
        PRIVATE
    }

    /**
     * Image enumeration request type.
     */
    public ImageEnumerateRequestType requestType;

    /**
     * Image enumeration action type.
     */
    public EnumerationAction enumerationAction;

}
