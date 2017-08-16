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

package com.vmware.admiral.compute.container;

import static com.vmware.admiral.common.util.ServiceUtils.addServiceRequestRoute;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.ServiceUtils;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.maintenance.ContainerStats;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerStatsService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.CONTAINER_STATS;

    public static final String CONTAINER_ID_QUERY_PARAM = "id";

    private static final long INSPECT_INTERVAL = TimeUnit.SECONDS.toMicros(70);
    private static final int MAX_SIZE = 10;

    @SuppressWarnings("unchecked")
    private static Map<String, Long> inspectCache = new LinkedHashMap(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_SIZE;
        }
    };

    @Override
    public void handleGet(Operation get) {
        Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
        String containerId = params.remove(CONTAINER_ID_QUERY_PARAM);
        if (containerId == null || containerId.isEmpty()) {
            get.fail(new IllegalArgumentException(
                    "URL parameter 'id' expected with container id as value."));
            return;
        }

        getContainerStateAndProcess(get, containerId);
    }

    /**
     * start processing the request - first fetch the ContainerState
     */
    private void getContainerStateAndProcess(Operation op, String id) {
        final String containerLink = UriUtils.buildUriPath(ContainerFactoryService.SELF_LINK, id);

        sendRequest(Operation
                .createGet(UriUtils.buildUri(getHost(), containerLink))
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Container '%s' not found", containerLink);
                        op.fail(Operation.STATUS_CODE_NOT_FOUND);
                        return;
                    }
                    ServiceUtils.handleExceptions(op, () -> {
                        ContainerState containerState = o.getBody(ContainerState.class);
                        processInspect(containerState, () ->
                                processStatsRequest(op, containerState));
                    });
                }));
    }

    /**
     * Request getting stats through the adapter and then return /stats as body response
     */
    private void processStatsRequest(Operation op, ContainerState containerState) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildUri(getHost(), containerState.documentSelfLink);
        request.operationTypeId = ContainerOperationType.STATS.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        sendRequest(Operation
                .createPatch(this, containerState.adapterManagementReference.toString())
                .setBodyNoCloning(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        // do not return, just log warning, previous /stats will be returned
                        logWarning("Exception in stats request for container: %s. Error: %s",
                                containerState.documentSelfLink, Utils.toString(ex));
                    }
                    forwardStatsResponse(op, containerState);
                }));
    }

    /**
     * Executes /stats request to the container state and copy its response to the GET operation.
     */
    private void forwardStatsResponse(Operation op, ContainerState containerState) {
        sendRequest(Operation
                .createGet(UriUtils.buildStatsUri(getHost(), containerState.documentSelfLink))
                .setExpiration(op.getExpirationMicrosUtc())
                .setCompletion((o, e) -> {
                    op.setBodyNoCloning(o.getBodyRaw());
                    op.setStatusCode(o.getStatusCode());
                    op.transferResponseHeadersFrom(o);
                    if (e != null) {
                        op.fail(e);
                    } else {
                        op.complete();
                    }
                }));
    }

    private void processInspect(ContainerState container, Runnable callback) {
        if (!isInspectionNeeded(container)) {
            callback.run();
            return;
        }

        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(getHost(), container.documentSelfLink);
        request.operationTypeId = ContainerOperationType.INSPECT.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        sendRequest(Operation
                .createPatch(getHost(), container.adapterManagementReference.toString())
                .setBodyNoCloning(request)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        logWarning("Error while inspect request for container: %s. Error: %s",
                                container.documentSelfLink, Utils.toString(ex));
                    }
                    callback.run();
                }));
    }

    private boolean isInspectionNeeded(ContainerState container) {
        Long lastInspect = inspectCache.get(container.documentSelfLink);
        if (lastInspect == null || lastInspect < Utils.fromNowMicrosUtc(-INSPECT_INTERVAL)) {
            inspectCache.put(container.documentSelfLink, Utils.getNowMicrosUtc());
            return true;
        }
        return false;
    }

    @Override
    public ServiceDocument getDocumentTemplate() {
        ServiceDocument template = super.getDocumentTemplate();
        addServiceRequestRoute(template, Action.GET,
                String.format("Get container stats. Provide the ContainerState id in URI query "
                        + "parameter with key \"%s\".", CONTAINER_ID_QUERY_PARAM),
                ContainerStats.class);
        return template;
    }

}
