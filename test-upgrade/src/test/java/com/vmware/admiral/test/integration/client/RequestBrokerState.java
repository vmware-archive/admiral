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

import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.request.ReservationTaskService;
import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * RequestBrokerState
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:request:RequestBrokerService:RequestBrokerState")
public class RequestBrokerState extends TaskServiceDocument {

    /** (Required) Type of resource to create. */
    public String resourceType;

    /** (Required) The operation name/id to be performed */
    public String operation;

    /** (Required) The description that defines the requested resource. */
    public String resourceDescriptionLink;

    /** (Optional- default 1) Number of resources to provision. */
    public long resourceCount;

    /** Set by Task when resources are provisioned. */
    public List<String> resourceLinks;

    /** Set by {@link ReservationTaskService} with the select {@link GroupResourcePlacementState} */
    public String groupResourcePlacementLink;
}
