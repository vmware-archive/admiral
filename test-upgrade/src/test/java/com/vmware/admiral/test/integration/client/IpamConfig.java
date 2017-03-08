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

import java.util.Map;

/**
 * IPAM config configuration as specified in the docker compose documentation
 * https://docs.docker.com/compose/compose-file/#ipam
 */
public class IpamConfig {

    public String subnet;

    public String ipRange;

    public String gateway;

    public Map<String, String> auxAddresses;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        IpamConfig that = (IpamConfig) obj;

        if (subnet != null ? !subnet.equals(that.subnet) : that.subnet != null) {
            return false;
        }
        if (ipRange != null ? !ipRange.equals(that.ipRange) : that.ipRange != null) {
            return false;
        }
        if (gateway != null ? !gateway.equals(that.gateway) : that.gateway != null) {
            return false;
        }

        return auxAddresses != null ? auxAddresses.equals(that.auxAddresses)
                : that.auxAddresses == null;
    }

    @Override
    public int hashCode() {
        int result = subnet != null ? subnet.hashCode() : 0;
        result = 31 * result + (ipRange != null ? ipRange.hashCode() : 0);
        result = 31 * result + (gateway != null ? gateway.hashCode() : 0);
        result = 31 * result + (auxAddresses != null ? auxAddresses.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IpamConfig {subnet='");
        sb.append(subnet);
        sb.append("', ipRange='");
        sb.append(ipRange);
        sb.append("', gateway='");
        sb.append(gateway);
        sb.append("', auxAddresses='");
        sb.append(auxAddresses != null ? auxAddresses.toString() : null);
        sb.append("'}");

        return sb.toString();
    }

}
