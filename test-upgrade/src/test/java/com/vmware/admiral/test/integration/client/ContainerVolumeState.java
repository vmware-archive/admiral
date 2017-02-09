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
 * ContainerVolume
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:volume:ContainerVolumeService:ContainerVolumeState")
public class ContainerVolumeState extends ProvisionableServiceDocument {

    /** Name of the resource instance. */
    public String name;

    /** Reference to the host that this volume was created on. */
    public URI originatingHostReference;

    /**
     * Link to CompositeComponent when a volume is part of Application/Composition request.
     */
    public String compositeComponentLink;

    /** Defines which adapter will serve the provision request. */
    public URI adapterManagementReference;

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

    @Override
    public String getName() {
        return name;
    }

}
