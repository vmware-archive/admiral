/*
 * Copyright (c) 2017-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.integration.client;

import java.net.URI;
import java.util.Map;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import com.vmware.admiral.test.integration.client.dcp.DcpDocumentKind;

/**
 * ContainerDescription
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
@JsonIgnoreProperties(ignoreUnknown = true)
@DcpDocumentKind("com:vmware:admiral:compute:container:ContainerDescriptionService:ContainerDescription")
public class ContainerDescription extends ResourceServiceDocument {
    public static final String RESOURCE_TYPE = "DOCKER_CONTAINER";

    public static final String FIELD_NAME_NAME = "name";
    public static final String FIELD_NAME_CUSTOM_PROPERTIES = "customProperties";

    /** (Required) The docker image */
    public String image;

    /** Commands to run. */
    public String[] command;

    /** Link to the parent container description */
    public String parentDescriptionLink;

    /**
     * (Optional) An image reference to a docker image in .tgz format to be downloaded to the server
     * and pushed to the local host repository.
     */
    public URI imageReference;

    /** Instance Adapter reference for provisioning of containers */
    public URI instanceAdapterReference;

    /** Data-center or other identification of the group of resources */
    public String zoneId;

    /**
     * Affinity or anti-affinity conditions of containers deployed or not deployed on the same host.
     * Format: "[!]serviceName[:soft|hard].
     *
     * If not specified, the default constraint type is "hard".
     *
     * Examples: ["cont1", "!cont2", "cont3:soft", "cont4:hard", "!cont5:soft", "!cont6:hard"]
     */
    public String[] affinity;

    /** User to use inside the container */
    public String user;

    /** Memory limit in bytes. */
    public Integer memoryLimit;

    /** Total memory usage (memory + swap); set -1 to disable swap. */
    public Integer memorySwapLimit;

    /** CPU Shares for container. */
    public Integer cpuShares;

    /** Force Docker to use specific DNS servers. */
    public String[] dns;

    /** A list of environment variables in the form of VAR=value. */
    public String[] env;

    /** Set the entrypoints for the container. */
    public String[] entryPoint;

    /** Mount a volume e.g /host:/container or /host:/container:ro */
    public String[] volumes;

    /** Working dir for commands to run in. */
    public String workingDir;

    /** Run in privileged mode. */
    public Boolean privileged;

    /** Host name of the container. */
    public String hostname;

    /** Domain name of the container. */
    public String domainName;

    /** Add a custom host-to-IP mapping (host:ip) */
    public String[] extraHosts;

    /** Automatically publish all exposed ports declared for the image */
    public Boolean publishAll;

    /**
     * Port bindings in the format ip:hostPort:containerPort | ip::containerPort |
     * hostPort:containerPort | containerPort where range of ports can also be provided
     */
    public PortBinding[] portBindings;

    /** The identity of a grouping associated usually with a host for multiple containers */
    public String pod;

    /** The number of nodes to be provisioned. */
    public Integer _cluster;

    /** A list of services (in a blueprint) the container depends on */
    public String[] links;

    /** custom DNS search domains (Use . if you don't wish to set the search domain) */
    public String[] dnsSearch;

    /** Mount volumes from the specified container(s) of the format <container name>[:<ro|rw>] */
    public String[] volumesFrom;

    /** Specify volume driver name. */
    public String volumeDriver;

    /** A list of kernel capabilities to add to the container. */
    public String[] capAdd;

    /** A list of kernel capabilities to drop from the container. */
    public String[] capDrop;

    /**
     * Restart policy to apply when a container exits (no, on-failure[:maximumRetryCount], always)
     */
    public String restartPolicy;

    /** When restart policy is set to on-failure, the max retries */
    public Integer maximumRetryCount;

    /** Network mode for the container (bridge / none / container:<name|id> / host) */
    public String networkMode;

    /** Networks to join, referencing declared or already existing networks */
    public Map<String, ServiceNetwork> networks;

    /** PID namespace for the container ( "" / host ) */
    public String pidMode;

    /**
     * A list of devices to add to the container specified in the format hostPath:containerPath:rwm
     */
    public String[] device;

    /**
     * Document id of the deployment policy if any. Container description with a deployment policy
     * will be deployed on hosts/policies with the same policy.
     */
    public String deploymentPolicyId;

    /**
     * Logging driver for the container and options.
     */
    public LogConfig logConfig;

    /**
     * Health configuration for the container.
     */
    public HealthConfig healthConfig;

    /** A list of services (in a blueprint) the container depends on */
    public String[] dependsOn;

    /** Custom properties. */
    public Map<String, String> customProperties;
}
