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
import com.vmware.admiral.test.ui.pages.AdmiralWebClientConfiguration;
import com.vmware.admiral.vic.test.ui.util.IdentitySourceConfigurator;

@RunWith(Suite.class)
public class BaseSuite {

    private static Logger LOG = Logger.getLogger(BaseSuite.class.getName());

    private static Properties properties = FileUtil
            .getProperties("/" + PropertiesNames.PROPERTIES_FILE_NAME, true);

    @BeforeClass
    public static void applyConfiguration() {
        LOG.info("Applying the configuration from the properties file");
        String timeout = properties.getProperty(PropertiesNames.WAIT_FOR_ELEMENT_TIMEOUT, "10000");
        Configuration.timeout = Integer.parseInt(timeout);

        String closeBrowserTimeout = properties.getProperty(PropertiesNames.BROWSER_CLOSE_TIMEOUT,
                "0");
        Configuration.closeBrowserTimeoutMs = Integer.parseInt(closeBrowserTimeout);

        String pollinfInterval = properties.getProperty(PropertiesNames.POLLING_INTERVAL, "100");
        Configuration.pollingInterval = Integer.parseInt(pollinfInterval);

        Configuration.reportsFolder = properties.getProperty(PropertiesNames.SCREENSHOTS_FOLDER,
                "target/screenshots");

        String loginTimeout = properties.getProperty(PropertiesNames.LOGIN_TIMEOUT_SECONDS);
        if (!Objects.isNull(loginTimeout) && !loginTimeout.isEmpty()) {
            AdmiralWebClientConfiguration.setLoginTimeoutSeconds(Integer.parseInt(loginTimeout));
        }

        String requestPollingInterval = properties
                .getProperty(PropertiesNames.REQUEST_POLLING_INTERVAL_MILISECONDS);
        if (!Objects.isNull(requestPollingInterval) && !requestPollingInterval.isEmpty()) {
            AdmiralWebClientConfiguration.setRequestPollingIntervalMiliseconds(Integer
                    .parseInt(requestPollingInterval));
        }

        String addHostTimeout = properties.getProperty(PropertiesNames.ADD_HOST_TIMEOUT_SECONDS);
        if (!Objects.isNull(addHostTimeout) && !addHostTimeout.isEmpty()) {
            AdmiralWebClientConfiguration
                    .setAddHostTimeoutSeconds(Integer.parseInt(addHostTimeout));
        }

        String deleteHostTimeout = properties
                .getProperty(PropertiesNames.DELETE_HOST_TIMEOUT_SECONDS);
        if (!Objects.isNull(deleteHostTimeout) && !deleteHostTimeout.isEmpty()) {
            AdmiralWebClientConfiguration.setDeleteHostTimeoutSeconds(Integer
                    .parseInt(deleteHostTimeout));
        }
    }

    @BeforeClass
    public static void configureActiveDirectories() throws IOException {
        LOG.info("Configuring active directories");
        String adCsv = properties
                .getProperty(PropertiesNames.ACTIVE_DIRECTORIES_SPEC_FILES_CSV_PROPERTY).trim();
        if (adCsv.isEmpty()) {
            LOG.warning(
                    "No active direcories spec files were specified in the properties file, no active directories will be configured");
            return;
        }
        List<String> adSpecFilenames = Arrays.asList(adCsv.split(","));
        Properties props = FileUtil.getProperties("/" + PropertiesNames.PROPERTIES_FILE_NAME, true);
        String vcenterAddress = props.getProperty(PropertiesNames.VCENTER_IP_PROPERTY);
        if (vcenterAddress.endsWith("/")) {
            vcenterAddress = vcenterAddress.substring(0, vcenterAddress.length() - 1);
        }
        if (!vcenterAddress.startsWith("https://")) {
            vcenterAddress = "https://" + vcenterAddress;
        }
        Objects.requireNonNull(vcenterAddress);
        String adminUsername = props.getProperty(PropertiesNames.DEFAULT_ADMIN_USERNAME_PROPERTY);
        Objects.requireNonNull(adminUsername);
        String adminPassword = props.getProperty(PropertiesNames.DEFAULT_ADMIN_PASSWORD_PROPERTY);
        IdentitySourceConfigurator identityConfigurator = new IdentitySourceConfigurator(
                vcenterAddress, adminUsername, adminPassword);

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

}
