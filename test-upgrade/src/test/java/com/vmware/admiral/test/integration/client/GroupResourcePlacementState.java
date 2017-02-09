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

package com.vmware.admiral.test.integration.client;

import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * GroupResourcePlacementState
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:GroupResourcePlacementService:GroupResourcePlacementState")
public class GroupResourcePlacementState extends TenantedServiceDocument {
    /** Name of the reservation. */
    public String name;

    /** {@link ResourcePoolState} link */
    public String resourcePoolLink;

    /**
     * Link to the deployment policy of this policy. If the same policy is set to a container
     * description, then that description should be provisioned from this policy.
     */
    public String deploymentPolicyLink;

    /** The priority with which the group resource policies will be applied */
    public int priority;

    /** The maximum number of resource instances for this policy for a group */
    public long maxNumberInstances;

    /** Memory limit in bytes per group for a resource pool. */
    public long memoryLimit;

    /** Storage limit in bytes per group for a resource pool. */
    public long storageLimit;

    /**
     * Percentages of the relative CPU sharing in a given resource pool. This is not an actual limit
     * but a guideline of how much CPU should be divided among all containers running at a given
     * time.
     */
    public int cpuShares;

    /** Custom properties. */
    public Map<String, String> customProperties;

    // Set by the system (readonly)
    /**
     * Internally set by the system. The number of resource instances currently available to be
     * allocated
     */
    public long availableInstancesCount;

    // Set by the system (readonly)
    /**
     * Internally set by the system. The number of resource instances currently allocated.
     */
    public long allocatedInstancesCount;

    /**
     * Set by the ReservationTask service. The number of used instances linked to the Resource
     * Descriptions of those instances.
     */
    public Map<String, Long> resourceQuotaPerResourceDesc;
}
