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

package com.vmware.admiral.service.common;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;

/**
 * Abstract factory service implementing {@link FactoryService}, which restricts the
 * access to the factory services.
 */
public abstract class AbstractSecuredFactoryService extends FactoryService {

    public AbstractSecuredFactoryService(Class<? extends ServiceDocument> childServiceDocumentType) {
        super(childServiceDocumentType);
    }

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.DELETE &&
                op.getUri().getQuery() == null &&
                !op.getAuthorizationContext().isSystemUser()) {
            op.fail(Operation.STATUS_CODE_FORBIDDEN);
            return;
        }

        super.handleRequest(op);
    }
}
