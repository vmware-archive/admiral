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

package com.vmware.admiral.compute.kubernetes.entities.pods;

import com.vmware.admiral.compute.kubernetes.entities.common.BaseKubernetesObject;
import com.vmware.xenon.common.ServiceDocument.Documentation;

/**
 * Pod is a collection of containers that can run on a host. T
 * his resource is created by clients and scheduled onto hosts.
 */
public class Pod extends BaseKubernetesObject {

    /**
     * Specification of the desired behavior of the pod.
     */
    @Documentation(description = "Specification of the desired behavior of the pod.")
    public PodSpec spec;

    /**
     * Most recently observed status of the pod.
     */
    @Documentation(description = "Most recently observed status of the pod.")
    public PodStatus status;
}
