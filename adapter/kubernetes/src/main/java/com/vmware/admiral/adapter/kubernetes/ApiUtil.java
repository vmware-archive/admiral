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

import java.net.URI;

import com.vmware.admiral.adapter.kubernetes.service.AbstractKubernetesAdapterService.KubernetesContext;
import com.vmware.admiral.common.util.AssertUtil;
import com.vmware.admiral.common.util.PropertyUtils;
import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.admiral.compute.kubernetes.service.KubernetesDescriptionService.KubernetesDescription;
import com.vmware.xenon.common.UriUtils;

public class ApiUtil {
    public static final String API_VERSION_V1 = "v1";
    public static final String API_VERSION_EXTENSIONS_V1BETA = "extensions/v1beta1";

    /**
     * Used for calls to the core/legacy API group. Check
     * https://kubernetes.io/docs/concepts/overview/kubernetes-api/#api-groups
     */
    public static final String API_PREFIX_LEGACY = "api";

    /**
     * Used for calls to non-core/non-legacy API groups. Check
     * https://kubernetes.io/docs/concepts/overview/kubernetes-api/#api-groups
     */
    public static final String API_PREFIX = "apis";

    public static final String API_PREFIX_V1 = UriUtils.buildUriPath(
            API_PREFIX_LEGACY,
            API_VERSION_V1);
    public static final String API_PREFIX_EXTENSIONS_V1BETA = UriUtils.buildUriPath(
            API_PREFIX,
            API_VERSION_EXTENSIONS_V1BETA);

    public static final String NAMESPACES = "namespaces";

    public static final String API_PATH_SEGMENT_PROXY = "proxy";

    public static String getKubernetesPath(String entityType) {
        return KubernetesApiEndpointsUtil.getEntityEndpoint(entityType);
    }

    static String apiPrefix(KubernetesContext context, String apiVersion) {
        AssertUtil.assertNotNull(context.host, "context.host");
        AssertUtil.assertNotNullOrEmpty(context.host.address, "context.host.address");
        return context.host.address + apiVersion;
    }

    static String namespacePrefix(KubernetesContext context, String apiVersionPrefix) {
        return namespacePrefix(context, (KubernetesDescription) null, apiVersionPrefix);
    }

    static String namespacePrefix(KubernetesContext context, KubernetesDescription description,
            String apiVersionPrefix) {
        // If there is a namespace in the description, use it
        if (description != null && description.namespace != null
                && !description.namespace.isEmpty()) {
            return namespacePrefix(context, description.namespace, apiVersionPrefix);
        }

        // Otherwise, use the namespace stored in the k8s context
        // or default to the default namespace
        String namespace = KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE;
        if (context.host != null && context.host.customProperties != null) {
            namespace = PropertyUtils
                    .getPropertyString(context.host.customProperties,
                            KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME)
                    .orElse(namespace);
        }

        return namespacePrefix(context, namespace, apiVersionPrefix);
    }

    static String namespacePrefix(KubernetesContext context, String namespace,
            String apiVersionPrefix) {
        return apiPrefix(context, apiVersionPrefix) + UriUtils.buildUriPath(NAMESPACES, namespace);
    }

    public static URI buildKubernetesFactoryUri(KubernetesDescription description,
            KubernetesContext context) {
        assertNotNull(context.host, "context.host");
        assertNotNullOrEmpty(context.host.address, "context.host.address");
        assertNotNull(description, "kubernetesDescription");
        assertNotNull(description.type, "description.type");
        assertNotNull(description.apiVersion, "description.apiVersion");

        String entityPath = getKubernetesPath(description.type);
        if (entityPath == null) {
            throw new IllegalArgumentException(
                    String.format("Kubernetes description with entity of type %s is not supported.",
                            description.type));
        }

        String versionPrefix = getApiVersionPrefix(description.apiVersion);

        String uriString;
        if (KubernetesUtil.NAMESPACE_TYPE.equals(description.type)) {
            uriString = apiPrefix(context, versionPrefix) + entityPath;
        } else {
            uriString = namespacePrefix(context, description, versionPrefix) + entityPath;
        }

        return UriUtils.buildUri(uriString);
    }

    /**
     * API calls to legacy API groups use the legacy API prefix "api". Calls to the other versioned
     * APIs use the new prefix "apis".
     *
     * @see https://kubernetes.io/docs/concepts/overview/kubernetes-api/#api-groups
     */
    public static String getApiVersionPrefix(String apiVersion) {
        if (apiVersion == null) {
            return null;
        }

        if (isCoreGroupVersion(apiVersion)) {
            return UriUtils.buildUriPath(API_PREFIX_LEGACY, apiVersion);
        }

        return UriUtils.buildUriPath(API_PREFIX, apiVersion);
    }

    /**
     * Checks if this apiVersion string denotes a core (legacy) group. The only legacy group is
     * "v1".
     *
     * @see https://kubernetes.io/docs/concepts/overview/kubernetes-api/#api-groups
     */
    public static boolean isCoreGroupVersion(String apiVersion) {
        return API_VERSION_V1.equals(apiVersion);
    }

    public static String buildApiServerProxyUri(KubernetesContext context,
            String entityApiVersionPrefix, String entityNamespace, String entitySelflink,
            String proxiedPath) {

        String uriPath = UriUtils.buildUriPath(
                entitySelflink,
                API_PATH_SEGMENT_PROXY,
                proxiedPath);

        return namespacePrefix(context, entityNamespace, entityApiVersionPrefix) + uriPath;
    }

    public static URI buildKubernetesUri(String kubernetesSelfLink, KubernetesContext context) {
        return UriUtils.buildUri(context.host.address + kubernetesSelfLink);
    }
}
