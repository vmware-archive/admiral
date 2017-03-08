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

import java.io.Serializable;
import java.util.EnumSet;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * ResourcePoolState
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:photon:controller:model:resources:ResourcePoolService:ResourcePoolState")
public class ResourcePoolState extends TenantedServiceDocument implements Serializable {
    private static final long serialVersionUID = 1L;

    public static enum ResourcePoolProperty {
        ELASTIC, HYBRID;
    }

    public String id;

    public String name;

    public String projectName;

    public EnumSet<ResourcePoolProperty> properties;

    public long minCpuCount;

    public long minGpuCount;

    public long minMemoryBytes;

    public long minDiskCapacityBytes;

    public long maxCpuCount;

    public long maxGpuCount;

    public long maxMemoryBytes;

    public long maxDiskCapacityBytes;

    public Double maxCpuCostPerMinute;

    public Double maxDiskCostPerMinute;

    public String currencyUnit;

    public Map<String, String> customProperties;

    public String computeResourceQueryLink;
}
