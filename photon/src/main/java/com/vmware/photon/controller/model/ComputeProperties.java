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

package com.vmware.photon.controller.model;

/**
 * Common infrastructure provider properties for compute resources manipulated by adapters.
 */
public class ComputeProperties {

    /**
     * The display name of the compute resource.
     */
    public static final String CUSTOM_DISPLAY_NAME = "displayName";

    /**
     * The resource group name to use to group the resources. E.g. on vSpehere this can be the
     * folder name, on Azure this is the resourceGroupName, on AWS this value can be used to tag the
     * resources.
     */
    public static final String RESOURCE_GROUP_NAME = "resourceGroupName";

    /**
     * Custom property to hold the link to the endpoint.
     */
    public static final String ENDPOINT_LINK_PROP_NAME = "__endpointLink";

    /**
     * The normalized OS type of the compute resource.
     * See {@link OSType} for a list of possible values.
     */
    public static final String CUSTOM_OS_TYPE = "osType";

    /**
     * A link to a compute resource where to deploy this compute.
     */
    public static final String PLACEMENT_LINK = "__placementLink";

    /**
     * A key for the custom properties property which value stores the specific type of the
     * resource state.
     * <p>
     * Useful when one resource state class can represent more than one target system type
     * (e.g. Both Azure Resource Groups and Storage containers are represented by
     * {@link com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState}
     */
    public static final String RESOURCE_TYPE_KEY = "__type";

    /**
     * A key for the custom properties property which value stores the parent compute host link.
     */
    public static final String COMPUTE_HOST_LINK_PROP_NAME = "computeHostLink";

    /**
     * A key for the custom properties property which value stores compute that has created the
     * object.
     */
    public static final String CREATE_CONTEXT_PROP_NAME = "__createContext";

    public static final String FIELD_VIRTUAL_GATEWAY = "__virtualGateway";

    /**
     * A key for the custom properties which indicates if the resource is for infrastructure use
     * only (the value is set to "true" in this case).
     */
    public static final String INFRASTRUCTURE_USE_PROP_NAME = "__infrastructureUse";

    /**
     * A key for a linked endpoint link, used to link two accounts.
     */
    public static final String LINKED_ENDPOINT_PROP_NAME = "linkedEndpointLink";

    /**
     * A key for the custom properties in compute which stores flag as whether a snapshot exists
     * for the given compute.
     */
    public static final String CUSTOM_PROP_COMPUTE_HAS_SNAPSHOTS = "__hasSnapshot";

    public enum OSType {
        WINDOWS, LINUX;
    }
}
