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

package com.vmware.admiral.compute.container;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.vmware.admiral.adapter.docker.util.DockerPortMapping;
import com.vmware.admiral.compute.content.kubernetes.PodContainerPort;

/**
 * Port binding for a container
 */
public class PortBinding {
    public static final String FIELD_NAME_HOST_PORT = "hostPort";

    @JsonProperty("host_ip")
    public String hostIp;

    @JsonProperty("host_port")
    public String hostPort;

    @JsonProperty("container_port")
    public String containerPort;

    @JsonProperty("protocol")
    public String protocol;

    public static PortBinding fromDockerPortMapping(DockerPortMapping mapping) {
        PortBinding portBinding = new PortBinding();
        portBinding.protocol = mapping.getProtocol().toString();
        portBinding.containerPort = mapping.getContainerPort();
        portBinding.hostIp = mapping.getHostIp();
        portBinding.hostPort = mapping.getHostPort();

        return portBinding;
    }

    public static PortBinding fromPodContainerPort(PodContainerPort podContainerPort) {
        PortBinding portBinding = new PortBinding();
        portBinding.protocol = podContainerPort.protocol;
        portBinding.containerPort = podContainerPort.containerPort;
        portBinding.hostIp = podContainerPort.hostIp;
        portBinding.hostPort = podContainerPort.hostPort;

        return portBinding;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((containerPort == null) ? 0 : containerPort.hashCode());
        result = prime * result + ((hostIp == null) ? 0 : hostIp.hashCode());
        result = prime * result + ((hostPort == null) ? 0 : hostPort.hashCode());
        result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        PortBinding other = (PortBinding) obj;
        if (containerPort == null) {
            if (other.containerPort != null) {
                return false;
            }
        } else if (!containerPort.equals(other.containerPort)) {
            return false;
        }
        if (hostIp == null) {
            if (other.hostIp != null) {
                return false;
            }
        } else if (!hostIp.equals(other.hostIp)) {
            return false;
        }
        if (hostPort == null) {
            if (other.hostPort != null) {
                return false;
            }
        } else if (!hostPort.equals(other.hostPort)) {
            return false;
        }
        if (protocol == null) {
            if (other.protocol != null) {
                return false;
            }
        } else if (!protocol.equals(other.protocol)) {
            return false;
        }
        return true;
    }

    /**
     * Format as a docker port mapping string (reverse of fromString)
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (hostIp != null) {
            sb.append(hostIp);
            sb.append(":");
        }

        if (hostPort != null) {
            sb.append(hostPort);
        }

        if (sb.length() > 0) {
            sb.append(":");
        }

        sb.append(containerPort);
        if (protocol != null) {
            sb.append("/");
            sb.append(protocol.toString());
        }
        return sb.toString();
    }

}
