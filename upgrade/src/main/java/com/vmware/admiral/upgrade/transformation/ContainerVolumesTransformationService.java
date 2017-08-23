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
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;
import com.vmware.xenon.services.common.QueryTask.Query.Occurance;
import com.vmware.xenon.services.common.QueryTask.QueryTerm.MatchType;

/**
 * The service is executed during the upgrade from VIC 1.1 to 1.2 and vRA 7.3 to 7.4. The service
 * adds the project from the host to the volumes
 */
public class ContainerVolumesTransformationService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CONTAINER_VOLUMES_UPGRADE_TRANSFORM_PATH;

    AtomicInteger hostsCount;

    @Override
    public void handlePost(Operation post) {
        QueryTask queryTask = QueryUtil.buildQuery(ComputeState.class, true);
        QueryUtil.addExpandOption(queryTask);
        List<ComputeState> hosts = new ArrayList<ComputeState>();
        new ServiceDocumentQuery<ComputeState>(getHost(), ComputeState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                        logSevere("Failed to get compute states");
                    } else if (r.hasResult()) {
                        hosts.add(r.getResult());
                    } else {
                        if (hosts.isEmpty()) {
                            post.complete();
                        }
                        logInfo("Number of hosts found: %d", hosts.size());
                        processHosts(hosts, post);
                    }
                });
    }

    private void processHosts(List<ComputeState> hosts, Operation post) {
        hostsCount = new AtomicInteger(hosts.size());
        for (ComputeState state : hosts) {
            QueryTask queryTask = QueryUtil.buildQuery(ContainerVolumeState.class, true);

            String parentLinksItemField = QueryTask.QuerySpecification
                    .buildCollectionItemName(ContainerVolumeState.FIELD_NAME_PARENT_LINKS);
            Query parentsClause = new Query()
                    .setTermPropertyName(parentLinksItemField)
                    .setTermMatchValue(state.documentSelfLink)
                    .setTermMatchType(MatchType.TERM)
                    .setOccurance(Occurance.MUST_OCCUR);

            queryTask.querySpec.query.addBooleanClause(parentsClause);
            QueryUtil.addExpandOption(queryTask);

            List<ContainerVolumeState> volumes = new ArrayList<ContainerVolumeState>();
            new ServiceDocumentQuery<ContainerVolumeState>(getHost(),
                    ContainerVolumeState.class)
                            .query(queryTask, (r) -> {
                                if (r.hasException()) {
                                    post.fail(r.getException());
                                    logSevere("Failed to get volumes containing parentLink %s",
                                            state.documentSelfLink);
                                } else if (r.hasResult()) {
                                    volumes.add(r.getResult());
                                } else {
                                    processVolumes(state, volumes, post);
                                    logInfo("Number of volumes found with containing parent link %s %d",
                                            state.documentSelfLink, volumes.size());
                                }
                            });
        }
    }

    private void processVolumes(ComputeState state, List<ContainerVolumeState> volumes,
            Operation post) {
        if (volumes.size() == 0) {
            logInfo("No volumes found for host %s",
                    state.documentSelfLink);
            if (hostsCount.decrementAndGet() == 0) {
                logInfo("Volumes tranformation completed successfully");
                post.complete();
                return;
            }
        }
        AtomicInteger volumesCount = new AtomicInteger(volumes.size());
        for (ContainerVolumeState volume : volumes) {
            if (volume.tenantLinks == null) {
                volume.tenantLinks = state.tenantLinks;
            } else {
                volume.tenantLinks.addAll(state.tenantLinks);
                volume.tenantLinks = new ArrayList<String>(
                        new LinkedHashSet<String>(volume.tenantLinks));
            }
            Operation.createPatch(this, volume.documentSelfLink)
                    .setBody(volume)
                    .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            logSevere("Failed to update tenantLinks for volume %s",
                                    volume.documentSelfLink);
                            post.fail(ex);
                        } else {
                            logInfo("Volume state %s updated with tenantLinks",
                                    volume.documentSelfLink);
                            if (volumesCount.decrementAndGet() == 0) {
                                if (hostsCount.decrementAndGet() == 0) {
                                    logInfo("Volumes tranformation completed successfully");
                                    post.complete();
                                }
                            }
                        }
                    }).sendWith(getHost());
        }
    }
}
