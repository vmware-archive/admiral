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
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;
import com.vmware.admiral.test.integration.client.enumeration.PowerState;

/**
 * ContainerState is the logical representation of the container instance that gets created in a
 * host (machine).
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:ContainerService:ContainerState")
public class ContainerState extends ProvisionableServiceDocument implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String FIELD_NAME_CONTEXT_ID_KEY = "__composition_context_id";

    /** The list of names of a given container host. */
    public List<String> names;

    /** Link to CompositeComponent when a container is part of App/Composition request */
    public String compositeComponentLink;

    /** Defines the address of the container */
    public String address;

    /** Defines which adapter which serve the provision request */
    public String adapterManagementReference;

    /** The container host that this container instance is associated with. */
    public String hostReference;

    /** Container state indicating runtime state of a container instance. */
    public PowerState powerState;

    /**
     * Port bindings in the format ip:hostPort:containerPort | ip::containerPort |
     * hostPort:containerPort | containerPort where range of ports can also be provided
     */
    public List<PortBinding> ports;

    /** Joined networks and the configuration with which they are joined. */
    public Map<String, ServiceNetwork> networks;

    /** (Required) The docker image */
    public String image;

    /** Commands to run. */
    public String[] command;

    /**
     * Link to the resource placement associated with a given container instance. Null if no
     * placement
     */
    public String groupResourcePlacementLink;

    /** Status of the container */
    public String status;

    /** Container created time in milliseconds */
    public Long created;

    /** Container started time in milliseconds */
    public Long started;

    /** Unmodeled container attributes */
    public Map<String, String> attributes;

    /** A list of environment variables in the form of VAR=value. */
    public String[] env;

    @Override
    public String getName() {
        if (names != null && !names.isEmpty()) {
            return names.get(0);
        }
        return null;
    }

}
