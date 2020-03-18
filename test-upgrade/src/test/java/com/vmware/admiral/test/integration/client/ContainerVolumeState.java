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
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;
import com.vmware.admiral.test.integration.client.enumeration.ContainerVolumePowerState;

/**
 * ContainerVolume
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:volume:ContainerVolumeService:ContainerVolumeState")
public class ContainerVolumeState extends ProvisionableServiceDocument {

    /** Reference to the host that this volume was created on. */
    public String originatingHostLink;

    /** Links to CompositeComponents when a volume is part of App/Composition request. */
    public List<String> compositeComponentLinks;

    /** Defines which adapter will serve the provision request. */
    public URI adapterManagementReference;

    /** Volume host links */
    public List<String> parentLinks;

    /** Volume state indicating runtime state of a volume instance. */
    public ContainerVolumePowerState powerState;

    /**
     * The name of the driver for this volume. List of supported plugins:
     * https://docs.docker.com/engine/extend/legacy_plugins/ #Volume plugins
     */
    public String driver;

    /**
     * If set to true, specifies that this volume exists independently of any application.
     */
    public Boolean external;

    /** Volume connected time in milliseconds */
    public Long connected;

    /**
     * Scope describes the level at which the volume exists, can be one of global for cluster-wide
     * or local for machine level. The default is local.
     */
    public String scope;

    /**
     * Mount path of the volume on the host.
     */
    public String mountpoint;

    /**
     * A map of field-value pairs for a given volume. These are used to specify volume option that
     * are to be used by the volume drivers.
     */
    public Map<String, String> options;

    /**
     * Low-level details about the volume, provided by the volume driver. Details are returned as a
     * map with key/value pairs: {"key":"value","key2":"value2"}
     */
    public Map<String, String> status;

    @Override
    public String getName() {
        return name;
    }

}
