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

import java.util.HashSet;
import java.util.Set;

import com.vmware.admiral.auth.util.ProjectUtil;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.admiral.compute.network.ComputeNetworkService.ComputeNetwork;
import com.vmware.photon.controller.model.query.QueryUtils;
import com.vmware.photon.controller.model.resources.ResourceGroupService;
import com.vmware.photon.controller.model.resources.ResourceGroupService.ResourceGroupState;
import com.vmware.photon.controller.model.resources.SecurityGroupService.SecurityGroupState;
import com.vmware.photon.controller.model.resources.SubnetService.SubnetState;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Service.Action;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query.Builder;

/**
 * Prevent deletion of {@link ResourceGroupState} if its in use by a {@link GroupResourcePlacementState}.
 */
public class ResourceGroupInterceptor {

    public static void register(OperationInterceptorRegistry registry) {
        registry.addServiceInterceptor(
                ResourceGroupService.class, Action.DELETE, ResourceGroupInterceptor::interceptDelete);
    }

    public static DeferredResult<Void> interceptDelete(Service service, Operation op) {
        ResourceGroupState currentState = service.getState(op);

        return DeferredResult.completed((Void)null)
                .thenCompose(v -> queryForLinkedProjects(service, currentState))
                .thenCompose(v -> queryForLinkedNetworks(service, currentState))
                .thenCompose(v -> queryForLinkedSubnets(service, currentState))
                .thenCompose(v -> queryForLinkedSecurityGroups(service, currentState));
    }

    private static DeferredResult<Void> queryForLinkedProjects(Service service, ResourceGroupState
            currentState) {

        QueryTask queryTask = ProjectUtil.createQueryTaskForProjectAssociatedWithPlacement(
                currentState, currentState.query);
        return QueryUtils.startQueryTask(service, queryTask).thenAccept(qt -> {
            ServiceDocumentQueryResult result = qt.results;
            long documentCount = result.documentCount;
            if (documentCount != 0) {
                throw new LocalizableValidationException(
                        ProjectUtil.PROJECT_IN_USE_MESSAGE,
                        ProjectUtil.PROJECT_IN_USE_MESSAGE_CODE,
                        documentCount, documentCount > 1 ? "s" : "");
            }
        });
    }

    private static DeferredResult<Void> queryForLinkedNetworks(Service service,
            ResourceGroupState currentState) {

        Builder builder = Builder.create()
                .addKindFieldClause(ComputeNetwork.class)
                .addCollectionItemClause(ComputeNetwork.FIELD_NAME_GROUP_LINKS,
                        currentState.documentSelfLink);
        QueryUtils.QueryByPages<ComputeNetwork> query = new QueryUtils.QueryByPages<>(
                service.getHost(),
                builder.build(), ComputeNetwork.class, currentState.tenantLinks);

        Set<String> networkLinks = new HashSet<>();
        return query.queryLinks(n -> networkLinks.add(n))
                .thenAccept(v -> {
                    int documentCount = networkLinks.size();
                    if (documentCount != 0) {
                        throw new LocalizableValidationException(
                                String.format("Resource Group is associated to %s network%s",
                                        documentCount, documentCount > 1 ? "s" : ""),
                                "compute.network.resource-group.in.use",
                                documentCount, documentCount > 1 ? "s" : "");
                    }
                });
    }

    private static DeferredResult<Void> queryForLinkedSubnets(Service service,
            ResourceGroupState currentState) {

        Builder builder = Builder.create()
                .addKindFieldClause(SubnetState.class)
                .addCollectionItemClause(SubnetState.FIELD_NAME_GROUP_LINKS,
                        currentState.documentSelfLink);
        QueryUtils.QueryByPages<SubnetState> query = new QueryUtils.QueryByPages<>(
                service.getHost(),
                builder.build(), SubnetState.class, currentState.tenantLinks);

        Set<String> subnetLinks = new HashSet<>();
        return query.queryLinks(n -> subnetLinks.add(n))
                .thenAccept(v -> {
                    int documentCount = subnetLinks.size();
                    if (documentCount != 0) {
                        throw new LocalizableValidationException(
                                String.format("Resource Group is associated to %s subnet%s",
                                        documentCount, documentCount > 1 ? "s" : ""),
                                "subnet.resource-group.in.use",
                                documentCount, documentCount > 1 ? "s" : "");
                    }
                });
    }

    private static DeferredResult<Void> queryForLinkedSecurityGroups(Service service,
            ResourceGroupState currentState) {

        Builder builder = Builder.create()
                .addKindFieldClause(SecurityGroupState.class)
                .addCollectionItemClause(SecurityGroupState.FIELD_NAME_GROUP_LINKS,
                        currentState.documentSelfLink);
        QueryUtils.QueryByPages<SecurityGroupState> query = new QueryUtils.QueryByPages<>(
                service.getHost(),
                builder.build(), SecurityGroupState.class, currentState.tenantLinks);

        Set<String> sgLinks = new HashSet<>();
        return query.queryLinks(sg -> sgLinks.add(sg))
                .thenAccept(v -> {
                    int documentCount = sgLinks.size();
                    if (documentCount != 0) {
                        throw new LocalizableValidationException(
                                String.format("Resource Group is associated to %s security group%s",
                                        documentCount, documentCount > 1 ? "s" : ""),
                                "security.group.resource-group.in.use",
                                documentCount, documentCount > 1 ? "s" : "");
                    }
                });
    }
}