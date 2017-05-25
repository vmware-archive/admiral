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

package com.vmware.admiral.auth.idm.local;

import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalState;
import com.vmware.admiral.auth.idm.local.LocalPrincipalService.LocalPrincipalType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;

public class LocalPrincipalFactoryService extends FactoryService {
    public static final String SELF_LINK = ManagementUriParts.LOCAL_PRINCIPALS;

    public LocalPrincipalFactoryService() {
        super(LocalPrincipalState.class);
        this.setUseBodyForSelfLink(true);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new LocalPrincipalService();
    }

    @Override
    protected String buildDefaultChildSelfLink(ServiceDocument document) {
        LocalPrincipalState state = (LocalPrincipalState) document;
        if (LocalPrincipalType.USER == state.type) {
            return state.email;
        }
        return super.buildDefaultChildSelfLink(document);
    }
}
