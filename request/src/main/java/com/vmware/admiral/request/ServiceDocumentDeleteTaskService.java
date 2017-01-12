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

package com.vmware.admiral.request;

import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyIndexingOption.STORE_ONLY;
import static com.vmware.xenon.common.ServiceDocumentDescription.PropertyUsageOption.REQUIRED;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.service.common.AbstractTaskStatefulService;
import com.vmware.admiral.service.common.DefaultSubStage;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.QuerySpecification.QueryOption;

/**
 * Deletes all documents of a kind
 */
public class ServiceDocumentDeleteTaskService
        extends
        AbstractTaskStatefulService<ServiceDocumentDeleteTaskService.ServiceDocumentDeleteTaskState, DefaultSubStage> {

    public static final String FACTORY_LINK = ManagementUriParts.DELETE_SERVICE_DOCUMENTS;
    private static final String DISPLAY_NAME = "Delete all service documents of a kind";

    public static class ServiceDocumentDeleteTaskState extends
            com.vmware.admiral.service.common.TaskServiceDocument<DefaultSubStage> {

        @PropertyOptions(usage = { REQUIRED }, indexing = STORE_ONLY)
        public String deleteDocumentKind;

    }

    public ServiceDocumentDeleteTaskService() {
        super(ServiceDocumentDeleteTaskState.class, DefaultSubStage.class, DISPLAY_NAME);
    }

    @Override
    public void handleStartedStagePatch(ServiceDocumentDeleteTaskState state) {
        switch (state.taskSubStage) {
        case CREATED:
            handleQueryServices(state);
            break;
        default:
            failTask(String.format("Unexpected sub stage: %s", state.taskSubStage), null);
            break;
        }
    }

    private void handleQueryServices(ServiceDocumentDeleteTaskState task) {
        QueryTask query = QueryUtil.buildQuery(task.deleteDocumentKind, false);
        query.querySpec.options = EnumSet.of(QueryOption.TOP_RESULTS);
        query.querySpec.resultLimit = 50;
        query.documentExpirationTimeMicros = Utils.getNowMicrosUtc() + TimeUnit.HOURS.toMicros(5);
        if (task.tenantLinks != null) {
            query.querySpec.query.addBooleanClause(QueryUtil.addTenantClause(task.tenantLinks));
        }

        List<String> documents = new ArrayList<String>();
        new ServiceDocumentQuery<ServiceDocument>(getHost(), ServiceDocument.class)
                    .query(query, (r) -> {
                        if (r.hasException()) {
                            logWarning("Query failed, task will not finish: %s",
                                    r.getException().getMessage());
                            failTask(String.format("Could not get %s, task failed", task.deleteDocumentKind), r.getException());
                            return;
                        } else if (r.hasResult() && documents.size() < r.getCount()) {
                            documents.add(r.getDocumentSelfLink());
                        } else {
                            handleDeleteServices(task, documents);
                        }
                    });
    }

    private void handleDeleteServices(ServiceDocumentDeleteTaskState task, List<String> documentLinks) {

        if (documentLinks.size() == 0) {
            complete();
            return;
        }

        List<Operation> deleteOperations = new ArrayList<>();
        for (String service : documentLinks) {
            URI serviceUri = UriUtils.buildUri(this.getHost(), service);
            Operation deleteOp = Operation.createDelete(serviceUri);
            deleteOperations.add(deleteOp);
        }

        OperationJoin operationJoin = OperationJoin.create();
        operationJoin
                .setOperations(deleteOperations)
                .setCompletion(
                        (ops, exs) -> {
                            if (exs != null && !exs.isEmpty()) {
                                failTask(String.format("%d deletes failed", exs.size()), exs
                                        .values().iterator().next());
                                return;
                            } else {
                                handleQueryServices(task);
                            }
                        }).sendWith(this);
    }

}
