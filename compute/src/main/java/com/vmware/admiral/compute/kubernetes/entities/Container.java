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

package com.vmware.admiral.compute.kubernetes.entities;

import java.util.List;

/**
 * A single application container that you want to run within a pod.
 */
public class Container {

    /**
     * Name of the container specified as a DNS_LABEL.
     * Each container in a pod must have a unique name (DNS_LABEL). Cannot be updated.
     */
    public String name;

    /**
     * Docker image name.
     */
    public String image;

    /**
     * Entrypoint array. Not executed within a shell. The docker image’s ENTRYPOINT is used if this is not provided.
     */
    public List<String> command;

    /**
     * Arguments to the entrypoint. The docker image’s CMD is used if this is not provided.
     */
    public List<String> args;

    /**
     * Container’s working directory.
     */
    public String workingDir;

    /**
     * List of ports to expose from the container.
     */
    public List<ContainerPort> ports;

    /**
     * List of environment variables to set in the container.
     */
    public List<EnvVar> env;

    /**
     * Compute Resources required by this container.
     */
    public ResourceRequirements resources;

    /**
     * Pod volumes to mount into the container’s filesystem.
     */
    public List<VolumeMount> volumeMounts;

    /**
     * Periodic probe of container liveness. Container will be restarted if the probe fails.
     */
    public Probe livenessProbe;

    /**
     * Periodic probe of container service readiness.
     * Container will be removed from service endpoints if the probe fails.
     */
    public Probe readinessProbe;

    /**
     * Actions that the management system should take in response to container lifecycle events.
     */
    public Lifecycle lifecycle;

    /**
     * Optional: Path at which the file to which the container’s termination message
     * will be written is mounted into the container’s filesystem.
     */
    public String terminationMessagePath;

    /**
     * Image pull policy. One of Always, Never, IfNotPresent.
     * Defaults to Always if :latest tag is specified, or IfNotPresent otherwise.
     */
    public String imagePullPolicy;

    /**
     * Security options the pod should run with.
     */
    public SecurityContext securityContext;

    /**
     * Whether this container should allocate a buffer for stdin in the container runtime.
     */
    public Boolean stdin;

    /**
     * Whether the container runtime should close the stdin channel
     * after it has been opened by a single attach.
     */
    public Boolean stdinOnce;

    /**
     * Whether this container should allocate a TTY for itself, also requires stdin to be true.
     * Default is false.
     */
    public Boolean tty;

}
