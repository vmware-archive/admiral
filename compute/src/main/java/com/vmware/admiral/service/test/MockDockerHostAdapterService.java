/*
 * Copyright (c) 2016-2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.service.test;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.NetworkListCallback;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.VolumeListCallback;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.Utils;

public class MockDockerHostAdapterService extends BaseMockAdapterService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_HOST;

    /**
     * The real type of the host (not the one that was specified by the user)
     */
    public static final String CONTAINER_HOST_TYPE_PROP_NAME = "__mockContainerHostType";
    public static final String DOCKER_INFO_STORAGE_DRIVER_PROP_NAME = "__Driver";
    public static final String VIC_STORAGE_DRIVER_PROP_VALUE = "vSphere Integrated Containers "
            + "v0.8.0-7540-aaae251 Backend Engine";

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

        } else if (ContainerHostOperationType.LIST_CONTAINERS.id.equals(request.operationTypeId)) {
            ContainerListCallback callbackResponse = new ContainerListCallback();
            callbackResponse.containerHostLink = request.resourceReference.getPath();
            String hostId = Service.getId(request.resourceReference.getPath());
            callbackResponse.containerIdsAndNames = new HashMap<>();
            for (String containerId : MockDockerAdapterService.getContainerIds(hostId)) {
                callbackResponse.containerIdsAndNames.put(containerId,
                        MockDockerAdapterService.getContainerNames(containerId));
                callbackResponse.containerIdsAndImage.put(containerId,
                        MockDockerAdapterService.getContainerImage(containerId));
            }
            patchTaskStage(request, null, callbackResponse);
            op.setBody(callbackResponse);
            op.complete();

        } else if (ContainerHostOperationType.LIST_NETWORKS.id.equals(request.operationTypeId)) {
            NetworkListCallback callbackResponse = new NetworkListCallback();
            callbackResponse.containerHostLink = request.resourceReference.getPath();
            String hostId = Service.getId(request.resourceReference.getPath());
            callbackResponse.networkIdsAndNames = new HashMap<>();
            for (String networkId : MockDockerNetworkAdapterService.getNetworkIdsByHost(hostId)) {
                callbackResponse.networkIdsAndNames.put(networkId,
                        MockDockerNetworkAdapterService.getNetworkNameById(networkId));
            }
            patchTaskStage(request, null, callbackResponse);
            op.setBody(callbackResponse);
            op.complete();
        } else if (ContainerHostOperationType.LIST_VOLUMES.id.equals(request.operationTypeId)) {
            VolumeListCallback callbackResponse = new VolumeListCallback();
            callbackResponse.containerHostLink = request.resourceReference.getPath();
            String hostId = Service.getId(request.resourceReference.getPath());
            MockDockerVolumeAdapterService.getVolumesByHost(hostId)
                    .forEach(volume -> callbackResponse.add(volume));
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
        properties.put(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME,
                MockDockerAdapterService.getNumberOfContainers());

        if (mockRequest != null && mockRequest.customProperties != null) {
            String hostType = mockRequest.customProperties.get(CONTAINER_HOST_TYPE_PROP_NAME);
            if (hostType != null && hostType.equals(ContainerHostType.VCH.toString())) {
                properties.put(DOCKER_INFO_STORAGE_DRIVER_PROP_NAME, VIC_STORAGE_DRIVER_PROP_VALUE);
            }
        }

        return properties;
    }

    private void patchHostState(AdapterRequest request,
            ComputeState computeState, Map<String, Object> properties) {

        if (properties != null && !properties.isEmpty()) {
            if (computeState.customProperties == null) {
                computeState.customProperties = new HashMap<>();
            }

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                computeState.customProperties.put(entry.getKey(), Utils.toJson(entry.getValue()));
            }
        }

        sendRequest(Operation
                .createPatch(request.resourceReference)
                .setBody(computeState)
                .setCompletion((o, ex) -> patchTaskStage(request, ex)));
    }
}
