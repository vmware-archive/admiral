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

package com.vmware.admiral.compute.kubernetes;

import com.vmware.photon.controller.model.resources.ComputeDescriptionService;
import com.vmware.xenon.common.UriUtils;

public interface KubernetesHostConstants {
    public static final String KUBERNETES_COMPUTE_DESC_ID = "kubernetes-host-compute-desc-id";
    public static final String KUBERNETES_COMPUTE_DESC_LINK = UriUtils.buildUriPath(
            ComputeDescriptionService.FACTORY_LINK, KUBERNETES_COMPUTE_DESC_ID);
    public static final String KUBERNETES_HOST_DEFAULT_NAMESPACE = "default";
    public static final String KUBERNETES_HOST_NAMESPACE_PROP_NAME = "__kubernetesNamespace";

    // When creating a namespace on kubernetes it matches the name with this regex
    public static final String KUBERNETES_NAMESPACE_REGEX = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";
}
