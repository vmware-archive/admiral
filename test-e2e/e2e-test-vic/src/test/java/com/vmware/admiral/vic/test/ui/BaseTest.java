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

import java.util.Objects;
import java.util.Properties;

import com.codeborne.selenide.junit.ScreenShooter;

import org.junit.Rule;
import org.junit.runner.RunWith;

import com.vmware.admiral.test.ui.FileUtil;
import com.vmware.admiral.test.ui.SelenideClassRunner;
import com.vmware.admiral.vic.test.ui.pages.VICWebClient;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;

@RunWith(SelenideClassRunner.class)
public class BaseTest {

    private final String MANAGEMENT_PORTAL_PORT = ":8282";
    private final String HTTPS_PROTOCOL = "https://";

    protected final Properties PROPERTIES = FileUtil
            .loadProperties(PropertiesNames.PROPERTIES_FILE_NAME);

    @Rule
    public ScreenShooter makeScreenshotOnFailure = ScreenShooter.failedTests();

    private VICWebClient client;

    protected VICWebClient getClient() {
        if (Objects.isNull(client)) {
            client = new VICWebClient();
        }
        return client;
    }

    protected String getVicUrl() {
        String vicIp = PROPERTIES.getProperty(PropertiesNames.VIC_IP_PROPERTY);
        Objects.requireNonNull(vicIp);
        if (!vicIp.startsWith(HTTPS_PROTOCOL)) {
            vicIp = HTTPS_PROTOCOL + vicIp;
        }
        if (!vicIp.endsWith(MANAGEMENT_PORTAL_PORT)) {
            vicIp = vicIp + MANAGEMENT_PORTAL_PORT;
        }
        return vicIp;
    }

    protected String getVchUrl() {
        String vchIp = PROPERTIES.getProperty(PropertiesNames.VCH_IP_PROPERTY);
        String vchPort = PROPERTIES.getProperty(PropertiesNames.VCH_PORT_PROPERTY);
        Objects.requireNonNull(vchIp);
        if (!vchIp.startsWith(HTTPS_PROTOCOL)) {
            vchIp = HTTPS_PROTOCOL + vchIp;
        }
        return vchIp + ":" + vchPort;
    }

    protected VICWebClient loginAsAdmin() {
        String target = getVicUrl();
        String username = PROPERTIES.getProperty(PropertiesNames.DEFAULT_ADMIN_USERNAME_PROPERTY);
        Objects.requireNonNull(username);
        String password = PROPERTIES.getProperty(PropertiesNames.DEFAULT_ADMIN_PASSWORD_PROPERTY);
        getClient().logIn(target, username, password);
        return getClient();
    }

    protected VICHomeTab navigateToHomeTab() {
        return getClient().navigateToHomeTab();
    }

    protected VICAdministrationTab navigateToAdministrationTab() {
        return getClient().navigateToAdministrationTab();
    }

    protected void logOut() {
        client.logOut();
    }

}
