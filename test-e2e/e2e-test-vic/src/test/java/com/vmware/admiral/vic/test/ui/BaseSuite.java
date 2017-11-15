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

package com.vmware.admiral.vic.test.ui;

import java.util.Properties;

import com.codeborne.selenide.Configuration;

import org.junit.BeforeClass;

import com.vmware.admiral.test.ui.FileUtil;
import com.vmware.admiral.test.ui.pages.AdmiralWebClientConfiguration;

public class BaseSuite {

    protected static Properties properties;

    @BeforeClass
    public static void initProperties() {
        properties = FileUtil.loadProperties(PropertiesNames.PROPERTIES_FILE_NAME);

        String timeout = properties.getProperty(PropertiesNames.WAIT_FOR_ELEMENT_TIMEOUT, "10000");
        Configuration.timeout = Integer.parseInt(timeout);

        String closeBrowserTimeout = properties.getProperty(PropertiesNames.BROWSER_CLOSE_TIMEOUT,
                "0");
        Configuration.closeBrowserTimeoutMs = Integer.parseInt(closeBrowserTimeout);

        String pollinfInterval = properties.getProperty(PropertiesNames.POLLING_INTERVAL, "100");
        Configuration.pollingInterval = Integer.parseInt(pollinfInterval);

        Configuration.reportsFolder = properties.getProperty(PropertiesNames.SCREENSHOTS_FOLDER,
                "target/screenshots");

        String loginTimeout = properties.getProperty(PropertiesNames.LOGIN_TIMEOUT_SECONDS, "30");
        AdmiralWebClientConfiguration.LOGIN_TIMEOUT_SECONDS = Integer.parseInt(loginTimeout);

        String requestPollingInterval = properties
                .getProperty(PropertiesNames.REQUEST_POLLING_INTERVAL_MILISECONDS, "500");
        AdmiralWebClientConfiguration.REQUEST_POLLING_INTERVAL_MILISECONDS = Integer
                .parseInt(requestPollingInterval);

        String addHostTimeout = properties.getProperty(PropertiesNames.ADD_HOST_TIMEOUT_SECONDS,
                "20");
        AdmiralWebClientConfiguration.ADD_HOST_TIMEOUT_SECONDS = Integer.parseInt(addHostTimeout);

        String deleteHostTimeout = properties
                .getProperty(PropertiesNames.DELETE_HOST_TIMEOUT_SECONDS, "20");
        AdmiralWebClientConfiguration.DELETE_HOST_TIMEOUT_SECONDS = Integer
                .parseInt(deleteHostTimeout);
    }

}
