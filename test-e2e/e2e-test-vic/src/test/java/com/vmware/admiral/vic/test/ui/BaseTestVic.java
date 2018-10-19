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

import java.util.Arrays;
import java.util.Objects;

import com.codeborne.selenide.Configuration;

import org.apache.http.Header;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicHeader;
import org.junit.BeforeClass;

import com.vmware.admiral.test.ui.BaseTest;
import com.vmware.admiral.test.ui.pages.main.MainPage;
import com.vmware.admiral.test.util.HttpUtils;
import com.vmware.admiral.test.util.SshCommandExecutor;
import com.vmware.admiral.test.util.host.ContainerHost;
import com.vmware.admiral.vic.test.VicTestProperties;
import com.vmware.admiral.vic.test.ui.pages.VICWebClient;
import com.vmware.admiral.vic.test.ui.pages.configuration.ConfigurationPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.hosts.ContainerHostsPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.internalrepos.BuiltInRepositoriesPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;
import com.vmware.admiral.vic.test.util.VICAuthTokenGetter;

public abstract class BaseTestVic extends BaseTest {

    private final VICWebClient CLIENT = new VICWebClient();

    private String vicTarget;

    @BeforeClass
    public static void applyConfiguration() {
        Configuration.screenshots = false;
        Configuration.savePageSource = false;
        String timeout = VicTestProperties.waitForElementTimeoutMiliseconds();
        Configuration.timeout = Integer.parseInt(timeout);

        String closeBrowserTimeout = VicTestProperties.browserCloseTimeoutMiliseconds();
        Configuration.closeBrowserTimeoutMs = Integer.parseInt(closeBrowserTimeout);

        Configuration.browser = VicTestProperties.browser();
        System.getProperties().put("wdm.chromeDriverVersion",
                VicTestProperties.chromeDriverVersion());

        String pollinfInterval = VicTestProperties.pollingIntervalMiliseconds();
        Configuration.pollingInterval = Integer.parseInt(pollinfInterval);

        Configuration.reportsFolder = VicTestProperties.screenshotFolder();

        String loginTimeout = VicTestProperties.loginTimeoutSeconds();
        if (!Objects.isNull(loginTimeout) && !loginTimeout.isEmpty()) {
            VICWebClient.setLoginTimeoutSeconds(Integer.parseInt(loginTimeout));
        }
    }

    protected void loginAsAdmin() {
        String username = VicTestProperties.defaultAdminUsername();
        String password = VicTestProperties.defaultAdminPassword();
        loginAs(username, password);
    }

    protected void loginAs(String username, String password) {
        CLIENT.logIn(getVicTarget(), username, password);
    }

    protected void logOut() {
        main().logOut();
        getClient().waitToLogout();
    }

    protected BuiltInRepositoriesPageLibrary builtInRepositories() {
        return getClient().builtInRepositories();
    }

    protected ConfigurationPageLibrary configuration() {
        return getClient().configuration();
    }

    protected static SshCommandExecutor createVicOvaSshCommandExecutor() {
        return SshCommandExecutor.createWithPasswordAuthentication(VicTestProperties.vicIp(),
                VicTestProperties.vicSshUsername(), VicTestProperties.vicSshPassword());
    }

    protected MainPage main() {
        return getClient().main();
    }

    protected VICHomeTab home() {
        return getClient().home();
    }

    protected VICAdministrationTab administration() {
        return getClient().administration();
    }

    @Override
    protected ContainerHostsPageLibrary clusters() {
        return getClient().clusters();
    }

    protected String getVicTarget() {
        if (Objects.isNull(vicTarget)) {
            vicTarget = "https://" + VicTestProperties.vicIp() + ":" + VicTestProperties.vicPort();
        }
        return vicTarget;
    }

    @Override
    protected VICWebClient getClient() {
        return CLIENT;
    }

    protected HttpClient getAdminHttpClient() {
        String token = VICAuthTokenGetter.getVICAuthToken(getVicTarget(),
                VicTestProperties.defaultAdminUsername(), VicTestProperties.defaultAdminPassword());
        HttpClient client = HttpUtils.createUnsecureHttpClient(null,
                Arrays.asList(new Header[] { new BasicHeader("x-xenon-auth-token", token) }));
        return client;
    }

    protected String getHostAddress(ContainerHost host) {
        String hostAddress;
        if (host.hasServerCertificate()) {
            hostAddress = "https://";
        } else {
            hostAddress = "http://";
        }
        hostAddress += host.getIp() + ":" + host.getPort();
        return hostAddress;
    }

}
