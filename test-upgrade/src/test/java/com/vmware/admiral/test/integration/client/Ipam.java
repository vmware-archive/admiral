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
 * IPAM configuration of a given network. Follows the specification of the ipam element in docker
 * compose networking https://docs.docker.com/compose/compose-file/#ipam
 */
public class Ipam {

    public String driver;

    public IpamConfig[] config;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Ipam that = (Ipam) obj;

        if (driver != null ? !driver.equals(that.driver) : that.driver != null) {
            return false;
        }
        if (config != null ? that.config == null || config.length != that.config.length
                : that.config != null) {
            return false;
        }

        if (config != null) {
            for (int i = 0; i < config.length; i++) {
                if (!config[i].equals(that.config[i])) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = driver != null ? driver.hashCode() : 0;
        if (config != null) {
            for (IpamConfig c : config) {
                result = 37 * result + c.hashCode();
            }
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ipam {driver='");
        sb.append(driver);
        sb.append("', config='[");
        if (config != null) {
            for (IpamConfig c : config) {
                sb.append(c.toString());
                sb.append(", ");
            }
            int length = sb.length();
            sb.delete(length - 2, length);
        }
        sb.append("]'}");

        return sb.toString();
    }

}
