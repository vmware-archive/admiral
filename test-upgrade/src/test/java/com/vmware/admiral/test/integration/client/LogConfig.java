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
 * Log configuration of the container
 */
public class LogConfig {
    public String type;

    public Map<String, String> config;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        LogConfig logConfig = (LogConfig) obj;

        if (type != null ? !type.equals(logConfig.type) : logConfig.type != null) {
            return false;
        }
        return config != null ? config.equals(logConfig.config) : logConfig.config == null;
    }

    @Override
    public int hashCode() {
        int result = type != null ? type.hashCode() : 0;
        result = 31 * result + (config != null ? config.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("LogConfig {type='");
        sb.append(type);
        sb.append("', config='{");
        if (config != null) {
            for (Map.Entry<String, String> entry : config.entrySet()) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
            }
        }
        sb.append("'}}");

        return sb.toString();
    }
}
