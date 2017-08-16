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

package com.vmware.iaas.consumer.api.common;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.iaas.consumer.api.model.base.Resource;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.services.common.QueryTask;

/**
 * Wrapper for all kind of services Operations
 */
public class ServiceOperationUtil {

    private ServiceOperationUtil() {
    }

    /**
     * Generic get method exposed through Consumer API for all ServiceDocuments
     *
     * @param get           - Original operation to service/controller
     * @param host          - Service Host
     * @param pathToFactory - Path to Factory that will be used for construction of self link
     * @param kind          - kind of a document
     * @param resourceToMap - Consumer API resource to which document will be translated. For
     *                      example if kind
     *                      is equals to ComputeState, than 'resoureToMap' will be Machine.
     */
    public static void handleGet(Operation get, ServiceHost host, String pathToFactory,
            Class<? extends ServiceDocument> kind, Class<? extends Resource> resourceToMap) {

        QueryTask.Query.Builder queryBuilder = QueryTask.Query.Builder.create()
                .addKindFieldClause(kind);

        //TODO retrieve documentSelfLink from URI in order to search for particular document
        // if(documentSelfLink != null){
        //     queryBuilder.addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, documentSelfLink);
        // }

        QueryTask q = QueryTask.Builder.create().setQuery(queryBuilder.build()).build();
        q.querySpec.resultLimit = ServiceDocumentQuery.DEFAULT_QUERY_RESULT_LIMIT;

        QueryUtil.addExpandOption(q);

        //TODO list should be type of Resource
        List<ServiceDocument> result = new ArrayList<>();

        new ServiceDocumentQuery<>(host, kind).query(q, (r) -> {
            if (r.hasException()) {
                host.log(Level.WARNING,
                        "Exception while quering documents Error: [%s]",
                        r.getException().getMessage());
                get.fail(r.getException());
                return;
            } else if (r.hasResult()) {
                // TODO translate result from Admiral/Photon model to Consumer API POJOs - resourceToMap
                result.add(r.getResult());
            } else {
                get.setBody(result).complete();
            }
        });
    }

}



