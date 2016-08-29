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

package com.vmware.admiral.image.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ImageOperationType;
import com.vmware.admiral.adapter.registry.service.RegistryAdapterService;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse;
import com.vmware.admiral.adapter.registry.service.RegistrySearchResponse.Result;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.UriUtilsExtended;
import com.vmware.admiral.host.HostInitRegistryAdapterServiceConfig;
import com.vmware.admiral.service.common.RegistryService;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

/**
 * Service for providing image search
 */
public class ContainerImageService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.IMAGES;

    public static final String TENANT_LINKS_PARAM_NAME = "tenantLinks";

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() != Action.GET) {
            getHost().failRequestActionNotSupported(op);
            return;
        }

        handleGet(op);
    }

    @Override
    public void handleGet(Operation op) {
        try {
            handleSearchRequest(op);

        } catch (Exception x) {
            logSevere(x);
            op.fail(x);
        }
    }

    /**
     * Perform an image search using the registry adapter
     *
     * The group query parameter is used to limit the registries to search to the ones visible the
     * given group (including global groups) while the rest of the request parameters (search query,
     * sort, etc.) are passed as custom properties directly to the adapter
     *
     * @param op
     */
    private void handleSearchRequest(Operation op) {
        URI registryAdapterUri = HostInitRegistryAdapterServiceConfig.registryAdapterReference;
        AssertUtil.assertNotNull(registryAdapterUri, "registryAdapterReference");

        // get the group parameter to build the registry query
        // pass the rest of the query parameters as custom properties directly to the adapter
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(op.getUri());
        String group = queryParams.remove(TENANT_LINKS_PARAM_NAME);

        logFine("Search in group: " + group);

        // query for registries and execute an adapter request for each one
        Consumer<Collection<String>> registryLinksConsumer = (registryLinks) -> handleSearchRequest(
                op, registryAdapterUri, queryParams, registryLinks);

        Consumer<Collection<Throwable>> failureConsumer = (failures) -> op.fail(failures.iterator()
                .next());

        RegistryService.forEachRegistry(getHost(), group, registryLinksConsumer, failureConsumer);
    }

    private void handleSearchRequest(Operation op, URI registryAdapterUri,
            Map<String, String> queryParams, Collection<String> searchRegistryLinks) {

        if (searchRegistryLinks.isEmpty()) {
            op.fail(new IllegalStateException("No registries found"));
            return;
        }

        Integer parsedLimit = null;
        try {
            parsedLimit = Integer.parseInt(queryParams.get(RegistryAdapterService.LIMIT_PROP_NAME));
        } catch (Exception e) {
        }

        final int limit = parsedLimit != null ? parsedLimit : 0;

        List<Operation> searchOperations = new ArrayList<Operation>(searchRegistryLinks.size());
        for (String registryLink : searchRegistryLinks) {
            Operation searchOp = createSearchOperation(registryAdapterUri, queryParams,
                    registryLink);

            searchOperations.add(searchOp);
        }

        OperationJoin join = OperationJoin.create(searchOperations
                .toArray(new Operation[searchOperations.size()]));

        join.setCompletion((searchOps, failures) -> {
            // failures are ignored, so search results will be returned even if only some of the
            // requests were successful
            RegistrySearchResponse mergedResponse = new RegistrySearchResponse();
            for (Operation searchOp : searchOps.values()) {
                if (searchOp.hasBody()) {
                    RegistrySearchResponse registryResponse = searchOp
                            .getBody(RegistrySearchResponse.class);

                    for (Result result : registryResponse.results) {
                        result.name = UriUtilsExtended.extractHostAndPort(result.registry) + "/"
                                + result.name;
                    }
                    mergedResponse.merge(registryResponse);
                }
            }

            if (limit > 0) {
                mergedResponse.limit(limit);
            }

            // pagination doesn't make sense when querying over multiple registries
            mergedResponse.page = -1;
            mergedResponse.pageSize = -1;
            mergedResponse.numPages = -1;
            op.setBody(mergedResponse);

            logFine("Search result: %s", Utils.toJsonHtml(mergedResponse));

            op.complete();
        });

        join.sendWith(this);
    }

    private Operation createSearchOperation(URI registryAdapterUri,
            Map<String, String> queryParams, String searchRegistryLink) {

        AdapterRequest imageRequest = new AdapterRequest();
        imageRequest.operationTypeId = ImageOperationType.SEARCH.id;
        imageRequest.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        imageRequest.resourceReference = UriUtils.buildPublicUri(getHost(), searchRegistryLink);
        imageRequest.customProperties = new HashMap<>(queryParams);

        Operation adapterOp = Operation
                .createPatch(registryAdapterUri)
                .setBody(imageRequest);

        return adapterOp;
    }
}
