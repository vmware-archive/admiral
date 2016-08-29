/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.common.util;

import java.util.EnumSet;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription;

public class ServiceDocumentTemplateUtil {
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";

    public static void indexCustomProperties(ServiceDocument template) {
        // enable indexing of custom properties map
        ServiceDocumentDescription.PropertyDescription pdCustomProperties = template
                .documentDescription.propertyDescriptions
                        .get(FIELD_NAME_CUSTOM_PROPERTIES);
        pdCustomProperties.indexingOptions = EnumSet
                .of(ServiceDocumentDescription.PropertyIndexingOption.EXPAND);
    }

    public static void indexProperty(ServiceDocument template, String propertyName) {
        ServiceDocumentDescription.PropertyDescription pdSupportedChildren = template
                .documentDescription.propertyDescriptions.get(propertyName);
        pdSupportedChildren.indexingOptions = EnumSet
                .of(ServiceDocumentDescription.PropertyIndexingOption.EXPAND);
    }

    public static void indexTextProperty(ServiceDocument template, String propertyName) {
        ServiceDocumentDescription.PropertyDescription pd = template
                .documentDescription.propertyDescriptions.get(propertyName);

        pd.indexingOptions = EnumSet.of(ServiceDocumentDescription.PropertyIndexingOption.EXPAND,
                ServiceDocumentDescription.PropertyIndexingOption.TEXT);
    }
}
