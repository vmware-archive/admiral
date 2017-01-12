/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.compute.container;

import java.util.Map;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.service.common.LogService;
import com.vmware.admiral.service.common.LogService.LogServiceState;
import com.vmware.admiral.service.common.ServiceTaskCallback;
import com.vmware.xenon.common.LocalizableValidationException;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class ContainerLogService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.CONTAINER_LOGS;
    public static final String CONTAINER_ID_QUERY_PARAM = "id";

    @Override
    public void handleGet(Operation get) {
        Map<String, String> params = UriUtils.parseUriQueryParams(get.getUri());
        String containerId = params.remove(CONTAINER_ID_QUERY_PARAM);
        if (containerId == null || containerId.isEmpty()) {
            get.fail(new IllegalArgumentException(
                    "URL parameter 'id' expected with container id as value."));
            return;
        }

        final String containerLogsLink = UriUtils.buildUriPath(LogService.FACTORY_LINK,
                containerId);

        sendRequest(Operation.createGet(this, containerLogsLink)
                .setCompletion((o, e) -> {
                    if (e != null) {
                        requestLog(get, containerId, params);
                    } else {
                        get.setBody(o.getBody(LogServiceState.class)).complete();
                        requestLog(null, containerId, params);
                    }
                }));
    }

    private void requestLog(Operation get, String containerId, Map<String, String> params) {
        sendRequest(Operation.createGet(this,
                UriUtils.buildUriPath(ContainerFactoryService.SELF_LINK, containerId))
                .setCompletion((o, e) -> {
                    if (e != null) {
                        String errMsg = String.format("Can't get container %s. Error: %s",
                                containerId, Utils.toString(e));
                        logWarning(errMsg);
                        if (get != null) {
                            get.fail(new LocalizableValidationException(errMsg, "compute.container.log.container.unavailable",
                                    containerId, Utils.toString(e)));
                        }
                        return;
                    }
                    ContainerState container = o.getBody(ContainerState.class);
                    if (get != null) {
                        LogServiceState logBody = new LogServiceState();
                        logBody.logs = "--".getBytes();
                        logBody.tenantLinks = container.tenantLinks;
                        get.setBody(logBody).complete();
                    }

                    if (container.powerState != null && !container.powerState.isUnmanaged()
                            && container.powerState != ContainerState.PowerState.UNKNOWN) {
                        createAdapterRequest(container, params);
                    } else {
                        logWarning("Can't get logs for container %s. Container power state is %s",
                                containerId, container.powerState);
                    }
                }));
    }

    private void createAdapterRequest(ContainerState container, Map<String, String> params) {
        if (container.adapterManagementReference == null) {
            logWarning("Container adapterManagementReference is null for container: %s",
                    container.documentSelfLink);
            return;
        }
        AdapterRequest request = new AdapterRequest();
        request.resourceReference = UriUtils.buildPublicUri(getHost(), container.documentSelfLink);
        request.operationTypeId = ContainerOperationType.FETCH_LOGS.id;
        request.serviceTaskCallback = ServiceTaskCallback.createEmpty();
        request.customProperties = params; // additional request params like since, tail ...
        sendRequest(Operation.createPatch(getHost(), container.adapterManagementReference.toString())
                .setBody(request)
                .setContextId(Service.getId(getSelfLink()))
                .setCompletion((op, ex) -> {
                    if (ex != null) {
                        logWarning("Adapter request for container logs %s failed. Error: %s",
                                container.documentSelfLink, Utils.toString(ex));
                        return;
                    }
                }));
    }
}
