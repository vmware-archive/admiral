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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * NetworkInterfaceDescription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:photon:controller:model:resources:NetworkInterfaceDescriptionService:NetworkInterfaceDescription")
public class NetworkInterfaceDescription extends ResourceServiceDocument {
    public static final String FIELD_NAME_NAME = "name";
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";

    public String assignment;

    public int deviceIndex;

    public List<String> firewallLinks;

    public String networkLink;

    public String subnetLink;

    public String address;

    public Map<String, String> customProperties;

    public String getCustomProperty(String key) {
        if (customProperties == null) {
            return null;
        }
        Object value = customProperties.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    public void setCustomProperty(String key, String value) {
        if (customProperties == null) {
            customProperties = new HashMap<>(2);
        }
        customProperties.put(key, value);
    }
}
