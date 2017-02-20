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

package com.vmware.admiral.compute.kubernetes.entities.common;

import java.util.HashMap;

public class ObjectMeta {

    /**
     * Name must be unique within a namespace.
     * Is required when creating resources, although some resources may allow a
     * client to request the generation of an appropriate name automatically.
     */
    public String name;

    /**
     * GenerateName is an optional prefix, used by the server,
     * to generate a unique name ONLY IF the Name field has not been provided.
     */
    public String generateName;

    /**
     * Namespace defines the space within each name must be unique.
     * An empty namespace is equivalent to the "default" namespace.
     */
    public String namespace;

    /**
     * SelfLink is a URL representing this object. Populated by the system. Read-only.
     */
    public String selfLink;

    /**
     * Map of string keys and values that can be used to organize and categorize
     * (scope and select) objects. May match selectors of replication controllers and services.
     */
    public HashMap<String, Object> labels;

    /**
     * Annotations is an unstructured key value map stored with a resource that may be set by
     * external tools to store and retrieve arbitrary metadata.
     */
    public HashMap<String, String> annotations;

    /**
     * CreationTimestamp is a timestamp representing the server time when this object was created.
     */
    public String creationTimestamp;

    /**
     * The name of the cluster which the object belongs to.
     * This is used to distinguish resources with same name and namespace in different clusters.
     */
    public String clusterName;

}
