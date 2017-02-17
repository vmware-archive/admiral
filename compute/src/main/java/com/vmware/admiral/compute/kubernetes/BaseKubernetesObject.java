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

package com.vmware.admiral.compute.kubernetes;

import com.vmware.admiral.compute.kubernetes.entities.ObjectMeta;
import com.vmware.photon.controller.model.resources.ResourceState;

/**
 * Base object which other states will extend.
 * Contains the common properties for all kubernetes objects
 */
public class BaseKubernetesObject extends ResourceState {

    /**
     * The api version of the object.
     */
    public String apiVersion;

    /**
     * The kind of the object.
     */
    public String kind;

    /**
     * The metadata version of the object.
     */
    public ObjectMeta metadata;

    /**
     * Serialized kubernetes entity in YAML format.
     */
    @Documentation(description = "Serialized kubernetes entity in YAML format.")
    public String kubernetesEntity;

}
