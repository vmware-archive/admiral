/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.iaas.consumer.api.common;

public class Constants {

    public static final String NOT_YET_IMPLEMENTED_MESSAGE = "Not Yet Implemented";

    // Custom properties (keys) supported by the Prelude API
    public static class CustomPropertyKeys {

        // A context id for the API that is set into every object that is created through the API.
        // The API user can set this id in the header of a request to tie a set of entities into a
        // single API context. The name of this header field is 'x-vra-api-context-id'.
        public static final String API_CONTEXT_ID = "api-context-id";
    }

    // URI Paths
    public static class UriPathElements {
        public static final String MACHINES_PREFIX = "/machines";
        public static final String NETWORK_INTERFACES_PREFIX = "/network-interfaces";
        public static final String MACHINE_DISKS_PREFIX = "/machine-disks";
        public static final String SPECIFICATION_PREFIX = "/specification";
        public static final String IMAGES_PREFIX = "/images";
        public static final String LOAD_BALANCERS_PREFIX = "/load-balancers";
        public static final String NETWORKS_PREFIX = "/networks";
        public static final String BLOCK_DEVICES_PREFIX = "/block-devices";

    }

}
