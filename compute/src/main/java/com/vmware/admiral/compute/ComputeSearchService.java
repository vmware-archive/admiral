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

package com.vmware.admiral.compute;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.xenon.common.ODataFactoryQueryResult;
import com.vmware.xenon.common.ODataQueryVisitor.BinaryVerb;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * Compute list service
 */
public class ComputeSearchService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.COMPUTE_SEARCH;

    private void appendLinks(Map<String, String> queryParams, String name, List<String> links, String operator) {
        if (links.isEmpty()) {
            return;
        }
        String filter = queryParams.get(UriUtils.URI_PARAM_ODATA_FILTER);
        String query = links.stream().map(l -> name + " eq '" + l + "'").reduce((l1, l2) -> l1 + " or " + l2).get();
        if (filter.contains(" and ")) {
            queryParams.put(UriUtils.URI_PARAM_ODATA_FILTER,
                    filter.substring(0, filter.length() - 1) + " " + operator + " (" + query + "))");
        } else {
            queryParams.put(UriUtils.URI_PARAM_ODATA_FILTER, filter + " and (" + query + ")");
        }
    }

    @Override
    public void handleGet(Operation get) {
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(get.getUri());
        List<Operation> operationsToJoin = new ArrayList<>();

        String endpoint = queryParams.remove("endpoint");
        if (endpoint != null && !endpoint.isEmpty()) {
            Operation endpointOp = Operation
                    .createGet(UriUtils.buildUri(getHost(), EndpointService.FACTORY_LINK, "$filter=" + endpoint))
                    .setReferer(getUri());
            operationsToJoin.add(endpointOp);
        }

        String tag = queryParams.remove("tag");
        if (tag != null && !tag.isEmpty()) {
            Operation tagOp = Operation
                    .createGet(UriUtils.buildUri(getHost(), TagService.FACTORY_LINK, "$filter=" + tag))
                    .setReferer(getUri());
            operationsToJoin.add(tagOp);
        }

        if (operationsToJoin.size() != 0) {

            OperationJoin.create(operationsToJoin).setCompletion((ops, exs) -> {
                if (exs != null) {
                    get.fail(exs.values().iterator().next());
                    return;
                }

                String defaultOperator = BinaryVerb.AND.toString().toLowerCase();
                String operator = queryParams.containsKey("operator") ? queryParams.remove("operator")
                        : defaultOperator;
                ODataFactoryQueryResult emptyBody = new ODataFactoryQueryResult();
                emptyBody.documentCount = 0L;
                emptyBody.totalCount = 0L;

                Iterator<Operation> operationsIterator = operationsToJoin.iterator();
                if (endpoint != null && !endpoint.isEmpty()) {
                    List<String> endpointLinks = ops.get(operationsIterator.next().getId())
                            .getBody(ODataFactoryQueryResult.class).documentLinks;
                    if (endpointLinks.isEmpty() && defaultOperator.equals(operator)) {
                        get.setBody(emptyBody);
                        get.complete();
                        return;
                    }
                    appendLinks(queryParams, "endpointLink", endpointLinks, operator);
                }
                if (tag != null && !tag.isEmpty()) {
                    List<String> tagLinks = ops.get(operationsIterator.next().getId())
                            .getBody(ODataFactoryQueryResult.class).documentLinks;
                    if (tagLinks.isEmpty() && defaultOperator.equals(operator)) {
                        get.setBody(emptyBody);
                        get.complete();
                        return;
                    }
                    appendLinks(queryParams, "tagLinks/item", tagLinks, operator);
                }

                String query = queryParams.entrySet().stream().map(p -> p.getKey() + "=" + p.getValue())
                        .reduce((p1, p2) -> p1 + "&" + p2).orElse("");

                URI uri = UriUtils.buildUri(getHost(), ComputeService.FACTORY_LINK, query);
                Operation op = Operation.createGet(uri).setCompletion((o, e) -> {
                    if (e != null) {
                        get.fail(e);
                        return;
                    }
                    get.setBody(o.getBodyRaw());
                    get.complete();
                });
                sendRequest(op);

            }).sendWith(getHost());

        } else {

            URI uri = UriUtils.buildUri(getHost(), ComputeService.FACTORY_LINK, get.getUri().getQuery());
            Operation op = Operation.createGet(uri).setCompletion((o, e) -> {
                if (e != null) {
                    get.fail(e);
                    return;
                }
                get.setBody(o.getBodyRaw());
                get.complete();
            });
            sendRequest(op);
        }
    }
}
