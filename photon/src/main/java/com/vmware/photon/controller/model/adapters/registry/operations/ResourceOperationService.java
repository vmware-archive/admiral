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

package com.vmware.photon.controller.model.adapters.registry.operations;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.vmware.photon.controller.model.UriPaths;
import com.vmware.photon.controller.model.adapters.registry.operations.ResourceOperationSpecService.ResourceOperationSpec;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.NetworkService.NetworkState;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;

/**
 * This service returns a list of available {@link ResourceOperationSpec}s for given {@link
 * ResourceState}
 * <p>
 * Supported resource states are {@link ComputeState} and {@link NetworkState}
 * <p>
 * The service serves GET requests on address /resources/resource-operations?resource=&lt;resource.documentSelfLink&gt;
 * and returns a list of {@link ResourceOperationSpec} available for the current state of the {@link
 * ResourceState} identified by the provided {@code resource.documentSelfLink}
 * <p>
 * Example:
 * <pre>
 *  String query = UriUtils.buildUriQuery(
 *          ResourceOperationService.QUERY_PARAM_RESOURCE,
 *          createdComputeState.documentSelfLink);
 *
 * URI uri = UriUtils.buildUri(super.host, ResourceOperationService.SELF_LINK, query);
 * Operation op = Operation.createGet(uri);
 *
 *  // send get request
 *  // on response get result:
 *
 *  String json = Utils.toJson(operation.getBodyRaw());
 *  List&lt;ResourceOperationSpec&gt; list = Utils.fromJson(
 *          json,
 *          new TypeToken&lt;List&lt;ResourceOperationSpec&gt;&gt;() {}.getType());
 *
 * </pre>
 */
public class ResourceOperationService extends StatelessService {

    public static final String SELF_LINK = UriPaths.RESOURCES + "/resource-operations";

    public static final String QUERY_PARAM_RESOURCE = "resource";

    public static final String QUERY_PARAM_OPERATION = "operation";

    @Override
    public void handleGet(Operation get) {
        Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
        String resourceLink = params.get(QUERY_PARAM_RESOURCE);
        if (resourceLink == null || resourceLink.isEmpty()) {
            get.fail(new IllegalArgumentException("No resource link provided."));
            return;
        }
        String operation = params.get(QUERY_PARAM_OPERATION);

        sendWithDeferredResult(Operation.createGet(this, resourceLink))
                .thenCompose(op -> {
                    ResourceState tmpResourceState = op.getBody(ComputeState.class);
                    if (ResourceOperationUtils.NETWORK_KIND.equals(tmpResourceState.documentKind)) {
                        tmpResourceState = op.getBody(NetworkState.class);
                    }
                    final ResourceState resourceState = tmpResourceState;
                    return ResourceOperationUtils
                            .lookupByResourceState(getHost(), getUri(), resourceState, operation)
                            .thenApply(collectAvailable(resourceState));
                })
                .whenComplete((specs, e) -> {
                    if (e != null) {
                        get.fail(e);
                    } else {
                        get.setBody(specs).complete();
                    }
                });
    }

    private Function<List<ResourceOperationSpec>, List<ResourceOperationSpec>> collectAvailable(
            ResourceState resourceState) {
        return specs -> specs.stream()
                .filter(spec -> ResourceOperationUtils.isAvailable(resourceState, spec))
                .collect(Collectors.toList());
    }
}