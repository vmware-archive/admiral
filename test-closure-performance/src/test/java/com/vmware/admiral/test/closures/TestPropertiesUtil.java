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

package com.vmware.admiral.test.closures;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.logging.Logger;

public final class TestPropertiesUtil {
    private static Logger logger = Logger.getLogger(TestPropertiesUtil.class.getName());

    private static final String TEST_INTEGRATION_PROPERTIES_FILE_PATH = "test.integration.properties";

    private TestPropertiesUtil() {
        // Do not create instances of this
    }

    private static final Properties testProperties = loadTestProperties();

    private static Properties loadTestProperties() {
        Properties properties = new Properties();
        try {
            properties.load(BasePerformanceSupportIT.class.getClassLoader()
                    .getResourceAsStream("integration-test.properties"));
            File systemConfiguredTestPropertiesFile = getSystemConfiguredTestPropertiesFile();
            if (systemConfiguredTestPropertiesFile == null) {
                logger.info(String.format("System property %s for external properties is not provided",
                        TEST_INTEGRATION_PROPERTIES_FILE_PATH));
            } else {
                loadProperties(properties, systemConfiguredTestPropertiesFile);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error during test properties loading.", e);
        }

        dumpProperties(properties);
        return properties;
    }

    private static final void dumpProperties(Properties properties) {
        logger.info("System properties");
        for (Entry<Object, Object> e : System.getProperties().entrySet()) {
            logger.info(e.getKey() + "=" + e.getValue());
        }
        logger.info("Properties from file");
        for (Entry<Object, Object> e : properties.entrySet()) {
            logger.info(e.getKey() + "=" + e.getValue());
        }
    }

    private static File getSystemConfiguredTestPropertiesFile() {
        String integrationProperties = System.getProperty(TEST_INTEGRATION_PROPERTIES_FILE_PATH);
        if (integrationProperties == null) {
            return null;
        }
        return new File(integrationProperties);
    }

    public static Properties loadProperties(Properties properties, File propertyFile) {
        try (FileInputStream inStream = new FileInputStream(propertyFile)) {
            properties.load(inStream);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Error loading %s", propertyFile.getAbsolutePath()), e);
        }
        return properties;
    }

    public static String getTestRequiredProp(String key) {
        String property = getSystemOrTestProp(key);
        if (property == null || property.isEmpty()) {
            throw new IllegalStateException(String.format("Property '%s' is required", key));
        }
        return property;
    }

    public static String getSystemOrTestProp(String key) {
        return getSystemOrTestProp(key, null);
    }

    public static String getSystemOrTestProp(String key, String defaultValue) {
        String result = System.getProperty(key);
        if (result == null) {
            result = testProperties.getProperty(key, defaultValue);
        }

        return result;
    }
}
