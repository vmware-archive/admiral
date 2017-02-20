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

import com.vmware.admiral.compute.kubernetes.entities.common.ConfigMapKeySelector;
import com.vmware.admiral.compute.kubernetes.entities.common.ObjectFieldSelector;
import com.vmware.admiral.compute.kubernetes.entities.common.ResourceFieldSelector;
import com.vmware.admiral.compute.kubernetes.entities.common.SecretKeySelector;

/**
 * EnvVarSource represents a source for the value of an EnvVar.
 */
public class EnvVarSource {

    /**
     * Selects a field of the pod: supports metadata.name, metadata.namespace, metadata.labels,
     * metadata.annotations, spec.nodeName, spec.serviceAccountName, status.podIP.
     */
    public ObjectFieldSelector fieldRef;

    /**
     * Selects a resource of the container: only resources limits and requests
     * (limits.cpu, limits.memory, requests.cpu and requests.memory) are currently supported.
     */
    public ResourceFieldSelector resourceFieldRef;

    /**
     * Selects a key of a ConfigMap.
     */
    public ConfigMapKeySelector configMapKeyRef;

    /**
     * Selects a key of a secret in the pod’s namespace.
     */
    public SecretKeySelector secretKeyRef;
}
