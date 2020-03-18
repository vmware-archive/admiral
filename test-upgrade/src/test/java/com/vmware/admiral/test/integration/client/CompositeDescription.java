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

import java.util.List;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * CompositeDescription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:CompositeDescriptionService:CompositeDescription")
public class CompositeDescription extends TenantedServiceDocument {
    /** Name of composite description */
    public String name;
    /** Status of the composite description (PUBLISHED) */
    public String status;
    /** Last published time in milliseconds */
    public Long lastPublished;
    /** Link to the parent composite description */
    public String parentDescriptionLink;
    /** List of all ContainerDescriptions as part of this composition description */
    public List<String> descriptionLinks;
    /** Custom properties. */
    public Map<String, String> customProperties;
    /** Bindings */
    public List<Binding.ComponentBinding> bindings;
}
