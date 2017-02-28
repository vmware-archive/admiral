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

package com.vmware.admiral.compute.kubernetes.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.KubernetesOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.entities.pods.Container;
import com.vmware.admiral.compute.kubernetes.service.PodService.PodState;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class PodLogService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.KUBERNETES_POD_LOGS;
    public static final String POD_ID_QUERY_PARAM = "id";

    @Override
    public void handleGet(Operation get) {
        Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
        String podId = params.remove(POD_ID_QUERY_PARAM);
        if (podId == null || podId.isEmpty()) {
            get.fail(new IllegalArgumentException(
                    "URL parameter 'id' expected with container id as value."));
            return;
        }

        String podStateLink = UriUtils.buildUriPath(PodService.FACTORY_LINK, podId);

        sendRequest(Operation
                .createGet(this, podStateLink)
                .setCompletion((o, ex) -> {
                    if (ex != null) {
                        get.fail(ex);
                    } else {
                        PodState podState = o.getBody(PodState.class);
                        processPodState(get, podState);
                    }
                }));
    }

    private void processPodState(Operation get, PodState podState) {
        Map<String, LogServiceState> resultLogs = new HashMap<>();

        AtomicInteger counter = new AtomicInteger(podState.pod.spec.containers.size());
        AtomicBoolean hasError = new AtomicBoolean(false);

        for (Container container : podState.pod.spec.containers) {
            String podLogLink = KubernetesUtil.buildLogUriPath(podState, container.name);

            sendRequest(Operation.createGet(this, podLogLink)
                    .setCompletion((o, ex) -> {
                        if (ex != null) {
                            if (hasError.compareAndSet(false, true)) {
                                get.fail(ex);
                                createAdapterRequest(podState);
                            }
                        } else {
                            resultLogs.put(container.name, o.getBody(LogServiceState.class));
                            if (counter.decrementAndGet() == 0 && !hasError.get()) {
                                get.setBody(resultLogs);
                                get.complete();
                                createAdapterRequest(podState);
                            }
                        }
                    }));
        }

    }

    private void createAdapterRequest(PodState pod) {
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(getHost(), pod.documentSelfLink);
        request.operationTypeId = KubernetesOperationType.FETCH_LOGS.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        sendRequest(Operation.createPatch(getHost(), ManagementUriParts.ADAPTER_KUBERNETES)
                .setBody(request)
                .setContextId(Service.getId(getSelfLink()))
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Adapter request for container logs %s failed. Error: %s",
                                pod.documentSelfLink, Utils.toString(ex));
                        return;
                    }
                }));
    }
}
