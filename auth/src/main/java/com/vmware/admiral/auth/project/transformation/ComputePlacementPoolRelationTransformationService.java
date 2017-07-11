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

package com.vmware.admiral.auth.project.transformation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.vmware.admiral.auth.project.ProjectService;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService;
import com.vmware.admiral.compute.ElasticPlacementZoneConfigurationService.ElasticPlacementZoneConfigurationState;
import com.vmware.admiral.compute.ElasticPlacementZoneService.ElasticPlacementZoneState;
import com.vmware.admiral.compute.container.GroupResourcePlacementService.GroupResourcePlacementState;
import com.vmware.photon.controller.model.query.QueryUtils.QueryByPages;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.photon.controller.model.resources.TagFactoryService;
import com.vmware.photon.controller.model.resources.TagService;
import com.vmware.photon.controller.model.resources.TagService.TagState;
import com.vmware.photon.controller.model.tasks.helpers.ResourcePoolQueryHelper;
import com.vmware.xenon.common.DeferredResult;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * The logic is executed during the upgrade from VIC 1.1 to 1.2 and vRA 7.3 to 7.4. The service
 * parforms the following operations for every placement: 1. Gets a resource pool for a placement 2.
 * Finds all the hosts for the resource pool 3. Creates a tag for the project 4. Updates the hosts
 * with the tag and the project from the placement 5. Adds the created tag to the resource pool
 */
