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

package com.vmware.admiral.vic.test.ui;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Logger;

import com.codeborne.selenide.Configuration;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import com.vmware.admiral.common.util.FileUtil;
import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.vic.test.ui.pages.VICWebClient;
import com.vmware.admiral.vic.test.ui.util.IdentitySourceConfigurator;

@RunWith(Suite.class)
public class BaseSuite {

    private static final String MANAGEMENT_PORTAL_PORT = ":8282";
    private static final String HTTPS_PROTOCOL = "https://";
    private static String vicUrl;

    private static Logger LOG = Logger.getLogger(BaseSuite.class.getName());

    public static final Properties PROPERTIES = FileUtil
            .getProperties("/" + PropertiesNames.PROPERTIES_FILE_NAME, true);

    @BeforeClass
    public static void applyConfiguration() {
        Configuration.screenshots = false;
        LOG.info("Applying the configuration from the properties file");
        String timeout = PROPERTIES.getProperty(PropertiesNames.WAIT_FOR_ELEMENT_TIMEOUT, "10000");
        Configuration.timeout = Integer.parseInt(timeout);

        String closeBrowserTimeout = PROPERTIES.getProperty(PropertiesNames.BROWSER_CLOSE_TIMEOUT,
                "0");
        Configuration.closeBrowserTimeoutMs = Integer.parseInt(closeBrowserTimeout);

        Configuration.browser = PROPERTIES.getProperty(PropertiesNames.BROWSER_PROPERTY, "chrome");

        String pollinfInterval = PROPERTIES.getProperty(PropertiesNames.POLLING_INTERVAL, "100");
        Configuration.pollingInterval = Integer.parseInt(pollinfInterval);

        Configuration.reportsFolder = PROPERTIES.getProperty(PropertiesNames.SCREENSHOTS_FOLDER,
                "target/screenshots");

        String loginTimeout = PROPERTIES.getProperty(PropertiesNames.LOGIN_TIMEOUT_SECONDS);
        if (!Objects.isNull(loginTimeout) && !loginTimeout.isEmpty()) {
            VICWebClient.setLoginTimeoutSeconds(Integer.parseInt(loginTimeout));
        }
    }

    @BeforeClass
    public static void configureActiveDirectories() throws IOException {
        LOG.info("Configuring active directories");
        String adCsv = PROPERTIES
                .getProperty(PropertiesNames.ACTIVE_DIRECTORIES_SPEC_FILES_CSV_PROPERTY).trim();
        if (adCsv.isEmpty()) {
            LOG.warning(
                    "No active direcories spec files were specified in the properties file, no active directories will be configured");
            return;
        }
        List<String> adSpecFilenames = Arrays.asList(adCsv.split(","));
        Properties props = FileUtil.getProperties("/" + PropertiesNames.PROPERTIES_FILE_NAME, true);
        String vcenterIp = props.getProperty(PropertiesNames.VCENTER_IP_PROPERTY);
        Objects.requireNonNull(vcenterIp);
        String adminUsername = props.getProperty(PropertiesNames.DEFAULT_ADMIN_USERNAME_PROPERTY);
        Objects.requireNonNull(adminUsername);
        String adminPassword = props.getProperty(PropertiesNames.DEFAULT_ADMIN_PASSWORD_PROPERTY);
        AuthContext vcenterAuthContext = new AuthContext(vcenterIp, adminUsername, adminPassword);
        IdentitySourceConfigurator identityConfigurator = new IdentitySourceConfigurator(
                vcenterAuthContext);

        for (String fileName : adSpecFilenames) {
            String body = null;
            try {
                body = FileUtil.getResourceAsString("/" + fileName.trim(), true);
            } catch (Exception e) {
                LOG.warning(
                        "Could not read resource file with filename: " + fileName);
                continue;
            }
            if (Objects.isNull(body) || body.trim().isEmpty()) {
                LOG.warning(String.format(
                        "Could not read AD spec body from file with filename: [%s], file is empty.",
                        fileName));
            }
            identityConfigurator.addIdentitySource(body);
        }
    }

    public static String getVicUrl() {
        if (Objects.isNull(vicUrl)) {
            vicUrl = getVicIp();
            if (!vicUrl.startsWith(HTTPS_PROTOCOL)) {
                vicUrl = HTTPS_PROTOCOL + vicUrl;
            }
            if (!vicUrl.endsWith(MANAGEMENT_PORTAL_PORT)) {
                vicUrl = vicUrl + MANAGEMENT_PORTAL_PORT;
            }
        }
        return vicUrl;
    }

    public static String getVicIp() {
        return PROPERTIES.getProperty(PropertiesNames.VIC_IP_PROPERTY);
    }

    public static String getVcenterIp() {
        return PROPERTIES.getProperty(PropertiesNames.VCENTER_IP_PROPERTY);
    }

    public static String getDefaultAdminUsername() {
        return PROPERTIES.getProperty(PropertiesNames.DEFAULT_ADMIN_USERNAME_PROPERTY);
    }

    public static String getDefaultAdminPassword() {
        return PROPERTIES.getProperty(PropertiesNames.DEFAULT_ADMIN_PASSWORD_PROPERTY);
    }
}
