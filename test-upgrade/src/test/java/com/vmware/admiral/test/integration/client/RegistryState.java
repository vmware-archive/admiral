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

import java.io.Serializable;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;
import com.vmware.admiral.test.integration.client.enumeration.AuthProtocolType;

/**
 * RegistryState contains the information about the docker host.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:service:common:RegistryService:RegistryState")
public class RegistryState extends TenantedServiceDocument implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Connection protocol type - HTTP, SSH... */
    public AuthProtocolType protocolType;

    /** The type of the endpoint configuration */
    public String endpointType;

    /** Name/type of a component/service (Required) */
    public String name;

    /** URI or other connection address. (Required) */
    public String address;

    /** Link to associated authentication credentials. */
    public String authCredentialsLink;

    /** (Optional) Version of the API supported by service registration */
    public String version;

    /** (Optional) Disabling the registry and removing it from the active registries. */
    public Boolean disabled;

    /** (Optional) Custom properties */
    public Map<String, String> customProperties;
}
