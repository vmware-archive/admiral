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
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.services.common.QueryTask;

/**
 * The service is executed during the upgrade from VIC 1.1 to 1.2 and vRA 7.3 to 7.4. The service
 * adds the project from the host to the containers
 */
public class ContainersTransformationService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CONTAINERS_UPGRADE_TRANSFORM_PATH;

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
            QueryTask queryTask = QueryUtil.buildPropertyQuery(ContainerState.class,
                    ContainerState.FIELD_NAME_PARENT_LINK,
                    state.documentSelfLink);
            QueryUtil.addExpandOption(queryTask);
            List<ContainerState> containers = new ArrayList<ContainerState>();
            new ServiceDocumentQuery<ContainerState>(getHost(),
                    ContainerState.class)
                            .query(queryTask, (r) -> {
                                if (r.hasException()) {
                                    post.fail(r.getException());
                                    logSevere("Failed to get container states with parentLink %s",
                                            state.documentSelfLink);
                                } else if (r.hasResult()) {
                                    containers.add(r.getResult());
                                } else {
                                    processContainers(state, containers, post);
                                    logInfo("Number of containers found with parentLink %s %d",
                                            state.documentSelfLink, containers.size());
                                }
                            });
        }
    }

    private void processContainers(ComputeState state, List<ContainerState> containers,
            Operation post) {
        if (containers.size() == 0) {
            logInfo("No containers found for host %s",
                    state.documentSelfLink);
            if (hostsCount.decrementAndGet() == 0) {
                logInfo("Containers tranformation completed successfully");
                post.complete();
                return;
            }
        }
        AtomicInteger containersCount = new AtomicInteger(containers.size());
        for (ContainerState container : containers) {
            if (container.tenantLinks == null) {
                container.tenantLinks = state.tenantLinks;
            } else {
                container.tenantLinks.addAll(state.tenantLinks);
                container.tenantLinks = new ArrayList<String>(
                        new LinkedHashSet<String>(container.tenantLinks));
            }
            Operation.createPatch(this, container.documentSelfLink)
                    .setBody(container)
                    .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            logSevere("Failed to update tenantLinks for container %s",
                                    container.documentSelfLink);
                            post.fail(ex);
                        } else {
                            logInfo("Container state %s updated with tenantLinks",
                                    container.documentSelfLink);
                            if (containersCount.decrementAndGet() == 0) {
                                if (hostsCount.decrementAndGet() == 0) {
                                    logInfo("Containers tranformation completed successfully");
                                    post.complete();
                                }
                            }
                        }
                    }).sendWith(getHost());
        }
    }
}
