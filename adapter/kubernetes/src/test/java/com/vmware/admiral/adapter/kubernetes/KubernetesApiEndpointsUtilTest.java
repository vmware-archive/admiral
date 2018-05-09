/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.adapter.kubernetes;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.vmware.admiral.compute.content.kubernetes.KubernetesUtil;

public class KubernetesApiEndpointsUtilTest {

    @Test
    public void testGetEntityEndpoint() {
        // these endpoints were available before generalizing
        // the endpoint generation out of the entity kind
        assertEquals("/deployments",
                KubernetesApiEndpointsUtil.getEntityEndpoint(KubernetesUtil.DEPLOYMENT_TYPE));
        assertEquals("/services",
                KubernetesApiEndpointsUtil.getEntityEndpoint(KubernetesUtil.SERVICE_TYPE));
        assertEquals("/pods",
                KubernetesApiEndpointsUtil.getEntityEndpoint(KubernetesUtil.POD_TYPE));
        assertEquals("/replicationcontrollers",
                KubernetesApiEndpointsUtil
                        .getEntityEndpoint(KubernetesUtil.REPLICATION_CONTROLLER_TYPE));
        assertEquals("/replicasets",
                KubernetesApiEndpointsUtil.getEntityEndpoint(KubernetesUtil.REPLICA_SET_TYPE));
        assertEquals("/namespaces",
                KubernetesApiEndpointsUtil.getEntityEndpoint(KubernetesUtil.NAMESPACE_TYPE));
        assertEquals("/nodes",
                KubernetesApiEndpointsUtil.getEntityEndpoint(KubernetesUtil.NODE_TYPE));

        // test some additional endpoints. see
        // https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.10
        assertEquals("/cronjobs",
                KubernetesApiEndpointsUtil.getEntityEndpoint("CronJob"));
        assertEquals("/ingresses",
                KubernetesApiEndpointsUtil.getEntityEndpoint("Ingress"));
        assertEquals("/podsecuritypolicies",
                KubernetesApiEndpointsUtil.getEntityEndpoint("PodSecurityPolicy"));
        assertEquals("/configmaps",
                KubernetesApiEndpointsUtil.getEntityEndpoint("ConfigMap"));
        assertEquals("/secrets",
                KubernetesApiEndpointsUtil.getEntityEndpoint("Secret"));
        assertEquals("/persistentvolumeclaims",
                KubernetesApiEndpointsUtil.getEntityEndpoint("PersistentVolumeClaim"));

        // test exclusions
        assertEquals("/endpoints",
                KubernetesApiEndpointsUtil.getEntityEndpoint(KubernetesUtil.ENDPOINTS_TYPE));

    }
}
