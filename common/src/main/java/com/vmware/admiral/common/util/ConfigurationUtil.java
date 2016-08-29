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

package com.vmware.admiral.common.util;

import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;

public class ConfigurationUtil {

    private static ConfigurationState[] configProperties;

    public static void initialize(ConfigurationState... cs) {
        configProperties = cs;
    }

    public static String getProperty(String propertyName) {
        if (configProperties == null || propertyName == null) {
            return null;
        }
        for (ConfigurationState config : configProperties) {
            if (propertyName.equals(config.key)) {
                return config.value;
            }
        }

        return null;
    }

}
