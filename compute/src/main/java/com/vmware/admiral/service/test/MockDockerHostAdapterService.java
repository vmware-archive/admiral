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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.vmware.admiral.adapter.common.AdapterRequest;
import com.vmware.admiral.adapter.common.ContainerHostOperationType;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.admiral.common.util.QueryUtil;
import com.vmware.admiral.common.util.ServiceDocumentQuery;
import com.vmware.admiral.compute.ContainerHostService;
import com.vmware.admiral.compute.ContainerHostService.ContainerHostType;
import com.vmware.admiral.compute.container.ContainerService.ContainerState;
import com.vmware.admiral.compute.container.HostContainerListDataCollection.ContainerListCallback;
import com.vmware.admiral.compute.container.HostNetworkListDataCollection.NetworkListCallback;
import com.vmware.admiral.compute.container.HostVolumeListDataCollection.VolumeListCallback;
import com.vmware.admiral.compute.container.network.ContainerNetworkService.ContainerNetworkState;
import com.vmware.admiral.compute.container.volume.ContainerVolumeService.ContainerVolumeState;
import com.vmware.admiral.service.test.MockDockerContainerToHostService.MockDockerContainerToHostState;
import com.vmware.admiral.service.test.MockDockerNetworkToHostService.MockDockerNetworkToHostState;
import com.vmware.admiral.service.test.MockDockerVolumeToHostService.MockDockerVolumeToHostState;
import com.vmware.photon.controller.model.resources.ComputeService;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;
import com.vmware.xenon.services.common.QueryTask;

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

            queryContainersByHost(request, (containerStates) -> {
                for (ContainerState containerState : containerStates) {
                    callbackResponse.containerIdsAndNames.put(containerState.id,
                            containerState.names.get(0));
                    callbackResponse.containerIdsAndImage.put(containerState.id,
                            containerState.image);
                    callbackResponse.containerIdsAndState.put(containerState.id,
                            containerState.powerState);
                }
                patchTaskStage(request, null, callbackResponse);
                op.setBody(callbackResponse);
                op.complete();
            });

        } else if (ContainerHostOperationType.LIST_NETWORKS.id.equals(request.operationTypeId)) {
            NetworkListCallback callbackResponse = new NetworkListCallback();
            callbackResponse.containerHostLink = request.resourceReference.getPath();

            queryNetworksByHost(request, (networkStates) -> {
                for (ContainerNetworkState networkState : networkStates) {
                    callbackResponse.addIdAndNames(networkState.id, networkState.name);
                }
                patchTaskStage(request, null, callbackResponse);
                op.setBody(callbackResponse);
                op.complete();
            });

        } else if (ContainerHostOperationType.LIST_VOLUMES.id.equals(request.operationTypeId)) {
            VolumeListCallback callbackResponse = new VolumeListCallback();
            callbackResponse.containerHostLink = request.resourceReference.getPath();
            queryVolumesByHost(request, (volumeStates) -> {
                for (ContainerVolumeState volumeState : volumeStates) {
                    callbackResponse.add(volumeState);
                }
                patchTaskStage(request, null, callbackResponse);
                op.setBody(callbackResponse);
                op.complete();
            });

        } else if (ContainerHostOperationType.INFO.id.equals(request.operationTypeId)) {
            sendRequest(Operation
                    .createGet(request.resourceReference)
                    .setCompletion(
                            (o, e) -> {
                                ComputeState cs = o.getBody(ComputeState.class);
                                getHostInfoResponse(request, properties -> {
                                    patchHostState(request, cs, properties);
                                    op.setBody(cs);
                                    op.complete();
                                });
                            }));
        } else {
            op.setStatusCode(Operation.STATUS_CODE_ACCEPTED).complete();
        }
    }

    private void getHostInfoResponse(AdapterRequest mockRequest, Consumer<Map<String, Object>> callback) {
        Map<String, Object> properties = new HashMap<>();

        if (mockRequest.customProperties != null) {
            String hostType = mockRequest.customProperties.get(CONTAINER_HOST_TYPE_PROP_NAME);
            if (hostType != null && hostType.equals(ContainerHostType.VCH.toString())) {
                properties.put(DOCKER_INFO_STORAGE_DRIVER_PROP_NAME, VIC_STORAGE_DRIVER_PROP_VALUE);
            }
        }

        queryContainersByHost(mockRequest, (containerStates) -> {
            properties.put(ContainerHostService.NUMBER_OF_CONTAINERS_PER_HOST_PROP_NAME, containerStates.size());
            callback.accept(properties);
        });
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

    private void queryNetworksByHost(AdapterRequest state,
            Consumer<List<ContainerNetworkState>> callback) {
        String hostLink = state.resourceReference.getPath();
        QueryTask q = QueryUtil.buildPropertyQuery(MockDockerNetworkToHostState.class,
                MockDockerNetworkToHostState.FIELD_NAME_HOST_LINK, hostLink);
        QueryUtil.addExpandOption(q);

        List<ContainerNetworkState> networkStates = new ArrayList< >();
        new ServiceDocumentQuery<>(getHost(), MockDockerNetworkToHostState.class).query(q,
                (r) -> {
                    if (r.hasException()) {
                        patchTaskStage(state, r.getException());
                    } else if (r.hasResult()) {
                        networkStates.add(buildContainerNetwork(r.getResult()));
                    } else {
                        callback.accept(networkStates);
                    }
                });
    }

    private void queryContainersByHost(AdapterRequest state, Consumer<List<ContainerState>> callback) {
        String parentLink = state.resourceReference.getPath();
        QueryTask q = QueryUtil.buildPropertyQuery(MockDockerContainerToHostState.class,
                MockDockerContainerToHostState.FIELD_NAME_PARENT_LINK, parentLink);
        QueryUtil.addExpandOption(q);

        List<ContainerState> containerStates = new ArrayList<>();
        new ServiceDocumentQuery<>(getHost(), MockDockerContainerToHostState.class).query(q,
                (r) -> {
                    if (r.hasException()) {
                        patchTaskStage(state, r.getException());
                    } else if (r.hasResult()) {
                        containerStates.add(buildContainer(r.getResult()));
                    } else {
                        callback.accept(containerStates);
                    }
                });
    }

    private ContainerNetworkState buildContainerNetwork(
            MockDockerNetworkToHostState networkToHostState) {
        ContainerNetworkState networkState = new ContainerNetworkState();
        networkState.originatingHostLink = networkToHostState.hostLink;
        networkState.id = networkToHostState.id;
        networkState.name = networkToHostState.name;
        return networkState;
    }

    private ContainerState buildContainer(MockDockerContainerToHostState containerToHostState) {
        ContainerState containerState = new ContainerState();
        containerState.parentLink = containerToHostState.parentLink;
        containerState.id = containerToHostState.id;
        containerState.names = Arrays.asList(containerToHostState.name);
        containerState.image = containerToHostState.image;
        containerState.powerState = containerToHostState.powerState;
        return containerState;
    }

    private void queryVolumesByHost(AdapterRequest state, Consumer<List<ContainerVolumeState>> callback) {
        String hostLink = state.resourceReference.getPath();
        QueryTask q = QueryUtil.buildPropertyQuery(MockDockerVolumeToHostState.class,
                MockDockerVolumeToHostState.FIELD_NAME_HOST_LINK, hostLink);
        QueryUtil.addExpandOption(q);

        List<ContainerVolumeState> volumeStates = new ArrayList< >();
        new ServiceDocumentQuery<>(getHost(),
                MockDockerVolumeToHostState.class).query(q,
                        (r) -> {
                            if (r.hasException()) {
                                patchTaskStage(state, r.getException());
                            } else if (r.hasResult()) {
                                volumeStates.add(buildContainerVolume(r.getResult()));
                            } else {
                                callback.accept(volumeStates);
                            }
                        });
    }

    private ContainerVolumeState buildContainerVolume(MockDockerVolumeToHostState volumeToHostState) {
        ContainerVolumeState volumeState = new ContainerVolumeState();
        volumeState.name = volumeToHostState.name;
        volumeState.originatingHostLink = volumeToHostState.hostLink;
        volumeState.driver = volumeToHostState.driver;
        volumeState.scope = volumeToHostState.scope;
        return volumeState;
    }
}
