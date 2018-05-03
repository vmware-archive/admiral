/*
 * Copyright (c) 2017-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.kubernetes;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.adapter.kubernetes.service.AbstractKubernetesAdapterService.KubernetesContext;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.xenon.common.UriUtils;

public class ApiUtil {
    public static final String API_PREFIX_V1 = "/api/v1";
    public static final String API_PREFIX_EXTENSIONS_V1BETA = "/apis/extensions/v1beta1";
    public static final String NAMESPACES = "/namespaces/";

    public static final String API_PATH_SEGMENT_PROXY = "proxy";

    private static Map<String, String> entityTypeToPath = new HashMap<>();

    static {
        entityTypeToPath.put(KubernetesUtil.DEPLOYMENT_TYPE, "/deployments");
        entityTypeToPath.put(KubernetesUtil.SERVICE_TYPE, "/services");
        entityTypeToPath.put(KubernetesUtil.POD_TYPE, "/pods");
        entityTypeToPath.put(KubernetesUtil.REPLICATION_CONTROLLER_TYPE, "/replicationcontrollers");
        entityTypeToPath.put(KubernetesUtil.REPLICA_SET_TYPE, "/replicasets");
        entityTypeToPath.put(KubernetesUtil.NAMESPACE_TYPE, "/namespaces");
        entityTypeToPath.put(KubernetesUtil.NODE_TYPE, "/nodes");
    }

    public static String getKubernetesPath(String entityType) {
        return entityTypeToPath.get(entityType);
    }

    static String apiPrefix(KubernetesContext context, String apiVersion) {
        AssertUtil.assertNotNull(context.host, "context.host");
        AssertUtil.assertNotNullOrEmpty(context.host.address, "context.host.address");
        return context.host.address + apiVersion;
    }

    static String namespacePrefix(KubernetesContext context, String apiVersion) {
        assert (context.host != null);
        assert (context.host.customProperties != null);
        String namespace = context.host.customProperties.get(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME);
        assert (namespace != null && !namespace.isEmpty());
        return apiPrefix(context, apiVersion) + NAMESPACES + namespace;
    }

    public static URI buildKubernetesUri(KubernetesDescription description,
            KubernetesContext context) throws IOException {
        assertNotNull(context.host, "context.host");
        assertNotNullOrEmpty(context.host.address, "context.host.address");
        assertNotNull(description, "kubernetesDescription");
        assertNotNull(description.type, "description.type");

        if (!entityTypeToPath.containsKey(description.type)) {
            throw new IllegalArgumentException(
                    String.format("Kubernetes description with entity of type %s is not supported.",
                            description.type));
        }

        String uriString;

        if (KubernetesUtil.DEPLOYMENT_TYPE.equals(description.type)
                || KubernetesUtil.REPLICA_SET_TYPE.equals(description.type)) {
            uriString = namespacePrefix(context, API_PREFIX_EXTENSIONS_V1BETA);
        } else {
            uriString = namespacePrefix(context, API_PREFIX_V1);
        }

        uriString = uriString + entityTypeToPath.get(description.type);

        return UriUtils.buildUri(uriString);

    }

    public static String buildApiServerProxyUri(KubernetesContext context,
            String entityApiVersionPrefix, String entityNamespace, String entitySelflink,
            String proxiedPath) {
        String uriPath = UriUtils.buildUriPath(
                NAMESPACES,
                entityNamespace,
                entitySelflink,
                API_PATH_SEGMENT_PROXY,
                proxiedPath);

        return ApiUtil.apiPrefix(context, ApiUtil.API_PREFIX_V1) + uriPath;
    }

    public static URI buildKubernetesUri(String kubernetesSelfLink, KubernetesContext context) {
        return UriUtils.buildUri(context.host.address + kubernetesSelfLink);
    }
}
