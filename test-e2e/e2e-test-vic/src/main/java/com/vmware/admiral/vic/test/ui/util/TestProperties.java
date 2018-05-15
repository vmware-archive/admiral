/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.vic.test.ui.util;

import java.util.Properties;

import com.vmware.admiral.common.util.FileUtil;

public class TestProperties {

    private static final String PROPERTIES_FILE_NAME = "vic-ui-test.properties";

    private static final String VCENTER_IP_PROPERTY = "vcenter.ip";
    private static final String VIC_IP_PROPERTY = "vic.ip";
    private static final String VIC_VM_USERNAME_PROPERTY = "vic.vm.username";
    private static final String VIC_VM_PASSWORD_PROPERTY = "vic.vm.password";
    private static final String VCH_IP_PROPERTY = "vch.ip";
    private static final String VCH_PORT_PROPERTY = "vch.port";
    private static final String DEFAULT_ADMIN_USERNAME_PROPERTY = "default.administrator.username";
    private static final String DEFAULT_ADMIN_PASSWORD_PROPERTY = "default.administrator.password";
    private static final String ACTIVE_DIRECTORIES_SPEC_FILES_CSV_PROPERTY = "active.directory.spec.files.csv";

    private static final String BROWSER_PROPERTY = "browser";
    private static final String CHROME_DRIVER_VERSION = "chrome.driver.version";
    private static final String WAIT_FOR_ELEMENT_TIMEOUT = "locate.element.timeout.miliseconds";
    private static final String BROWSER_CLOSE_TIMEOUT = "browser.close.timeout.miliseconds";
    private static final String POLLING_INTERVAL = "polling.interval.miliseconds";
    private static final String SCREENSHOTS_FOLDER = "screenshots.folder";

    private static final String LOGIN_TIMEOUT_SECONDS = "login.timeout.seconds";

    private static final Properties PROPERTIES = FileUtil.getProperties(
            constructPropertiesFilePath(),
            true);

    public static String vcenterIp() {
        return PROPERTIES.getProperty(VCENTER_IP_PROPERTY);
    }

    public static String vicIp() {
        return PROPERTIES.getProperty(VIC_IP_PROPERTY);
    }

    public static String vicSshUsername() {
        return PROPERTIES.getProperty(VIC_VM_USERNAME_PROPERTY);
    }

    public static String vicSshPassword() {
        return PROPERTIES.getProperty(VIC_VM_PASSWORD_PROPERTY);
    }

    public static String defaultVchIp() {
        return PROPERTIES.getProperty(VCH_IP_PROPERTY);
    }

    public static String defaultVchPort() {
        return PROPERTIES.getProperty(VCH_PORT_PROPERTY);
    }

    public static String defaultAdminUsername() {
        return PROPERTIES.getProperty(DEFAULT_ADMIN_USERNAME_PROPERTY);
    }

    public static String defaultAdminPassword() {
        return PROPERTIES.getProperty(DEFAULT_ADMIN_PASSWORD_PROPERTY);
    }

    public static String activeDiroctorySpecFilesCsv() {
        return PROPERTIES.getProperty(ACTIVE_DIRECTORIES_SPEC_FILES_CSV_PROPERTY);
    }

    public static String chromeDriverVersion() {
        return PROPERTIES.getProperty(CHROME_DRIVER_VERSION);
    }

    public static String browser() {
        return PROPERTIES.getProperty(BROWSER_PROPERTY);
    }

    public static String waitForElementTimeoutMiliseconds() {
        return PROPERTIES.getProperty(WAIT_FOR_ELEMENT_TIMEOUT);
    }

    public static String browserCloseTimeoutMiliseconds() {
        return PROPERTIES.getProperty(BROWSER_CLOSE_TIMEOUT);
    }

    public static String pollingIntervalMiliseconds() {
        return PROPERTIES.getProperty(POLLING_INTERVAL);
    }

    public static String screenshotFolder() {
        return PROPERTIES.getProperty(SCREENSHOTS_FOLDER);
    }

    public static String loginTimeoutSeconds() {
        return PROPERTIES.getProperty(LOGIN_TIMEOUT_SECONDS);
    }

    private static final String constructPropertiesFilePath() {
        return "/" + PROPERTIES_FILE_NAME;
    }

}
