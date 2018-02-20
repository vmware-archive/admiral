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

package com.vmware.admiral.compute.container;

import com.google.gson.JsonSyntaxException;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.compute.container.ContainerDescriptionService.ContainerDescription;
import com.vmware.admiral.service.common.AbstractSecuredFactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

public class ContainerDescriptionFactoryService extends AbstractSecuredFactoryService {
    public static final String SELF_LINK = ManagementUriParts.CONTAINER_DESC;

    public ContainerDescriptionFactoryService() {
        super(ContainerDescription.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new ContainerDescriptionService();
    }

    @Override
    public void handleGet(Operation get) {
        OperationUtil.transformProjectHeaderToFilterQuery(get);
        super.handleGet(get);
    }

    @Override
    public void handleRequest(Operation op) {
        // Workaround for invalid json document for container-descriptions during upgrade
        // VBV-1845
        if (op.getAction() == Action.POST
                && op.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_FROM_MIGRATION_TASK)) {
            try {
                op.getBody(this.stateType);
            } catch (IllegalArgumentException | JsonSyntaxException e) {
                if (e.getMessage().contains("Unparseable JSON body")
                        || e.getMessage().contains("IllegalStateException")) {
                    logWarning(
                            "Incorrect json structure detected for container-description document during migration: %s. Document will be skipped",
                            op.getBodyRaw());
                    op.setBody(null).complete();
                } else {
                    throw e;
                }
            }
            super.handleRequest(op);
        } else {
            super.handleRequest(op);
        }
    }
}
