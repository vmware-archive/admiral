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

import static com.vmware.admiral.common.util.QueryUtil.createAnyPropertyClause;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import com.vmware.admiral.service.common.RegistryService.RegistryState;

import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;

public class RegistryUtil {

    public static void findRegistriesByHostname(ServiceHost host, String hostname, String group,
            BiConsumer<List<String>, Throwable> consumer) {

        QueryTask registryQuery = QueryUtil.buildQuery(RegistryState.class, false);

        if (group != null) {
            registryQuery.querySpec.query.addBooleanClause(
                    QueryUtil.addTenantGroupAndUserClause(group));
        }

        registryQuery.querySpec.query.addBooleanClause(createAnyPropertyClause(
                String.format("*://%s*", hostname), RegistryState.FIELD_NAME_ADDRESS));

        Query excludeDisabledClause = new Query()
                .setTermPropertyName(RegistryState.FIELD_NAME_DISABLED)
                .setTermMatchValue(Boolean.TRUE.toString());
        excludeDisabledClause.occurance = Occurance.MUST_NOT_OCCUR;
        registryQuery.querySpec.query.addBooleanClause(excludeDisabledClause);

        List<String> registryLinks = new ArrayList<>();
        new ServiceDocumentQuery<RegistryState>(host, RegistryState.class).query(
                registryQuery, (r) -> {
                    if (r.hasException()) {
                        Exception err = new Exception("Exception while querying for registry state",
                                r.getException());
                        consumer.accept(null, err);
                        return;
                    } else if (r.hasResult()) {
                        registryLinks.add(r.getDocumentSelfLink());
                    } else {
                        consumer.accept(registryLinks, null);
                    }
                });
    }
}
