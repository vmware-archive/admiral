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
 * Represents a Flocker volume mounted by the Flocker agent. One and only one of datasetName
 * and datasetUUID should be set. Flocker volumes do not support ownership management or SELinux relabeling.
 */
public class FlockerVolumeSource {

    /**
     * Name of the dataset stored as metadata → name on the dataset for
     * Flocker should be considered as deprecated
     */
    public String datasetName;

    /**
     * UUID of the dataset. This is unique identifier of a Flocker dataset
     */
    public String datasetUUID;
}
