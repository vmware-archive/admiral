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

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.NetworkListCallback;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.VolumeListCallback;
import com.vmware.admiral.service.common.ServiceTaskCallback.ServiceTaskCallbackResponse;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class MockDockerHostAdapterService extends StatelessService {
    public static final String SELF_LINK = ManagementUriParts.ADAPTER_DOCKER_HOST;

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

        if (ContainerHostOperationType.PING.id == request.operationTypeId
                && ComputeService.FACTORY_LINK.equals(request.resourceReference.getPath())) {
            op.complete();

        } else if (ContainerHostOperationType.LIST_CONTAINERS.id == request.operationTypeId) {
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

        } else if (ContainerHostOperationType.LIST_NETWORKS.id == request.operationTypeId) {
            NetworkListCallback callbackResponse = new NetworkListCallback();
            callbackResponse.containerHostLink = request.resourceReference.getPath();
            String hostId = Service.getId(request.resourceReference.getPath());
            callbackResponse.networkIdsAndNames = new HashMap<>();
            for (String networkId : MockDockerNetworkAdapterService.getNetworkIds(hostId)) {
                callbackResponse.networkIdsAndNames.put(networkId,
                        MockDockerNetworkAdapterService.getNetworkNames(networkId));
            }
            patchTaskStage(request, null, callbackResponse);
            op.setBody(callbackResponse);
            op.complete();
        } else if (ContainerHostOperationType.LIST_VOLUMES.id == request.operationTypeId) {
            VolumeListCallback callbackResponse = new VolumeListCallback();
            callbackResponse.containerHostLink = request.resourceReference.getPath();
            String hostId = Service.getId(request.resourceReference.getPath());
            callbackResponse.volumeNames = new ArrayList<>();
            for (String name: MockDockerVolumeAdapterService.getVolumeNames(hostId)) {
                callbackResponse.addName(name);
            }
            patchTaskStage(request, null, callbackResponse);
            op.setBody(callbackResponse);
            op.complete();
        } else {
            op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();

            sendRequest(Operation
                    .createGet(request.resourceReference)
                    .setCompletion(
                            (o, e) -> {
                                ComputeState cs = o.getBody(ComputeState.class);
                                Map<String, Object> properties = null;
                                if (ContainerHostOperationType.INFO.id == request.operationTypeId) {
                                    properties = new HashMap<>();
                                    properties
                                            .put(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME,
                                                    MockDockerAdapterService
                                                            .getNumberOfContainers());
                                }
                                patchHostState(request, cs, properties);
                            }));

        }
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

    private void patchTaskStage(AdapterRequest state, Throwable exception) {

        patchTaskStage(state,
                exception == null ? null : Utils.toServiceErrorResponse(exception),
                null);
    }

    private void patchTaskStage(AdapterRequest state, ServiceErrorResponse errorResponse,
            ServiceTaskCallbackResponse callbackResponse) {

        if (state.serviceTaskCallback.isEmpty()) {
            return;
        }

        if (errorResponse != null) {
            callbackResponse = state.serviceTaskCallback.getFailedResponse(errorResponse);
        } else if (callbackResponse == null) {
            callbackResponse = state.serviceTaskCallback.getFinishedResponse();
        }

        URI callbackReference = URI.create(state.serviceTaskCallback.serviceSelfLink);
        if (callbackReference.getScheme() == null) {
            callbackReference = UriUtils.buildUri(getHost(),
                    state.serviceTaskCallback.serviceSelfLink);
        }

        // tell the parent we are done. We are a mock service, so we get things done, fast.
        sendRequest(Operation
                .createPatch(callbackReference)
                .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_QUEUE_FOR_SERVICE_AVAILABILITY)
                .setBody(callbackResponse)
                .setCompletion(
                        (o, e) -> {
                            if (e != null) {
                                logWarning(
                                        "Notifying parent task %s from mock docker host adapter failed: %s",
                                        o.getUri(), Utils.toString(e));
                            }
                        }));
    }

}
