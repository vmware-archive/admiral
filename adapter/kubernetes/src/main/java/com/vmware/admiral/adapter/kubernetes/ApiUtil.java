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

package com.vmware.admiral.adapter.kubernetes;

import static com.vmware.admiral.common.util.AssertUtil.assertNotNull;
import static com.vmware.admiral.common.util.AssertUtil.assertNotNullOrEmpty;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import com.vmware.admiral.adapter.kubernetes.service.AbstractKubernetesAdapterService.KubernetesContext;
import com.vmware.admiral.compute.content.kubernetes.CommonKubernetesEntity;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.compute.kubernetes.KubernetesService.KubernetesState;
import com.vmware.xenon.common.UriUtils;

public class ApiUtil {
    public static final String API_PREFIX_V1 = "/api/v1";
    public static final String API_PREFIX_EXTENSIONS_V1BETA = "/apis/extensions/v1beta1";
    public static final String NAMESPACES = "/namespaces/";

    private static Map<String, String> entityTypeToPath = new HashMap<>();

    static {
        entityTypeToPath.put(KubernetesUtil.DEPLOYMENT, "/deployments/");
        entityTypeToPath.put(KubernetesUtil.SERVICE, "/services/");
        entityTypeToPath.put(KubernetesUtil.POD, "/pods/");
        entityTypeToPath.put(KubernetesUtil.SERVICE, "/replicationcontrollers/");
    }

    static String apiPrefix(KubernetesContext context, String apiVersion) {
        assert (context.host != null);
        assert (context.host.customProperties != null);
        String namespace = context.host.customProperties.get(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME);
        assert (namespace != null && !namespace.isEmpty());
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

    public static URI buildKubernetesUri(KubernetesDescription description, KubernetesContext
            context) throws IOException {
        assertNotNull(context.host, "context.host");
        assertNotNullOrEmpty(context.host.address, "context.host.address");
        assertNotNull(description, "kubernetesDescription");
        assertNotNull(description.type, "description.type");

        if (!entityTypeToPath.containsKey(description.type)) {
            throw new IllegalArgumentException(
                    String.format("Kubernetes description with entity of type %s is not supported.",
                            description.type));
        }

        CommonKubernetesEntity entity = description.getKubernetesEntity(CommonKubernetesEntity
                .class);

        String uriString = context.host.address;

        if (KubernetesUtil.DEPLOYMENT.equals(description.type)) {
            uriString = uriString + API_PREFIX_EXTENSIONS_V1BETA;
        } else {
            uriString = uriString + API_PREFIX_V1;
        }

        uriString = uriString + NAMESPACES + entity.metadata.namespace +
                entityTypeToPath.get(description.type) + description.name;

        return UriUtils.buildUri(uriString);

    }

    public static URI buildKubernetesUri(KubernetesState state, KubernetesContext context) {
        return UriUtils.buildUri(context.host.address + state.selfLink);
    }
}
