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
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * ContainerNetworkDescription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:network:ContainerNetworkDescriptionService:ContainerNetworkDescription")
public class ContainerNetworkDescription extends ResourceServiceDocument {
    public static final String RESOURCE_TYPE = "NETWORK";

    /** An IPAM configuration for a given network. */
    public Ipam ipam;

    /** The name of the driver for this network. Can be bridge, host, overlay, none. */
    public String driver;

    /**
     * A map of field-value pairs for a given network. These are used to specify network option that
     * are to be used by the network drivers.
     */
    public Map<String, String> options;

    /**
     * Composite Template use only. If set to true, specifies that this network exists outside of
     * the Composite Template.
     */
    public Boolean external;

    /**
     * Composite Template use only. The name of the external network. If set then the value of the
     * attribute 'external' is considered 'true'.
     */
    public String externalName;

    /** Instance Adapter reference for provisioning of containers. */
    public URI instanceAdapterReference;

    /** Link to the parent network description. */
    public String parentDescriptionLink;

    /** Custom properties. */
    public Map<String, String> customProperties;
}
