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

package com.vmware.admiral.service.common;

import java.util.List;

import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption;




public abstract class MultiTenantDocument extends ServiceDocument {

    public static final String FIELD_NAME_TENANT_LINKS = "tenantLinks";
    public static final String TENANTS_PREFIX = "/tenants";
    public static final String USERS_PREFIX = "/users";

    /**
     * A list of tenant links which can access this service.
     */
    @PropertyOptions(indexing = { PropertyIndexingOption.EXPAND })
    public List<String> tenantLinks;

}
