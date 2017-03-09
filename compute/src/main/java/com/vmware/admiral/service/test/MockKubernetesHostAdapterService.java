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

package com.vmware.admiral.service.test;

import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.createEntityData;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.kubernetes.KubernetesEntityDataCollection.EntityListCallback;
import com.vmware.admiral.compute.kubernetes.service.BaseKubernetesState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

public class MockKubernetesHostAdapterService extends BaseMockAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_KUBERNETES_HOST;

    @Override
    public void handleRequest(Operation op) {
        if (op.getAction() == Action.DELETE) {
            if (op.hasBody()) {
                op.complete();
                return;
            } else {
                handleDeleteCompletion(op);
                return;
            }
        }

        if (op.getAction() == Action.GET) {
            op.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
            op.complete();
            return;
        }

        if (op.getAction() != Action.PATCH) {
            op.fail(new IllegalArgumentException("action not supported"));
            return;
        }

        AdapterRequest request = op.getBody(AdapterRequest.class);
        request.validate();

        if (ContainerHostOperationType.PING.id.equals(request.operationTypeId)
                && ComputeService.FACTORY_LINK.equals(request.resourceReference.getPath())) {
            op.complete();

        } else if (ContainerHostOperationType.LIST_ENTITIES.id.equals(request.operationTypeId)) {
            EntityListCallback callbackResponse = new EntityListCallback();
            callbackResponse.computeHostLink = request.resourceReference.getPath();
            // String hostId = Service.getId(request.resourceReference.getPath());
            callbackResponse.idToEntityData = new HashMap<>();
            for (BaseKubernetesState entity : MockKubernetesAdapterService
                    .getKubernetesEntities()) {
                callbackResponse.idToEntityData
                        .put(entity.id, createEntityData(entity.getEntityAsBaseKubernetesObject(),
                                entity.getType()));
            }
            patchTaskStage(request, null, callbackResponse);
            op.setBody(callbackResponse);
            op.complete();

        } else if (ContainerHostOperationType.INFO.id.equals(request.operationTypeId)) {
            sendRequest(Operation
                    .createGet(request.resourceReference)
                    .setCompletion(
                            (o, e) -> {
                                ComputeState cs = o.getBody(ComputeState.class);
                                Map<String, Object> properties = getHostInfoResponse(request);
                                patchHostState(request, cs, properties);
                                op.setBody(cs);
                                op.complete();
                            }));
        } else {
            op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();
        }
    }

    private Map<String, Object> getHostInfoResponse(AdapterRequest mockRequest) {
        Map<String, Object> properties = new HashMap<>();
        /*
        properties.put(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME,
                MockDockerAdapterService.getNumberOfContainers());

        if (mockRequest != null && mockRequest.customProperties != null) {
            String hostType = mockRequest.customProperties.get(CONTAINER_HOST_TYPE_PROP_NAME);
            if (hostType != null && hostType.equals(ContainerHostType.VCH.toString())) {
                properties.put(DOCKER_INFO_STORAGE_DRIVER_PROP_NAME, VIC_STORAGE_DRIVER_PROP_VALUE);
            }
        }
*/
        return properties;
    }

    private void patchHostState(AdapterRequest request,
            ComputeState computeState, Map<String, Object> properties) {

        if (properties != null && !properties.isEmpty()) {
            if (computeState.customProperties == null) {
                computeState.customProperties = new HashMap<String, String>();
            }

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                computeState.customProperties.put(entry.getKey(), Utils.toJson(entry.getValue()));
            }
        }

        sendRequest(Operation
                .createPatch(request.resourceReference)
                .setBody(computeState).setCompletion((o, ex) -> {
                    patchTaskStage(request, ex);
                }));
    }
}
