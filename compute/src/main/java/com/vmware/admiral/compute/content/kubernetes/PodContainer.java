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

package com.vmware.admiral.compute.content.kubernetes;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PodContainer {
    public String name;
    public String image;
    public String[] command;
    public String[] args;
    public String workingDir;
    public PodContainerPort[] ports;
    public PodContainerEnvVar[] env;
    public PodContainerSecurityContext securityContext;
    public PodContainerProbe livenessProbe;
    public Map<String, PodContainerResources> resources;
}
