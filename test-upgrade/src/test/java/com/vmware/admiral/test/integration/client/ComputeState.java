/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration.client;

import java.net.URI;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.vmware.admiral.test.integration.client.ComputeDescription.ComputeType;
import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;
import com.vmware.admiral.test.integration.client.enumeration.ContainerSchemaConstants;
import com.vmware.admiral.test.integration.client.provider.ProviderConstants;

/**
 * Represents a compute resource (Machine, VM or Cloud Compute resource) that host containers.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@DcpDocumentKind("com:vmware:photon:controller:model:resources:ComputeService:ComputeState")
public class ComputeState extends ProvisionableServiceDocument {
    public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";
    public static final String FIELD_NAME_RESOURCE_POOL_LINK = "resourcePoolLink";
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";
    public static final String FIELD_NAME_PARENT_LINK = "parentLink";
    public static final String FIELD_NAME_ID_LINK = "id";

    public static final String CUSTOM_PROP_KEY_GROUP = "__group";
    public static final String CUSTOM_PROP_KEY_NAME = "__name";
    public static final String CUSTOM_PROP_KEY_CONTAINER_TYPE = "__containerType";
    public static final String NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME = "__Containers";
    public static final String NUMBER_OF_SYSTEM_CONTAINERS_PROP_NAME = "__systemContainers";

    public static enum ComputePowerState {
        ON, OFF, UNKNOWN
    }

    public String resourcePoolLink;
    public String address;
    public String primaryMAC;
    public ComputeType type;
    public String powerState = ComputePowerState.UNKNOWN.name();
    public URI adapterManagementReference;
    public List<String> diskLinks;
    public List<String> networkLinks;
    public Long creationTimeMicros;
    public String endpointLink;

    @Override
    public String getName() {
        if (this.name != null) {
            return this.name;
        }
        return getCustomProperty(ProviderConstants.FIELD_NAME_COMPUTE_NAME_KEY);
    }

    public void setName(String name) {
        this.name = name;
        setCustomProperty(ProviderConstants.FIELD_NAME_COMPUTE_NAME_KEY, name);
    }

    public String getContainerTypeId() {
        return getCustomProperty(ContainerSchemaConstants.CONTAINER_TYPE_ID.value());
    }

    public void setContainerTypeId(String containerTypeId) {
        setCustomProperty(ContainerSchemaConstants.CONTAINER_TYPE_ID.value(), containerTypeId);
    }
}
