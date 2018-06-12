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

package com.vmware.xenon.services.rdbms;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceClient;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.common.config.XenonConfiguration;
import com.vmware.xenon.services.common.QueryTask;
import com.vmware.xenon.services.common.ServiceUriPaths;

public class NodeGroupChangeManager {

    private final ConcurrentHashMap<String, Long> factoryNodeGroupChangeTriggerTime = new ConcurrentHashMap<>();

    private int queryPageSize = XenonConfiguration.integer(
            NodeGroupChangeManager.class,
            "queryPageSize",
            100
    );

    private final ServiceHost host;

    public NodeGroupChangeManager(ServiceHost host) {
        this.host = host;
    }

    public void handleNodeGroupChangeForPersistencePeriodicFactory(String nodeSelectorPath,
            FactoryService factoryService) {
        if (this.host.isStopping()) {
            return;
        }

        String factoryPath = factoryService.getSelfLink();

        this.host.log(Level.INFO, "Reloading stateful services with periodic maintenance: %s",
                factoryPath);

        // update tracking time
        long startTimestamp = Utils.getNowMicrosUtc();
        this.factoryNodeGroupChangeTriggerTime.put(factoryPath, startTimestamp);

        // query with owner selection
        QueryTask queryTask = QueryTask.Builder.createDirectTask()
                .setResultLimit(this.queryPageSize)
                .addOptions(EnumSet
                        .of(QueryTask.QuerySpecification.QueryOption.FORWARD_ONLY,
                                QueryTask.QuerySpecification.QueryOption.OWNER_SELECTION))
                .setQuery(QueryTask.Query.Builder.create()
                        .addFieldClause(ServiceDocument.FIELD_NAME_SELF_LINK, factoryPath + "/",
                                QueryTask.QueryTerm.MatchType.PREFIX)
                        .build())
                .build();
        queryTask.nodeSelectorLink = nodeSelectorPath;

        // expiration
        long timeoutMicros = TimeUnit.SECONDS
                .toMicros(this.host.getPeerSynchronizationTimeLimitSeconds());
        timeoutMicros = Math.max(timeoutMicros, this.host.getOperationTimeoutMicros());
        queryTask.documentExpirationTimeMicros = Utils.fromNowMicrosUtc(timeoutMicros);

        // perform query
        Operation.createPost(this.host, ServiceUriPaths.CORE_LOCAL_QUERY_TASKS)
                .setBodyNoCloning(queryTask)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        this.host.log(Level.WARNING, "Failed to perform query for loading services: %s",
                                ex);
                        return;
                    }

                    String firstResultPageLink = op.getBody(QueryTask.class).results.nextPageLink;
                    handlePage(firstResultPageLink, factoryPath, startTimestamp);
                })
                .setReferer(this.host.getUri())
                .setExpiration(Utils.fromNowMicrosUtc(this.host.getOperationTimeoutMicros()))
                .sendWith(this.host);
    }

    private void handlePage(String pageLink, String factoryPath, long startTime) {
        if (this.host.isStopping()) {
            return;
        }

        if (pageLink == null) {
            return;
        }

        if (isNewNodeGroupChangeTriggered(factoryPath, startTime)) {
            // new node-group-change happened, do not proceed current one
            return;
        }

        // retrieve query result page
        Operation.createGet(this.host, pageLink)
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        this.host.log(Level.WARNING, "Failed to retrieve query result for loading services (page=%s): %s",
                                pageLink, ex);
                        return;
                    }

                    if (this.host.isStopping()) {
                        return;
                    }

                    if (isNewNodeGroupChangeTriggered(factoryPath, startTime)) {
                        // new node-group-change happened, do not proceed this one
                        return;
                    }

                    QueryTask queryTask = op.getBody(QueryTask.class);
                    List<String> servicePaths = queryTask.results.documentLinks;

                    // all services are local owner services (based on OWNER_SELECTION query)
                    for (String servicePath : servicePaths) {
                        handleLocalOwnerService(servicePath);
                    }

                    String nextPageLink = queryTask.results.nextPageLink;
                    handlePage(nextPageLink, factoryPath, startTime);

                })
                .setReferer(this.host.getUri())
                .sendWith(this.host);
    }

    private void handleLocalOwnerService(String servicePath) {
        Operation.CompletionHandler c = (o, e) -> {
            if (e != null) {
                this.host.log(Level.WARNING, "Loading service %s by node-group-change failed with status code %d: %s",
                        servicePath, o.getStatusCode(), e);
                return;
            }
        };

        // perform GET to bring the service to memory, then periodic maintenance will be triggered
        Operation.createGet(this.host, servicePath)
                .setCompletion(c)
                .setReferer(this.host.getUri())
                .setConnectionSharing(true)
                .setConnectionTag(ServiceClient.CONNECTION_TAG_SYNCHRONIZATION)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_NO_FORWARDING)
                .sendWith(this.host);
    }

    private boolean isNewNodeGroupChangeTriggered(String factoryPath, long startTime) {
        Long timestamp = this.factoryNodeGroupChangeTriggerTime.get(factoryPath);
        return timestamp == null || timestamp > startTime;
    }

    public void setQueryPageSize(int queryPageSize) {
        this.queryPageSize = queryPageSize;
    }

}
