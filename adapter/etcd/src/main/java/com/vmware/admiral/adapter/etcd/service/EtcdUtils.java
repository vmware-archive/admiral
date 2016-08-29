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

package com.vmware.admiral.adapter.etcd.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.vmware.admiral.adapter.etcd.service.EtcdEmulatorService.EtcdNode;
import com.vmware.admiral.adapter.etcd.service.EtcdEmulatorService.EtcdNodeError;
import com.vmware.admiral.adapter.etcd.service.EtcdEmulatorService.EtcdNodeResult;
import com.vmware.admiral.adapter.etcd.service.KVStoreService.KVNode;
import com.vmware.admiral.common.ManagementUriParts;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Utils;

public class EtcdUtils {

    public static void processResultAndComplete(Operation op, KVNode node, KVNode prevNode) {
        try {
            EtcdNodeResult result = new EtcdNodeResult();
            result.action = getAction(op);

            if (node != null) {
                // CRU
                result.node = toEtcdNode(node);
            } else {
                // D
                result.node = toEtcdNode(prevNode);
                result.node.value = null;
            }

            result.prevNode = toEtcdNode(prevNode);
            op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
            op.setBodyNoCloning(result);
            op.complete();
        } catch (Throwable e) {
            op.fail(e);
        }
    }

    public static void processResultAndComplete(Operation op, Map<String, Object> nodes) {
        final String key = EtcdUtils.getEtcdKey(op);

        if (nodes == null) {
            processNoResults(op, key);
            return;
        }
        try {

            ArrayList<EtcdNode> etcdNodes = new ArrayList<>();
            for (Entry<String, Object> entry : nodes.entrySet()) {
                KVNode kvNode = Utils.fromJson(entry.getValue(), KVNode.class);
                if (kvNode.key != null) {
                    etcdNodes.add(EtcdUtils.toEtcdNode(kvNode));
                }
            }

            EtcdNodeResult result = new EtcdNodeResult();
            result.action = getAction(op);

            final String searchKey = KVStoreFactoryService.SELF_LINK + key;
            if ((etcdNodes.size() == 1) && (etcdNodes.get(0).key.equals(searchKey))) {
                result.node = etcdNodes.get(0);
            } else {
                EtcdNode node = new EtcdNode();
                result.node = node;
                if ((key != null) && (!key.isEmpty())) {
                    node.key = key;
                }
                node.dir = true;
                node.nodes = etcdNodes;
            }

            op.setBodyNoCloning(result);

            op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
            op.complete();
        } catch (Throwable e) {
            op.fail(e);
        }
    }

    public static void processNoResults(Operation op, String key) {
        EtcdNodeError error = newErrorKeyNotFound(key);
        op.setStatusCode(Operation.STATUS_CODE_NOT_FOUND);
        op.setBodyNoCloning(error);
        op.setContentType(Operation.MEDIA_TYPE_APPLICATION_JSON);
        op.complete();
    }

    private static String getAction(Operation op) {
        switch (op.getAction()) {
        case GET:
            return "get";
        case PUT:
        case POST:
            return "set";
        case DELETE:
            return "delete";
        default:
            throw new RuntimeException("Unknown action type: " + op.getAction());
        }
    }

    public static Map<String, String> getBodyParameters(Operation op) {
        String body = null;

        Object bodyRaw = op.getBodyRaw();
        if ((bodyRaw != null) && (bodyRaw instanceof String)) {
            body = (String) bodyRaw;
        }

        Map<String, String> parameters;
        if ((body != null) && (!body.isEmpty())) {
            parameters = Arrays.stream(body.split("&"))
                    .map(p -> p.split("=", 2))
                    .collect(Collectors.toMap(e -> e[0], e -> e[1]));
        } else {
            parameters = Collections.emptyMap();
        }

        return parameters;
    }

    public static KVNode getKVNode(Operation op) {
        KVNode node = new KVNode();

        if (op.getContentType().equals(Operation.MEDIA_TYPE_APPLICATION_JSON)) {
            KVNode body = op.getBody(KVNode.class);
            node.key = body.key;
            node.documentSelfLink = node.key;
            node.value = body.value;
            return node;
        }

        node.key = getEtcdKey(op);
        node.documentSelfLink = node.key;

        Map<String, String> parameters = getBodyParameters(op);
        node.value = parameters.get("value");

        return node;
    }

    public static String getEtcdKey(Operation op) {
        return getEtcdKey(op.getUri().getPath());
    }

    public static String getEtcdKey(String key) {
        if ((key != null) && key.startsWith(ManagementUriParts.ADAPTER_ETCD_KV)) {
            key = key.replaceFirst(ManagementUriParts.ADAPTER_ETCD_KV, "");
        }

        if (key != null && key.endsWith("/")) {
            key = key.substring(0, key.length() - 1);
        }

        return key;
    }

    public static EtcdNode toEtcdNode(KVNode in) {
        if (in == null) {
            return null;
        }
        EtcdNode out = new EtcdNode();
        out.key = getEtcdKey(in.key);
        out.value = in.value;
        return out;
    }

    public static EtcdNodeError newErrorKeyNotFound(String key) {
        EtcdNodeError error = new EtcdNodeError();
        error.errorCode = 100;
        error.message = "Key not found";
        error.cause = key;
        return error;
    }

    public static EtcdNodeError newErrorNotAFile(String key) {
        EtcdNodeError error = new EtcdNodeError();
        error.errorCode = 102;
        error.message = "Not a file";
        error.cause = key;
        return error;
    }

    public static EtcdNodeError newErrorRootReadOnly() {
        EtcdNodeError error = new EtcdNodeError();
        error.errorCode = 107;
        error.message = "Root is read only";
        error.cause = "/";
        return error;
    }

}
