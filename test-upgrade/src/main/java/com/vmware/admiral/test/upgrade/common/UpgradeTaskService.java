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

package com.vmware.admiral.test.upgrade.common;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class UpgradeTaskService extends StatelessService {

    public static final String SELF_LINK = UpgradeUtil.UPGRADE_TASK_SERVICE_FACTORY_LINK;

    public static class UpgradeServiceRequest {
        public String version;
        public String clazz;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handlePost(Operation post) {

        UpgradeServiceRequest request = post.getBody(UpgradeServiceRequest.class);

        Class<? extends ServiceDocument> clazz;
        try {
            clazz = (Class<? extends ServiceDocument>) Class.forName(request.clazz);
        } catch (ClassNotFoundException e) {
            post.fail(e);
            return;
        }

        Query query = Query.Builder.create().addKindFieldClause(clazz).build();
        QueryTask queryTask = QueryTask.Builder.createDirectTask().setQuery(query).build();

        // TODO - the query must go through all the states (multiple pages)

        Operation op = Operation.createPost(this, ServiceUriPaths.CORE_QUERY_TASKS)
                .setBody(queryTask)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        post.fail(e);
                    } else {
                        QueryTask resultTask = o.getBody(QueryTask.class);
                        ServiceDocumentQueryResult result = resultTask.results;

                        if (result == null || result.documentLinks == null) {
                            logWarning("---------- NO documents found!");
                            post.complete();
                            return;
                        }

                        logWarning("---------- Documents found: %s", result.documentLinks.size());

                        CountDownLatch countDown = new CountDownLatch(result.documentLinks.size());

                        result.documentLinks.forEach((link) -> {
                            getHost().registerForServiceAvailability((o2, e2) -> {
                                if (e2 != null) {
                                    logWarning("---------- Document '%s' missing: %s", link,
                                            Utils.toString(e2));
                                    return;
                                }

                                UpgradeUtil.upgradeState(this, link, clazz, request.version,
                                        (v) -> {
                                            countDown.countDown();
                                        });
                            }, link);
                        });

                        boolean waited;
                        try {
                            waited = countDown.await(10, TimeUnit.SECONDS);
                            if (!waited) {
                                logWarning("Waiting for documentLinks timedout: %s",
                                        result.documentLinks);
                            }
                        } catch (InterruptedException ex) {
                            logWarning("Thread interrupted: %s", Utils.toString(ex));
                        }

                        logWarning("---------- Upgrade ends!");

                        post.complete();
                    }
                });

        UpgradeUtil.setOperationRequestApiVersion(op, request.version);
        logWarning("---------- Upgrade starts!");
        sendRequest(op);
    }
}
