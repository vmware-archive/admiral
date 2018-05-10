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

package com.vmware.admiral.upgrade.transformation;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.photon.controller.model.resources.ComputeDescriptionService.ComputeDescription.ComputeType;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState;
import com.vmware.photon.controller.model.resources.ResourcePoolService.ResourcePoolState.ResourcePoolProperty;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Operation.CompletionHandler;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.QueryTask.Query;

/**
 * One-time node group setup (bootstrap) for removing the 'type' part of the elastic query
 *
 * The service will update all the elastic pools and remove the 'type' clause from the elastic query
 *
 * This service is guaranteed to be performed only once within entire node group, in a consistent
 * safe way. Durable for restarting the owner node or even complete shutdown and restarting of all
 * nodes. Following the SampleBootstrapService.
 */
public class ResourcePoolTransformationBootstrapService extends StatefulService {

    public static final String FACTORY_LINK = ManagementUriParts.CONFIG
            + "/pools-upgrade-bootstrap";

    public static FactoryService createFactory() {
        return FactoryService.create(ResourcePoolTransformationBootstrapService.class);
    }

    public static CompletionHandler startTask(ServiceHost host) {
        return (o, e) -> {
            if (e != null) {
                host.log(Level.SEVERE, Utils.toString(e));
                return;
            }
            // create service with fixed link
            // POST will be issued multiple times but will be converted to PUT after the first one.
            ServiceDocument doc = new ServiceDocument();
            doc.documentSelfLink = "pool-upgrade-bootstrap-task";
            Operation.createPost(host, ResourcePoolTransformationBootstrapService.FACTORY_LINK)
                    .setBody(doc)
                    .setReferer(host.getUri())
                    .setCompletion((oo, ee) -> {
                        if (ee != null) {
                            host.log(Level.SEVERE, Utils.toString(ee));
                            return;
                        }
                        host.log(Level.INFO, "pools-upgrade-bootstrap-task");
                    })
                    .sendWith(host);
        };
    }

    public ResourcePoolTransformationBootstrapService() {
        super(ServiceDocument.class);
        toggleOption(ServiceOption.IDEMPOTENT_POST, true);
        toggleOption(ServiceOption.PERSISTENCE, true);
        toggleOption(ServiceOption.REPLICATION, true);
        toggleOption(ServiceOption.OWNER_SELECTION, true);
    }

    @Override
    public void handleStart(Operation post) {
        QueryTask queryTask = QueryUtil.buildQuery(ResourcePoolState.class, true);
        QueryUtil.addExpandOption(queryTask);
        List<ResourcePoolState> resourcePools = new ArrayList<ResourcePoolState>();
        new ServiceDocumentQuery<ResourcePoolState>(getHost(), ResourcePoolState.class)
                .query(queryTask, (r) -> {
                    if (r.hasException()) {
                        post.fail(r.getException());
                    } else if (r.hasResult()) {
                        resourcePools.add(r.getResult());
                    } else {
                        getHost().log(Level.INFO, "pools found: %d", resourcePools.size());
                        if (resourcePools.size() == 0) {
                            post.complete();
                            return;
                        } else {
                            processPools(resourcePools, post);
                        }
                    }
                });
    }

    private void processPools(List<ResourcePoolState> pools, Operation post) {
        for (ResourcePoolState pool : pools) {
            // not elastic pools should not be updated
            if (pool.properties != null && pool.properties.contains(ResourcePoolProperty.ELASTIC)) {
                for (Query query : pool.query.booleanClauses) {
                    // For compute type VM_GUEST there is only one clause
                    if (query.booleanClauses != null && query.booleanClauses.size() == 1) {
                        if (query.booleanClauses.get(0).term.matchValue
                                .equals(ComputeType.VM_GUEST.toString())) {
                            pool.query.booleanClauses.remove(query);
                            Operation.createPatch(this, pool.documentSelfLink)
                                    .setBody(pool)
                                    .setReferer(UriUtils.buildUri(getHost(), FACTORY_LINK))
                                    .setCompletion((o, ex) -> {
                                        if (ex != null) {
                                            logSevere("Failed to update query for resource pool %s",
                                                    pool.documentSelfLink);
                                            logSevere(ex);
                                        } else {
                                            logInfo("Resource pool state %s query updated",
                                                    pool.documentSelfLink);
                                        }
                                    }).sendWith(getHost());
                            break;
                        }
                    }

                }
            }
        }
        post.complete();
    }

    @Override
    public void handlePut(Operation put) {
        if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
            // converted PUT due to IDEMPOTENT_POST option
            put.complete();
            return;
        }
        // normal PUT is not supported
        put.fail(Operation.STATUS_CODE_BAD_METHOD);
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        com.vmware.photon.controller.model.ServiceUtils.setRetentionLimit(template);
        return template;
    }

}