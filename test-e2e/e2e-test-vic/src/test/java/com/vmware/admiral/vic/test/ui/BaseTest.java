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

import com.codeborne.selenide.junit.ScreenShooter;

import org.junit.Rule;
import org.junit.runner.RunWith;

import com.vmware.admiral.test.ui.pages.AdmiralWebClientConfiguration;
import com.vmware.admiral.test.ui.pages.SelenideClassRunner;
import com.vmware.admiral.vic.test.ui.pages.VICWebClient;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;

@RunWith(SelenideClassRunner.class)
public class BaseTest {

    private final String VIC_URL = "vic.url";
    private final String DOCKERHOST_URL = "dockerhost.url";
    private final String VCH_URL = "vch.url";
    private final String VIC_ADMIN_USERNAME_PROPERTY = "vic.administrator.username";
    private final String VIC_ADMIN_PASSWORD_PROPERTY = "vic.administrator.password";
    private final String LOGIN_TIMEOUT_SECONDS = "login.timeout.seconds";
    private final String REQUEST_POLLING_INTERVAL_MILISECONDS = "request.polling.interval.miliseconds";
    private final String ADD_HOST_TIMEOUT_SECONDS = "add.host.timeout.seconds";
    private final String DELETE_HOST_TIMEOUT_SECONDS = "delete.host.timeout.seconds";

    public BaseTest() {
        AdmiralWebClientConfiguration.LOGIN_TIMEOUT_SECONDS = Integer
                .parseInt(getProperty(LOGIN_TIMEOUT_SECONDS));
        AdmiralWebClientConfiguration.REQUEST_POLLING_INTERVAL_MILISECONDS = Integer
                .parseInt(getProperty(REQUEST_POLLING_INTERVAL_MILISECONDS));
        AdmiralWebClientConfiguration.ADD_HOST_TIMEOUT_SECONDS = Integer
                .parseInt(getProperty(ADD_HOST_TIMEOUT_SECONDS));
        AdmiralWebClientConfiguration.DELETE_HOST_TIMEOUT_SECONDS = Integer
                .parseInt(getProperty(DELETE_HOST_TIMEOUT_SECONDS));
    }

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
        return getProperty(VIC_URL);
    }

    protected String getDockerhostUrl() {
        return getProperty(DOCKERHOST_URL);
    }

    protected String getVchUrl() {
        return getProperty(VCH_URL);
    }

    protected VICWebClient loginAsAdmin() {
        String target = getProperty(VIC_URL);
        String username = getProperty(VIC_ADMIN_USERNAME_PROPERTY);
        String password = getProperty(VIC_ADMIN_PASSWORD_PROPERTY);
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

    protected String getProperty(String key) {
        String prop = System.getProperty(key);
        if (Objects.isNull(prop)) {
            throw new RuntimeException(String.format("Property with key [%s] not found", key));
        }
        return prop;
    }

}
