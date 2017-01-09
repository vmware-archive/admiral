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

package com.vmware.admiral.adapter.kubernetes.service;

import static com.vmware.admiral.adapter.kubernetes.service.KubernetesRemoteApiClient.apiPrefix;

import com.vmware.admiral.adapter.kubernetes.service.AbstractKubernetesAdapterService.KubernetesContext;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;

class ApiUtil {
    static String apiPrefix(KubernetesContext context) {
        assert (context.host != null);
        assert (context.host.customProperties != null);
        String namespace = context.host.customProperties.get(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME);
        assert (namespace != null && !namespace.isEmpty());
        return context.host.address + apiPrefix;
    }

    static String namespacePrefix(KubernetesContext context) {
        assert (context.host != null);
        assert (context.host.customProperties != null);
        String namespace = context.host.customProperties.get(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME);
        assert (namespace != null && !namespace.isEmpty());
        return apiPrefix(context) + "/namespaces/" + namespace;
    }
}
