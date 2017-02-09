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

import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlTransient;

/**
 * Base class for all {@link ServiceDocument}s that can be provisioned. All of them are sharing some
 * common properties.
 */
@XmlTransient
public abstract class ProvisionableServiceDocument extends ResourceServiceDocument {

    public static final String FIELD_NAME_DESCRIPTION_LINK = "descriptionLink";

    /** External instance identifier of this resource. */
    public String id;

    /** Defines the description of the resource */
    public String descriptionLink;

    public String parentLink;

    /**
     * A map of field-value pairs for a given resource. These key/value pairs are custom tags,
     * properties or attributes that could be used to add additional data or tag to the resource
     * instance for query and policy purposes.
     */
    public Map<String, String> customProperties;

    public String getExternalId() {
        return id;
    }

    public void setExternalId(String id) {
        this.id = id;
    }

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

    public abstract String getName();
}
