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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * Describes grouping of multiple container instances deployed at the same time. It represents a
 * template definition of related services or an application.
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:CompositeComponentService:CompositeComponent")
public class CompositeComponent extends TenantedServiceDocument implements Serializable {
    public static final String RESOURCE_TYPE = "COMPOSITE_COMPONENT";
    private static final long serialVersionUID = 1L;

    /** Name of composite description */
    public String name;

    /** (Optional) CompositeDescription link */
    public String compositeDescriptionLink;

    public List<String> componentLinks;

    /** Composite component creation time in milliseconds */
    public long created;

}
