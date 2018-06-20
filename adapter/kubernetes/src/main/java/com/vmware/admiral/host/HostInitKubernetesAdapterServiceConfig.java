/*
 * Copyright (c) 2016-2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.host;

import com.vmware.admiral.adapter.kubernetes.service.KubernetesAdapterService;
import com.vmware.admiral.adapter.kubernetes.service.KubernetesApplicationAdapterService;
import com.vmware.admiral.adapter.kubernetes.service.KubernetesHostAdapterService;
import com.vmware.admiral.adapter.pks.service.KubeConfigContentService;
import com.vmware.admiral.adapter.pks.service.PKSAdapterService;
import com.vmware.admiral.adapter.pks.service.PKSClusterConfigService;
import com.vmware.admiral.adapter.pks.service.PKSClusterListService;
import com.vmware.admiral.adapter.pks.test.MockPKSAdapterService;
import com.vmware.admiral.service.kubernetes.test.MockKubernetesApplicationAdapterService;
import com.vmware.admiral.service.test.MockKubernetesAdapterService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitKubernetesAdapterServiceConfig {
    public static void startServices(ServiceHost host, boolean startMockHostAdapterInstance) {
        if (startMockHostAdapterInstance) {
            host.startService(
                    Operation.createPost(
                            UriUtils.buildUri(host, MockKubernetesApplicationAdapterService.class)),
                    new MockKubernetesApplicationAdapterService());

            host.startService(
                    Operation.createPost(
                            UriUtils.buildUri(host, MockKubernetesAdapterService.class)),
                    new MockKubernetesAdapterService());

            host.startService(
                    Operation.createPost(
                            UriUtils.buildUri(host, MockPKSAdapterService.class)),
                    new MockPKSAdapterService());
        } else {
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, KubernetesAdapterService.class)),
                    new KubernetesAdapterService());
            host.startService(
                    Operation.createPost(
                            UriUtils.buildUri(host, KubernetesHostAdapterService.class)),
                    new KubernetesHostAdapterService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host,
                            KubernetesApplicationAdapterService.class)),
                    new KubernetesApplicationAdapterService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, PKSAdapterService.class)),
                    new PKSAdapterService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, PKSClusterListService.class)),
                    new PKSClusterListService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, PKSClusterConfigService.class)),
                    new PKSClusterConfigService());
            host.startService(
                    Operation.createPost(UriUtils.buildUri(host, KubeConfigContentService.class)),
                    new KubeConfigContentService());
        }
    }
}
