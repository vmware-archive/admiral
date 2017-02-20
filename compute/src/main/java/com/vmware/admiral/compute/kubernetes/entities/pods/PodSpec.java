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

import java.util.List;
import java.util.Map;

import com.vmware.admiral.compute.kubernetes.entities.volumes.Volume;

public class PodSpec {

    /**
     * List of volumes that can be mounted by containers belonging to the pod.
     */
    public List<Volume> volumes;

    /**
     * List of containers belonging to the pod. Containers cannot currently be added or removed.
     * There must be at least one container in a Pod.
     */
    public List<Container> containers;
    /**
     * Restart policy for all containers within the pod.
     */
    public RestartPolicy restartPolicy;

    /**
     * Optional duration in seconds the pod needs to terminate gracefully.
     */
    public Long terminationGracePeriodSeconds;

    /**
     * Optional duration in seconds the pod may be active on the node relative to StartTime
     * before the system will actively try to mark it failed and kill associated containers.
     */
    public Long activeDeadlineSeconds;

    /**
     * Set DNS policy for containers within the pod. Defaults to "ClusterFirst".
     */
    public DnsPolicy dnsPolicy;

    /**
     * NodeSelector is a selector which must be true for the pod to fit on a node.
     * Selector which must match a node’s labels for the pod to be scheduled on that node.
     */
    public Map<String, Object> nodeSelector;

    /**
     * NodeName is a request to schedule this pod onto a specific node.
     */
    public String nodeName;

    /**
     * Host networking requested for this pod. Use the host’s network namespace.
     * If this option is set, the ports that will be used must be specified.
     */
    public String hostNetwork;

    /**
     * Use the host’s pid namespace. Optional: Default to false.
     */
    public Boolean hostPID;

    /**
     * Use the host’s ipc namespace. Optional: Default to false.
     */
    public Boolean hostIPC;

    /**
     * SecurityContext holds pod-level security attributes and common container settings.
     */
    public PodSecurityContext securityContext;

    /**
     * Specifies the hostname of the Pod If not specified,
     * the pod’s hostname will be set to a system-defined value.
     */
    public String hostname;

    /**
     * If specified, the fully qualified Pod hostname will be
     * "<hostname>.<subdomain>.<pod namespace>.svc.<cluster domain>".
     * If not specified, the pod will not have a domainname at all.
     */
    public String subdomain;
}
