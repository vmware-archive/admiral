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

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.profile.ProfileService;
import com.vmware.admiral.compute.profile.ProfileService.ProfileState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;

/**
 * Profile-related service interceptors.
 */
public class ProfileInterceptor {
    public static void register(OperationInterceptorRegistry registry) {
        registry.addServiceInterceptor(
                ProfileService.class, Action.DELETE, ProfileInterceptor::interceptDelete);
    }

    /**
     * Cascading deletes of sub-profiles.
     */
    public static DeferredResult<Void> interceptDelete(Service service, Operation operation) {
        ProfileState profile = ((ProfileService)service).getState(operation);
        service.getHost().log(Level.FINE, "Profile %s being deleted, deleting sub-profiles",
                profile.documentSelfLink);

        List<String> profileLinks = Arrays.asList(profile.computeProfileLink,
                profile.networkProfileLink, profile.storageProfileLink);

        List<DeferredResult<Operation>> drs = profileLinks.stream()
                .map(link -> link != null
                        ? service.getHost().sendWithDeferredResult(
                                Operation.createDelete(service.getHost(), link)
                                        .setReferer(service.getUri()))
                        : DeferredResult.completed((Operation)null))
                .collect(Collectors.toList());
        return DeferredResult.allOf(drs).thenApply((ops) -> (Void)null);
    }
}
