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

package com.vmware.admiral.adapter.kubernetes.service;

import java.util.HashMap;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.vmware.admiral.adapter.kubernetes.service.AbstractKubernetesAdapterService.KubernetesContext;
import com.vmware.admiral.compute.kubernetes.KubernetesHostConstants;
import com.vmware.photon.controller.model.resources.ComputeService.ComputeState;

public class ApiUtilTest {
    private static KubernetesContext context;

    @BeforeClass
    public static void setUp() {
        context = new KubernetesContext();
        context.host = new ComputeState();
        context.host.address = "test-address";
        context.host.customProperties = new HashMap<>();
        context.host.customProperties.put(
                KubernetesHostConstants.KUBERNETES_HOST_NAMESPACE_PROP_NAME,
                KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE);
    }

    @Test
    public void TestCorrectApiPrefix() {
        String prefix = ApiUtil.apiPrefix(context);
        Assert.assertEquals(context.host.address + KubernetesRemoteApiClient.apiPrefix, prefix);
    }

    @Test
    public void TestCorrectNamespacedPrefix() {
        String prefix = ApiUtil.namespacePrefix(context);
        Assert.assertEquals(context.host.address + KubernetesRemoteApiClient.apiPrefix
                + "/namespaces/" + KubernetesHostConstants.KUBERNETES_HOST_DEFAULT_NAMESPACE, prefix);
    }
}
