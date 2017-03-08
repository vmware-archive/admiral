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

package com.vmware.admiral.test.integration.client;

/**
 * Port binding for a container
 */
public class PortBinding {
    public String hostIp;
    public String hostPort;
    public String containerPort;
    public String protocol;

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
        if (hostIp != null && hostIp.trim().length() > 0) {
            sb.append(hostIp.trim());
            sb.append(":");
        }

        if (hostPort != null && hostPort.trim().length() > 0) {
            sb.append(hostPort.trim());
        }

        if (sb.length() > 0) {
            sb.append(":");
        }

        sb.append(containerPort.trim());

        if (protocol != null && protocol.trim().length() > 0) {
            sb.append("/");
            sb.append(protocol.trim());
        }
        return sb.toString();
    }
}
