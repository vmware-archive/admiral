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
 * ContainerVolumeDescription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:volume:ContainerVolumeDescriptionService:ContainerVolumeDescription")
public class ContainerVolumeDescription extends ResourceServiceDocument {

    public static final String RESOURCE_TYPE = "CONTAINER_VOLUME";

    /**
     * The name of the driver for this volume. List of supported plugins:
     * https://docs.docker.com/engine/extend/legacy_plugins/ #Volume plugins
     */
    public String driver;

    /**
     * A map of field-value pairs for a given volume. These are used to specify volume option that
     * are to be used by the volume drivers.
     */
    public Map<String, String> options;

    /**
     * Composite Template use only. If set to true, specifies that this volume exists outside of the
     * Composite Template.
     */
    public Boolean external;

    /**
     * Composite Template use only. The name of the external volume. If set then the value of the
     * attribute 'external' is considered 'true'.
     */
    public String externalName;

    /** Instance Adapter reference for provisioning of containers. */
    public URI instanceAdapterReference;

    /** Link to the parent volume description. */
    public String parentDescriptionLink;

    /** Custom properties. */
    public Map<String, String> customProperties;

}
