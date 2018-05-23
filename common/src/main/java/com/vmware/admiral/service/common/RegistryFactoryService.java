/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.common;

import java.util.ArrayList;
import java.util.List;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.OperationUtil;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.service.common.RegistryService.RegistryState;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

public class RegistryFactoryService extends AbstractSecuredFactoryService {

    public static final String SELF_LINK = ManagementUriParts.REGISTRIES;
    public static final String REGISTRY_ALREADY_EXISTS = "Registry with this address already exists.";

    public RegistryFactoryService() {
        super(RegistryState.class);
    }

    @Override
    public Service createServiceInstance() throws Throwable {
        return new RegistryService();
    }

    @Override
    public void handleGet(Operation get) {
        OperationUtil.transformProjectHeaderToFilterQuery(get, true);
        super.handleGet(get);
    }


    @Override
    public void handlePost(Operation post) {
        QueryTask queryTask = QueryUtil.buildQuery(RegistryState.class, true);
        RegistryState registry = post.getBody(RegistryState.class);
        AssertUtil.assertNotNullOrEmpty(registry.address, "registry.address");
        Query parentsClause = new Query()
                .setTermPropertyName(RegistryState.FIELD_NAME_ADDRESS)
                .setTermMatchValue(registry.address)
                .setTermMatchType(MatchType.TERM)
                .setOccurance(Occurance.MUST_OCCUR);

        queryTask.querySpec.query.addBooleanClause(parentsClause);
        queryTask.querySpec.query.addBooleanClause(QueryUtil
                .addTenantGroupAndUserClause(registry.tenantLinks));
        QueryUtil.addExpandOption(queryTask);

        List<RegistryState> registries = new ArrayList<RegistryState>();
        new ServiceDocumentQuery<RegistryState>(getHost(),
                RegistryState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                        logSevere("Failed to get registry state with address %s",
                                registry.address);
                    } else if (r.hasResult()) {
                        registries.add(r.getResult());
                    } else {
                        if (registries.isEmpty()) {
                            super.handlePost(post);
                        } else {
                            if (!post.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_FROM_MIGRATION_TASK)) {
                                post.fail(new LocalizableValidationException(
                                        REGISTRY_ALREADY_EXISTS,
                                        "compute.registry.host.address.already.exists"));
                            } else {
                                super.handlePost(post);
                            }
                        }
                    }
                });
    }
}
