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

package com.vmware.admiral.host;

import com.vmware.admiral.adapter.kubernetes.service.KubernetesAdapterService;
import com.vmware.admiral.adapter.kubernetes.service.KubernetesApplicationAdapterService;
import com.vmware.admiral.adapter.kubernetes.service.KubernetesHostAdapterService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

public class HostInitKubernetesAdapterServiceConfig {
    public static void startServices(ServiceHost host, boolean startMockHostAdapterInstance) {
        if (startMockHostAdapterInstance) {
            // Do something
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
        }
    }
}
