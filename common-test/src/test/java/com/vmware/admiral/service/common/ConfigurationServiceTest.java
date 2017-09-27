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

package com.vmware.admiral.service.common;

import static com.vmware.admiral.service.common.ConfigurationService.CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import io.netty.util.internal.StringUtil;

import org.junit.Assert;
import org.junit.Test;

import com.vmware.admiral.service.common.ConfigurationService.ConfigurationState;

public class ConfigurationServiceTest {

    @Test
    public void testGetConfigurationProperties() {
        overrideConfigurationPropertiesFile("/customconfig.properties");

        ConfigurationState[] props = ConfigurationService.getConfigurationProperties();

        Assert.assertNotEquals(props.length, 0);

        boolean isSshConsoleRead = false;
        for (ConfigurationState x : props) {
            if (x.key.equals("allow.browser.ssh.console")) {
                isSshConsoleRead = true;
                Assert.assertEquals("true", x.value);
            }

            if (x.key.equals("allow.closures")) {
                // Validate that customconfig.properties has been read.
                Assert.assertEquals("true", x.value);
            }
        }

        Assert.assertTrue(isSshConsoleRead);
    }

    public static void overrideConfigurationPropertiesFile(String value) {
        System.setProperty("configuration.properties", value);

        // Reset the static final value of CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES in case other
        // tests have loaded the ConfigurationService class before the configuration.properties
        // property was set.
        if (StringUtil.isNullOrEmpty(CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES) ||
                !CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES.equals(value)) {

            try {
                Field customFileNameField = ConfigurationService.class
                        .getField("CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES");
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(customFileNameField,
                        customFileNameField.getModifiers() & ~Modifier.FINAL);

                customFileNameField.set(null, value);
            } catch (Exception ex) {
                Assert.fail("Could not set CUSTOM_CONFIGURATION_PROPERTIES_FILE_NAMES variable");
            }
        }
    }

}
