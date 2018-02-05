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

package com.vmware.admiral.upgrade.transformation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.CompositeComponentService.CompositeComponent;
import com.vmware.photon.controller.model.resources.ResourceState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.OperationJoin;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * The service is executed during the upgrade from VIC 1.1 to 1.2 and vRA 7.3 to 7.4. The service
 * adds the project(s) to the application from the components
 */
public class CompositeComponentsTransformationService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.COMPOSITE_COMPONENTS_UPGRADE_TRANSFORM_PATH;

    AtomicInteger compositeComponentsCount;

    @Override
    public void handlePost(Operation post) {
        QueryTask queryTask = QueryUtil.buildQuery(CompositeComponent.class, true);
        QueryUtil.addExpandOption(queryTask);
        List<CompositeComponent> compositeComponents = new ArrayList<CompositeComponent>();
        new ServiceDocumentQuery<CompositeComponent>(getHost(), CompositeComponent.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                        logSevere("Failed to get composite components");
                    } else if (r.hasResult()) {
                        compositeComponents.add(r.getResult());
                    } else {
                        if (compositeComponents.isEmpty()) {
                            post.complete();
                        }
                        logInfo("Number of applications: %d", compositeComponents.size());
                        processApplications(compositeComponents, post);
                    }
                });
    }

    private void processApplications(List<CompositeComponent> compositeComponents, Operation post) {
        if (compositeComponents.isEmpty()) {
            logInfo("No applications found. Composite components transformation completed successfully");
            post.complete();
            return;
        }
        compositeComponentsCount = new AtomicInteger(compositeComponents.size());
        for (CompositeComponent state : compositeComponents) {
            processApplication(state, post);
        }
    }

    private void processApplication(CompositeComponent state, Operation post) {
        Set<String> tenantLinks = ConcurrentHashMap.newKeySet();
        List<Operation> getOperations = state.componentLinks.stream()
                .map(link -> Operation.createGet(getHost(), link)
                        .setReferer(getUri()))
                .collect(Collectors.toList());

        OperationJoin.create(getOperations).setCompletion((ops, ex) -> {
            if (ex != null) {
                post.fail(new Throwable(
                        "Error retrieving composite components: " + Utils.toString(ex),
                        ex.values().iterator().next()));
            } else {
                for (Operation op : ops.values()) {
                    ResourceState document = op.getBody(ResourceState.class);
                    tenantLinks.addAll(new LinkedHashSet<String>(document.tenantLinks));
                }
                updateApplicationTenantLinks(state, tenantLinks, post);
            }
        }).sendWith(getHost());
    }

    private void updateApplicationTenantLinks(CompositeComponent state, Set<String> tenantLinks,
            Operation post) {

        if (state.tenantLinks == null) {
            state.tenantLinks = new ArrayList<>();
        }
        state.tenantLinks.addAll(tenantLinks);
        state.tenantLinks = new ArrayList<>(new LinkedHashSet<String>(state.tenantLinks));

        Operation.createPatch(this, state.documentSelfLink)
                .setBody(state)
                .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to update tenantLinks for composite component %s",
                                state.documentSelfLink);
                        post.fail(ex);
                    } else {
                        logInfo("Composite component %s updated with tenantLinks",
                                state.documentSelfLink);
                        if (compositeComponentsCount.decrementAndGet() == 0) {
                            logInfo("Composite components tranformation completed successfully");
                            post.complete();
                        }
                    }
                }).sendWith(getHost());
    }
}
