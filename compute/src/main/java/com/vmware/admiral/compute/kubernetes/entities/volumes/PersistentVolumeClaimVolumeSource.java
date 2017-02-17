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
 * PersistentVolumeClaimVolumeSource references the user’s PVC in the same namespace.
 * This volume finds the bound PV and mounts that volume for the pod.
 * A PersistentVolumeClaimVolumeSource is, essentially, a wrapper around another
 * type of volume that is owned by someone else (the system).
 */
public class PersistentVolumeClaimVolumeSource {

    /**
     * ClaimName is the name of a PersistentVolumeClaim in the same namespace as the pod using this volume.
     */
    public String claimName;

    /**
     * Will force the ReadOnly setting in VolumeMounts. Default false.
     */
    public String readOnly;
}
