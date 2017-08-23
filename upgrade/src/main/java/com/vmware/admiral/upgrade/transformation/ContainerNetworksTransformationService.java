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
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
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
 * adds the project from the host to the networks
 */
public class ContainerNetworksTransformationService extends StatelessService {

    public static final String SELF_LINK = ManagementUriParts.CONTAINER_NETWORKS_UPGRADE_TRANSFORM_PATH;

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
            QueryTask queryTask = QueryUtil.buildQuery(ContainerNetworkState.class, true);

            String parentLinksItemField = QueryTask.QuerySpecification
                    .buildCollectionItemName(ContainerNetworkState.FIELD_NAME_PARENT_LINKS);
            Query parentsClause = new Query()
                    .setTermPropertyName(parentLinksItemField)
                    .setTermMatchValue(state.documentSelfLink)
                    .setTermMatchType(MatchType.TERM)
                    .setOccurance(Occurance.MUST_OCCUR);

            queryTask.querySpec.query.addBooleanClause(parentsClause);
            QueryUtil.addExpandOption(queryTask);

            List<ContainerNetworkState> networks = new ArrayList<ContainerNetworkState>();
            new ServiceDocumentQuery<ContainerNetworkState>(getHost(),
                    ContainerNetworkState.class)
                            .query(queryTask, (r) -> {
                                if (r.hasException()) {
                                    post.fail(r.getException());
                                    logSevere("Failed to get networks containing parentLink %s",
                                            state.documentSelfLink);
                                } else if (r.hasResult()) {
                                    networks.add(r.getResult());
                                } else {
                                    processNetworks(state, networks, post);
                                    logInfo("Number of networks found with containing parent link %s %d",
                                            state.documentSelfLink, networks.size());
                                }
                            });
        }
    }

    private void processNetworks(ComputeState state, List<ContainerNetworkState> networks,
            Operation post) {
        if (networks.size() == 0) {
            logInfo("No networks found for host %s",
                    state.documentSelfLink);
            if (hostsCount.decrementAndGet() == 0) {
                logInfo("Networks tranformation completed successfully");
                post.complete();
                return;
            }
        }
        AtomicInteger networksCount = new AtomicInteger(networks.size());
        for (ContainerNetworkState network : networks) {
            if (network.tenantLinks == null) {
                network.tenantLinks = state.tenantLinks;
            } else {
                network.tenantLinks.addAll(state.tenantLinks);
                network.tenantLinks = new ArrayList<String>(
                        new LinkedHashSet<String>(network.tenantLinks));
            }
            Operation.createPatch(this, network.documentSelfLink)
                    .setBody(network)
                    .setReferer(UriUtils.buildUri(getHost(), SELF_LINK))
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            logSevere("Failed to update tenantLinks for network %s",
                                    network.documentSelfLink);
                            post.fail(ex);
                        } else {
                            logInfo("Network state %s updated with tenantLinks",
                                    network.documentSelfLink);
                            if (networksCount.decrementAndGet() == 0) {
                                if (hostsCount.decrementAndGet() == 0) {
                                    logInfo("Networks tranformation completed successfully");
                                    post.complete();
                                }
                            }
                        }
                    }).sendWith(getHost());
        }
    }
}
