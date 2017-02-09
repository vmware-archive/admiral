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

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;
import com.vmware.admiral.test.integration.client.enumeration.ContainerNetworkPowerState;

/**
 * ContainerNetwork
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:network:ContainerNetworkService:ContainerNetworkState")
public class ContainerNetworkState extends ProvisionableServiceDocument {

    /** Name of the resource instance. */
    public String name;

    /** Reference to the host that this network was created on. */
    public String originatingHostLink;

    /** Links to CompositeComponents when a network is part of App/Composition request. */
    public List<String> compositeComponentLinks;

    /** Defines which adapter will serve the provision request. */
    public URI adapterManagementReference;

    /** Network state indicating runtime state of a network instance. */
    public ContainerNetworkPowerState powerState;

    /** Container host links */
    public List<String> parentLinks;

    /** An IPAM configuration for a given network. */
    public Ipam ipam;

    /** The name of the driver for this network. Can be bridge, host, overlay, none. */
    public String driver;

    /** If set to true, specifies that this network exists independently of any application. */
    public Boolean external;

    /** Network connected time in milliseconds */
    public Long connected;

    /**
     * A map of field-value pairs for a given network. These are used to specify network option that
     * are to be used by the network drivers.
     */
    public Integer connectedContainersCount;

    /**
     * A map of field-value pairs for a given network. These are used to specify network option that
     * are to be used by the network drivers.
     */
    public Map<String, String> options;

    @Override
    public String getName() {
        return name;
    }
}
