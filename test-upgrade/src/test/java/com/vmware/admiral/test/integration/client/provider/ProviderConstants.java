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

package com.vmware.admiral.test.integration.client.provider;

public interface ProviderConstants {
    /**
     * Operation that will cleanup remove all resources created as part of a given request.
     */
    String REMOVE_RESOURCE_OPERATION = "REMOVE_RESOURCE";

    /**
     * Operation that will add containers to a deployment.
     */
    String CLUSTER_RESOURCE_OPERATION = "CLUSTER_RESOURCE";

    /**
     * Flag that tells if this is a clustering request
     */
    String CLUSTERING_OPERATION_CUSTOM_PROP = "__clustering_operation";

    /**
     * Type for deleting container host with remove resource operation.
     */
    String CONTAINER_HOST_TYPE = "CONTAINER_HOST";
    /**
     * Post allocation provision operation. This is the operation that will be performed after the
     * initial allocation based on {@link ProviderConstants#FIELD_NAME_ALLOCATION_REQUEST}.
     */
    String POST_ALLOCATION_PROVISION_OPERATION_ID = "Container.Create";

    /**
     * Post allocation closure provision operation. This is the operation that will be performed
     * after the initial allocation based on {@link ProviderConstants#FIELD_NAME_ALLOCATION_REQUEST}
     * .
     */
    String POST_ALLOCATION_CLOSURE_OPERATION_ID = "Closure.Create";

    /**
     * Property field name indicating whether a give request is an allocation one - meaning that the
     * container states will be created and allocated but no actual provisioning will take place.
     */
    String FIELD_NAME_ALLOCATION_REQUEST = "__allocation_request";

    /**
     * Property field name indicating whether a give request is a deallocation one - meaning that
     * the container states will be deleted and deallocated.
     */
    String FIELD_NAME_DEALLOCATION_REQUEST = "__deallocation_request";

    /**
     * Property field name indicating whether a give request is for a compute one.
     */
    String FIELD_NAME_COMPUTE_REQUEST = "__compute_request";

    /**
     * Property field name indicating whether a give request is for a container host one.
     */
    String FIELD_NAME_CONTAINER_HOST_REQUEST = "__container_host_request";

    /**
     * Property field name indicating whether a give request is for a container volume one.
     */
    String FIELD_NAME_CONTAINER_VOLUME_REQUEST = "__container_volume_request";

    /**
     * Property field name indicating whether a give request is for a closure one.
     */
    String FIELD_NAME_CLOSURE_REQUEST = "__closure_request";

    /**
     * Context id that spreads across multiple allocation request as part of multi-container
     * composite deployment.
     */
    String FIELD_NAME_CONTEXT_ID_KEY = "__composition_context_id";

    /**
     * Field name representing the cluster size.
     */
    String FIELD_NAME_CLUSTER_SIZE_KEY = "clusterSize";

    /**
     * Field name for the image to be used for the compute.
     */
    String FIELD_NAME_COMPUTE_IMAGE_ID_KEY = "imageType";

    /**
     * Field name for the display name to be used for the compute.
     */
    String FIELD_NAME_COMPUTE_NAME_KEY = "displayName";

    /**
     * Post allocation provision operation. This is the operation that will be performed after the
     * initial allocation based on {@link ProviderConstants#FIELD_NAME_ALLOCATION_REQUEST}.
     */
    String POST_ALLOCATION_NETWORK_PROVISION_OPERATION_ID = "Network.Create";

    /**
     * Prevents external networks from being removed when container applications get destroyed
     */
    String EXTERNAL_INSPECT_ONLY_CUSTOM_PROPERTY = "__externalInspectOnly";

    /**
     * Property field name indicating whether a give request is for a compute one.
     */
    String FIELD_NAME_COMPUTE_NETWORK_REQUEST = "__compute_network_request";
}
