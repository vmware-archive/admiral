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

package com.vmware.admiral.host.interceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.photon.controller.model.resources.EndpointService;
import com.vmware.photon.controller.model.resources.EndpointService.EndpointState;
import com.vmware.photon.controller.model.tasks.QueryUtils;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * Endpoint-related service interceptors.
 */
public class EndpointInterceptor {
    public static void register(OperationInterceptorRegistry registry) {
        registry.addServiceInterceptor(
                EndpointService.class, Action.DELETE, EndpointInterceptor::interceptDelete);
    }

    /**
     * Cascading delete of associated profiles.
     */
    public static DeferredResult<Void> interceptDelete(Service service, Operation operation) {
        EndpointState endpoint = ((EndpointService)service).getState(operation);
        service.getHost().log(Level.FINE, "Endpoint %s being deleted, deleting associated profiles",
                endpoint.documentSelfLink);

        Query profilesQuery = Query.Builder.create()
                .addKindFieldClause(ProfileState.class)
                .addFieldClause(ProfileState.FIELD_NAME_ENDPOINT_LINK, endpoint.documentSelfLink)
                .build();
        QueryUtils.QueryByPages<ProfileState> queryHelper = new QueryUtils.QueryByPages<>(
                service.getHost(), profilesQuery, ProfileState.class, endpoint.tenantLinks);
        List<String> profileLinks = new ArrayList<>();
        return queryHelper.queryLinks(link -> profileLinks.add(link))
                .thenCompose(ignore -> {
                    return DeferredResult.allOf(profileLinks.stream()
                            .map(link -> Operation.createDelete(service.getHost(), link)
                                    .setReferer(service.getUri()))
                            .map(op -> service.getHost().sendWithDeferredResult(op))
                            .collect(Collectors.toList()));
                }).thenApply((ops) -> (Void)null);
    }
}