public class ComputePlacementPoolRelationTransformationService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.COMPUTE_UPGRADE_TRANSFORM_PATH;
    private AtomicInteger placementsToProcess;
    private AtomicBoolean failed = new AtomicBoolean();
    private ConcurrentHashMap<String, Set<String>> poolTags = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ElasticPlacementZoneConfigurationState> pools = new ConcurrentHashMap<>();

    @Override
    public void handlePost(Operation post) {
        DeferredResult<List<GroupResourcePlacementState>> result = getPlacements();
        result.whenComplete((placements, ex) -> {
            if (ex != null) {
                post.fail(ex);
            } else {
                if (placements.isEmpty()) {
                    logInfo("No placements found. Transformation completed successfully");
                    post.complete();
                    return;
                } else {
                    this.placementsToProcess = new AtomicInteger(placements.size());
                    processPlacements(placements, post);
                }
            }
        });
    }

    private void processPlacements(List<GroupResourcePlacementState> groupPlacements,
            Operation post) {
        for (GroupResourcePlacementState placement : groupPlacements) {
            DeferredResult<ElasticPlacementZoneConfigurationState> result = getResourcePoolForPlacement(
                    placement);
            result.whenComplete((pool, e) -> {
                if (e != null) {
                    if (this.failed.compareAndSet(false, true)) {
                        post.fail(e);
                        return;
                    }
                } else {
                    processPool(pool, placement, post);
                }
            });
        }
    }

    private DeferredResult<List<GroupResourcePlacementState>> getPlacements() {
        Query query = Query.Builder.create().addKindFieldClause(GroupResourcePlacementState.class)
                .build();
        return new QueryByPages<>(this.getHost(), query, GroupResourcePlacementState.class, null)
                .collectDocuments(Collectors.toList());
    }

    private DeferredResult<ElasticPlacementZoneConfigurationState> getResourcePoolForPlacement(
            GroupResourcePlacementState placement) {
        String link = ElasticPlacementZoneConfigurationService.SELF_LINK
                + placement.resourcePoolLink;
        Operation operation = Operation.createGet(this, link);
        return sendWithDeferredResult(operation, ElasticPlacementZoneConfigurationState.class);
    }

    private void processPool(ElasticPlacementZoneConfigurationState pool,
            GroupResourcePlacementState placement, Operation post) {

        DeferredResult<List<ComputeState>> result = getHostsForPool(pool);
        result.whenComplete((hosts, ex) -> {
            if (ex != null) {
                if (failed.compareAndSet(false, true)) {
                    post.fail(ex);
                }
            } else {
                processHosts(hosts, pool, placement, post);
            }
        });
    }

    private DeferredResult<List<ComputeState>> getHostsForPool(
            ElasticPlacementZoneConfigurationState pool) {
        ResourcePoolQueryHelper helper = ResourcePoolQueryHelper.createForResourcePool(getHost(),
                pool.documentSelfLink);
        helper.setExpandComputes(true);
        DeferredResult<List<ComputeState>> deferredResult = new DeferredResult<>();
        QueryTask queryTask = QueryUtil.buildPropertyQuery(ComputeState.class,
                ComputeState.FIELD_NAME_RESOURCE_POOL_LINK,
                pool.documentSelfLink);
        QueryUtil.addExpandOption(queryTask);

        helper.query((qr) -> {
            if (qr.error != null) {
                logSevere(
                        "Failed to query hosts with resource pool link %s",
                        pool.documentSelfLink);
                deferredResult.fail(qr.error);
            } else {
                logInfo("Hosts found %d for resource pool %s",
                        qr.computesByLink.size(), pool.documentSelfLink);
                deferredResult.complete(new ArrayList<>(qr.computesByLink.values()));
            }
        });
        return deferredResult;
    }

    private void processHosts(List<ComputeState> hosts, ElasticPlacementZoneConfigurationState pool,
            GroupResourcePlacementState placement, Operation post) {
        TagState tag = new TagState();
        tag.key = placement.name;
        tag.value = "";
        tag.tenantLinks = new ArrayList<>();

        if (placement.tenantLinks != null && !placement.tenantLinks.isEmpty()) {
            tag.tenantLinks.addAll(placement.tenantLinks);
        } else {
            // if the placement does not have tenant links set the default
            // project
            tag.tenantLinks.add(ProjectService.DEFAULT_PROJECT_LINK);
        }

        Operation.createPost(this, TagService.FACTORY_LINK)
                .setBody(tag)
                .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logSevere("Creating tag with key %s failed", tag.key);
                        if (failed.compareAndSet(false, true)) {
                            post.fail(ex);
                        }
                    } else {
                        logInfo("Tag created or already existing for placement %s", placement.name);
                        String tagSelfLink = TagFactoryService.generateSelfLink(tag);
                        if (poolTags.get(pool.resourcePoolState.documentSelfLink) == null) {
                            poolTags.put(pool.resourcePoolState.documentSelfLink, new HashSet<>());
                        }
                        poolTags.get(pool.resourcePoolState.documentSelfLink).add(tagSelfLink);
                        pools.put(pool.resourcePoolState.documentSelfLink, pool);
                        patchStates(hosts, placement, pool, tagSelfLink, post);
                    }
                }).sendWith(getHost());
    }

    private void patchStates(List<ComputeState> hosts,
            GroupResourcePlacementState placement, ElasticPlacementZoneConfigurationState pool,
            String tagSelfLink, Operation post) {
        if (hosts.isEmpty()) {
            updatePlacementAndPool(placement, tagSelfLink, post);
            return;
        }
        AtomicInteger hostsToProcess = new AtomicInteger(hosts.size());
        // Patch the hosts with tenant links and add a tag
        for (ComputeState host : hosts) {
            if (host.tenantLinks == null) {
                host.tenantLinks = new ArrayList<>();
            }
            if (placement.tenantLinks != null && !placement.tenantLinks.isEmpty()) {
                host.tenantLinks.addAll(placement.tenantLinks);
            } else {
                // if the placement does not have tenant links set the default
                // project
                host.tenantLinks.add(ProjectService.DEFAULT_PROJECT_LINK);
            }

            if (host.tagLinks == null) {
                host.tagLinks = new HashSet<>();
            }
            host.tagLinks.add(tagSelfLink);

            Operation.createPatch(this, host.documentSelfLink)
                    .setBody(host)
                    .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            logSevere("Updating host %s with tenant links and tag failed",
                                    host.documentSelfLink);
                            if (this.failed.compareAndSet(false, true)) {
                                post.fail(ex);
                            }
                        } else {
                            logInfo("Host updated with tenant links and tag %s",
                                    host.documentSelfLink);
                            if (hostsToProcess.decrementAndGet() == 0) {
                                updatePlacementAndPool(placement, tagSelfLink, post);
                            }
                        }
                    }).sendWith(getHost());
        }
    }

    private void updatePlacementAndPool(GroupResourcePlacementState placement, String tagSelfLink,
            Operation post) {
        if (placement.tenantLinks == null) {
            placement.tenantLinks = new ArrayList<>();
        }
        if (placement.tenantLinks.isEmpty()) {
            placement.tenantLinks.add(ProjectService.DEFAULT_PROJECT_LINK);
        }

        Operation.createPut(this, placement.documentSelfLink)
                .setBody(placement)
                .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                .setCompletion((oo, ex2) -> {
                    if (ex2 != null) {
                        logSevere("Updating placement %s with tenant links",
                                placement.documentSelfLink);
                        if (failed.compareAndSet(false, true)) {
                            post.fail(ex2);
                        }
                    } else {
                        logInfo("placement %s updated", placement.documentSelfLink);
                        if (this.placementsToProcess.decrementAndGet() == 0) {
                            logInfo("Transformation completed successfully");
                            updateEPZTags(post);
                        }
                    }
                }).sendWith(getHost());
    }

    private void updateEPZTags(Operation post) {
        AtomicInteger poolsToProcess = new AtomicInteger(poolTags.size());
        for (Entry<String, Set<String>> entry : poolTags.entrySet()) {
            ElasticPlacementZoneConfigurationState pool = pools.get(entry.getKey());
            if (pool.epzState == null) {
                pool.epzState = new ElasticPlacementZoneState();
            }
            if (pool.epzState.tagLinksToMatch == null) {
                pool.epzState.tagLinksToMatch = new HashSet<>();
            }
            pool.epzState.tagLinksToMatch.addAll(entry.getValue());
            pool.epzState.resourcePoolLink = pool.resourcePoolState.documentSelfLink;
            Operation.createPatch(this, ElasticPlacementZoneConfigurationService.SELF_LINK)
                    .setBody(pool)
                    .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            logSevere("Updating resource pool %s with tag failed",
                                    pool.resourcePoolState.documentSelfLink);
                            if (failed.compareAndSet(false, true)) {
                                post.fail(ex);
                            }
                        } else {
                            logInfo("resource pool %s updated with tags",
                                    pool.resourcePoolState.documentSelfLink);
                            if (poolsToProcess.decrementAndGet() == 0) {
                                post.complete();
                            }
                        }
                    }).sendWith(getHost());
        }
    }
}
