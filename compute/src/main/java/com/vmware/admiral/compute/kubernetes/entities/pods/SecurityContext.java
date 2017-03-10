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

/**
 * SecurityContext holds security configuration that will be applied to a container.
 * Some fields are present in both SecurityContext and PodSecurityContext.
 * When both are set, the values in SecurityContext take precedence.
 */
public class SecurityContext {

    public Boolean privileged;

    public Long runAsUser;

    public Boolean runAsNonRoot;

    public Boolean readOnlyRootFilesystem;
}
