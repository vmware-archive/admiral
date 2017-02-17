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

package com.vmware.admiral.compute.kubernetes.entities.volumes;

/**
 * AzureFile represents an Azure File Service mount on the host and bind mount to the pod.
 */
public class AzureFileVolumeSource {

    /**
     * The name of secret that contains Azure Storage Account Name and Key
     */
    public String secretName;

    /**
     * Share Name
     */
    public String shareName;

    /**
     * Defaults to false (read/write). ReadOnly here will force the ReadOnly setting in VolumeMounts.
     */
    public Boolean readOnly;
}
