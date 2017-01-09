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

package com.vmware.admiral.adapter.kubernetes.service.apiobject;

public class Container {
    public String name;    // Required
    public String image;
    public String[] command;
    public String[] args;
    public String workingDir;
    public ContainerPort[] ports;
    public EnvVar[] env;
    // public ResourceRequirements resources;
    // public VolumeMount[] volumeMounts;
    // public Probe livenessProbe;
    // public Probe readinessProbe;
    // public Lifecycle lifecycle;
    // public String terminationMessagePath;
    public String imagePullPolicy;
    // public SecurityContext securityContext;
    // public boolean stdin;
    // public boolean stdinOnce;
    // public boolean tty;
}
