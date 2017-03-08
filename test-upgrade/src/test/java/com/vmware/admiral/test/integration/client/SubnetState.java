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

import java.util.Set;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * Represents the state of a subnet.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@DcpDocumentKind("com:vmware:photon:controller:model:resources:SubnetService:SubnetState")
public class SubnetState extends ResourceServiceDocument {
    public static final String FIELD_NAME_NETWORK_LINK = "networkLink";

    public String networkLink;
    public String zoneId;
    public String subnetCIDR;
    public String gatewayAddress;
    public Set<String> dnsServerAddresses;
    public String domain;
    public Set<String> dnsSearchDomains;
    public Boolean supportPublicIpAddress;
    public Boolean defaultForZone;
    public String endpointLink;
}
