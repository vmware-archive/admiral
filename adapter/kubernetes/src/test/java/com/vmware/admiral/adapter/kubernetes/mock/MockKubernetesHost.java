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

package com.vmware.admiral.adapter.kubernetes.mock;

import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.DEPLOYMENT_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.POD_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICATION_CONTROLLER_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.REPLICA_SET_TYPE;
import static com.vmware.admiral.compute.content.kubernetes.KubernetesUtil.SERVICE_TYPE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.admiral.compute.kubernetes.entities.deployments.Deployment;
import com.vmware.admiral.compute.kubernetes.entities.services.Service;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.StatelessService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

public class MockKubernetesHost extends StatelessService {

    public List<BaseKubernetesObject> deployedElements;

    public Map<String, Object> deployedElementsMap;

    public Map<String, String> containerNamesToLogs;

    public Map<BaseKubernetesObject, BaseKubernetesObject> inspectMap;

    public boolean failIntentionally;

    public MockKubernetesHost() {
        super(ServiceDocument.class);
        deployedElements = Collections.synchronizedList(new ArrayList<>());
        deployedElementsMap = new ConcurrentHashMap<>();
        containerNamesToLogs = new ConcurrentHashMap<>();
        inspectMap = new ConcurrentHashMap<>();
    }

    @Override
    public void handleGet(Operation get) {
        String uri = get.getUri().toString();
        if (uri.contains("/log?container=")) {
            handleFetchLog(get);
        } else if (uri.contains("/pods?labelSelector=")) {
            getEntitiesMatchingLabel(get, POD_TYPE);
        } else if (uri.contains("/replicasets?labelSelector=")) {
            getEntitiesMatchingLabel(get, REPLICA_SET_TYPE);
        } else if (uri.contains("/deployments?labelSelector=")) {
            getEntitiesMatchingLabel(get, DEPLOYMENT_TYPE);
        } else if (uri.contains("/services?labelSelector=")) {
            getEntitiesMatchingLabel(get, SERVICE_TYPE);
        } else if (uri.contains("/replicationcontrollers?labelSelector=")) {
            getEntitiesMatchingLabel(get, REPLICATION_CONTROLLER_TYPE);
        } else {
            handleGetResource(get);
        }
    }

    @Override
    public void handlePost(Operation post) {
        String uri = post.getUri().toString();
        if (uri.endsWith("/services")) {
            Service service = post.getBody(Service.class);
            service.metadata.uid = UUID.randomUUID().toString();
            callbackRandomly(post, post.getBody(Service.class));
        } else if (uri.endsWith("/deployments")) {
            Deployment deployment = post.getBody(Deployment.class);
            deployment.metadata.uid = UUID.randomUUID().toString();
            callbackRandomly(post, post.getBody(Deployment.class));
        } else {
            post.fail(new IllegalArgumentException("Unknown uri " + uri));
        }
    }

    @Override
    public void handleDelete(Operation delete) {
        String uri = delete.getUri().toString();
        String[] splittedUri = uri.split("/");
        String componentName = splittedUri[splittedUri.length - 1];

        if (failIntentionally) {
            delete.fail(404);
        } else {
            deployedElementsMap.remove(componentName);
            delete.complete();
        }
    }

    private void callbackRandomly(Operation post, BaseKubernetesObject element) {
        String responseBody = Utils.toJson(element);

        if (Math.random() > 0.5) {
            deployedElements.add(element);
            deployedElementsMap.put(post.getBody(BaseKubernetesObject.class).metadata.name,
                    element);
            post.setBody(responseBody);
            post.complete();
        } else {
            getHost().schedule(() -> {
                deployedElements.add(element);
                deployedElementsMap.put(post.getBody(BaseKubernetesObject.class).metadata.name,
                        element);
                post.setBody(responseBody);
                post.complete();
            }, 20, TimeUnit.MILLISECONDS);
        }
    }

    private void handleFetchLog(Operation get) {
        String uri = get.getUri().toString();
        String containerName = uri.split("log\\?container=")[1];
        get.setBody(containerNamesToLogs.get(containerName));
        get.complete();
    }

    private void handleGetResource(Operation get) {
        String resourceName = extractName(get);
        for (Entry<BaseKubernetesObject, BaseKubernetesObject> entity : inspectMap.entrySet()) {
            if (entity.getKey().metadata.name.equals(resourceName)) {
                get.setBody(entity.getValue()).complete();
                return;
            }
        }
        get.fail(404);
    }

    private void getEntitiesMatchingLabel(Operation get, String objectType) {
        Map<String, String> queryParams = UriUtils.parseUriQueryParams(get.getUri());
        String labelSelectorKey = queryParams.get("labelSelector").split("=")[0];
        String labelSelectorValue = queryParams.get("labelSelector").split("=")[1];
        List<BaseKubernetesObject> items = deployedElements.stream()
                .filter(e -> e.kind.equals(objectType))
                .filter(e -> {
                    if (e.metadata.labels.containsKey(labelSelectorKey) && e.metadata.labels.get
                            (labelSelectorKey).equals(labelSelectorValue)) {
                        return true;
                    }
                    return false;
                }).collect(Collectors.toList());

        HashMap<String, Object> response = new HashMap<>();
        response.put("items", items);
        get.setBody(response).complete();
    }

    private String extractName(Operation op) {
        String[] elements = op.getUri().getPath().split("/");
        return elements[elements.length - 1];
    }
}