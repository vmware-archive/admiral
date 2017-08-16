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

package com.vmware.admiral.compute.transformation;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * The service is executed during the upgrade from VIC 1.1 to 1.2 and vRA 7.3 to 7.4. The service
 * checks if there is more that 1 resource pool for placement and if there is a new the service
 * creates a new pool and changes the placement to point to the new pool
 *
 */
public class ResourcePoolTransformationService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.RESOURCE_POOL_UPGRADE_TRANSFORM_PATH;

    AtomicInteger poolsCount;

    @Override
    public void handlePost(Operation post) {
        URI serviceUri = UriUtils.buildUri(ElasticPlacementZoneConfigurationService.SELF_LINK);
        Operation.createGet(this, UriUtils.buildExpandLinksQueryUri(serviceUri).toString())
                .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to get resource pool states");
                        post.fail(ex);
                    } else {
                        ServiceDocumentQueryResult queryResult = op
                                .getBody(ServiceDocumentQueryResult.class);
                        Map<String, ElasticPlacementZoneConfigurationState> states = QueryUtil
                                .extractQueryResult(
                                        queryResult, ElasticPlacementZoneConfigurationState.class);
                        logInfo("Number of resource pool states found %d", states.size());
                        if (states.isEmpty()) {
                            logInfo("No resource pools found. Tranformation completed successfully");
                            post.complete();
                            return;
                        }
                        poolsCount = new AtomicInteger(states.size());
                        processPools(states.values(), post);
                    }
                }).sendWith(getHost());
    }

    private void processPools(Collection<ElasticPlacementZoneConfigurationState> collection,
            Operation post) {
        // Get the placements for every pool. If there is more that one placement, clone the pool
        // and point one of the placements to it.
        for (ElasticPlacementZoneConfigurationState state : collection) {
            QueryTask queryTask = QueryUtil.buildPropertyQuery(GroupResourcePlacementState.class,
                    GroupResourcePlacementState.FIELD_NAME_RESOURCE_POOL_LINK,
                    state.resourcePoolState.documentSelfLink);
            QueryUtil.addExpandOption(queryTask);

            QueryUtil.addBroadcastOption(queryTask);
            List<GroupResourcePlacementState> placements = new ArrayList<GroupResourcePlacementState>();
            new ServiceDocumentQuery<GroupResourcePlacementState>(getHost(),
                    GroupResourcePlacementState.class)
                            .query(queryTask, (r) -> {
                                if (r.hasException()) {
                                    logSevere(
                                            "Failed to query resource pool placement states eith resource pool link %s",
                                            state.documentSelfLink);
                                    post.fail(r.getException());
                                } else if (r.hasResult()) {
                                    placements.add(r.getResult());
                                } else {
                                    logInfo("Resource pool placements found %d for resource pool %s",
                                            placements.size(), state.documentSelfLink);
                                    processPlacement(state, placements, post);
                                }
                            });
        }
    }

    private void processPlacement(ElasticPlacementZoneConfigurationState state,
            List<GroupResourcePlacementState> placements, Operation post) {
        if (placements.size() > 1) {
            // Update only tenant links for the pool of the first placement
            updatePoolTenantLinks(state, post, placements.get(0), false);
            // skip the first placement. Only the duplicates should be updated and a new pool should
            // be created
            AtomicInteger processedCount = new AtomicInteger();
            String poolName = state.resourcePoolState.name;
            for (int i = 1; i < placements.size(); i++) {
                GroupResourcePlacementState placement = placements.get(i);
                state.resourcePoolState.id = null;
                state.resourcePoolState.documentSelfLink = null;
                state.resourcePoolState.name = poolName + "-" + placement.name;
                if (state.epzState != null) {
                    state.epzState.documentSelfLink = null;
                }
                state.resourcePoolState.tenantLinks = new ArrayList<>();
                if (placement.tenantLinks != null) {
                    state.resourcePoolState.tenantLinks.addAll(placement.tenantLinks);
                }
                state.documentSelfLink = null;
                Operation.createPost(this, ElasticPlacementZoneConfigurationService.SELF_LINK)
                        .setBody(state)
                        .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                        .setCompletion((o, ex) -> {
                            if (ex != null) {
                                logSevere("Cloning placement zone failed");
                                post.fail(ex);
                            } else {
                                logInfo("Resource pool created: %s",
                                        o.getBody(
                                                ElasticPlacementZoneConfigurationState.class).documentSelfLink);
                                placement.resourcePoolLink = o
                                        .getBody(
                                                ElasticPlacementZoneConfigurationState.class).resourcePoolState.documentSelfLink;
                                sendRequest(Operation
                                        .createPut(this, placement.documentSelfLink)
                                        .setBody(placement)
                                        .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                                        .setCompletion((operation, e) -> {
                                            if (e != null) {
                                                logSevere(
                                                        "Failed to update placement %s with resource pool %s",
                                                        placement.documentSelfLink,
                                                        placement.resourcePoolLink);
                                                post.fail(e);
                                            }
                                            logInfo("placement [%s] updated with resource pool [%s].",
                                                    placement.documentSelfLink,
                                                    placement.resourcePoolLink);
                                            if (processedCount.incrementAndGet() + 1 == placements
                                                    .size()) {
                                                if (poolsCount.decrementAndGet() == 0) {
                                                    logInfo("Resource pool tranformation completed successfully");
                                                    post.complete();
                                                }
                                            }
                                        }));
                            }
                        }).sendWith(getHost());
            }
        } else {
            GroupResourcePlacementState placement = placements.get(0);
            updatePoolTenantLinks(state, post, placement, true);
        }

    }

    private void updatePoolTenantLinks(ElasticPlacementZoneConfigurationState state, Operation post,
            GroupResourcePlacementState placement, boolean decrementCount) {
        state.resourcePoolState.tenantLinks = new ArrayList<>();

        if (placement.tenantLinks != null) {
            state.resourcePoolState.tenantLinks.addAll(placement.tenantLinks);
        }
        Operation.createPost(this, ElasticPlacementZoneConfigurationService.SELF_LINK)
                .setBody(state)
                .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Failed to update resource pool with tenant links");
                        post.fail(ex);
                    } else {
                        logInfo("Resource pool %s updated with tenant links",
                                state.documentSelfLink);
                        if (decrementCount) {
                            if (poolsCount.decrementAndGet() == 0) {
                                // add tenant links to the pool
                                logInfo("Resource pool tranformation completed successfully");
                                post.complete();
                            }
                        }
                    }
                }).sendWith(getHost());
    }
}
