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

import static com.codeborne.selenide.Selenide.close;

import java.util.logging.Logger;

import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.vmware.admiral.test.ui.pages.applications.ApplicationsPageLibrary;
import com.vmware.admiral.test.ui.pages.containers.ContainersPageLibrary;
import com.vmware.admiral.test.ui.pages.identity.IdentityManagementPageLibrary;
import com.vmware.admiral.test.ui.pages.logs.LogsPageLibrary;
import com.vmware.admiral.test.ui.pages.main.MainPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPageLibrary;
import com.vmware.admiral.test.ui.pages.projects.ProjectsPageLibrary;
import com.vmware.admiral.test.ui.pages.publicrepos.RepositoriesPageLibrary;
import com.vmware.admiral.test.ui.pages.registries.GlobalRegistriesPageLibrary;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPageLibrary;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPageLibrary;
import com.vmware.admiral.test.util.AuthContext;
import com.vmware.admiral.test.util.SSHCommandExecutor;
import com.vmware.admiral.test.util.ScreenshotRule;
import com.vmware.admiral.test.util.TestStatusLoggerRule;
import com.vmware.admiral.vic.test.ui.pages.VICWebClient;
import com.vmware.admiral.vic.test.ui.pages.configuration.ConfigurationPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.hosts.ContainerHostsPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.internalrepos.BuiltInRepositoriesPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;
import com.vmware.admiral.vic.test.ui.util.TestProperties;
import com.vmware.admiral.vic.test.ui.util.VchPoolRule;

public class BaseTest {

    protected final Logger LOG = Logger.getLogger(getClass().getName());

    private static final AuthContext vicOvaAuthContext = new AuthContext(TestProperties.vicIp(),
            TestProperties.vicSshUsername(), TestProperties.vicSshPassword());
    private static final AuthContext vcenterAuthContext = new AuthContext(
            TestProperties.vcenterIp(),
            TestProperties.defaultAdminUsername(), TestProperties.defaultAdminPassword());

    public final VchPoolRule POOL = new VchPoolRule();

    @Rule
    public TestRule chain = RuleChain
            .outerRule(new TestStatusLoggerRule())
            .around(POOL)
            .around(new ScreenshotRule());

    private VICWebClient client = new VICWebClient();

    protected static String getVicUrl() {
        return BaseSuite.getVicUrl();
    }

    protected static String getVchUrl(String vchIp) {
        return "https://" + vchIp + ":" + TestProperties.defaultVchPort();
    }

    protected static AuthContext getVicOvaAuthContext() {
        return vicOvaAuthContext;
    }

    protected static AuthContext getVcenterAuthContext() {
        return vcenterAuthContext;
    }

    @AfterClass
    public static void closeBrowser() {
        close();
    }

    protected void loginAsAdmin() {
        String username = TestProperties.defaultAdminUsername();
        String password = TestProperties.defaultAdminPassword();
        loginAs(username, password);
    }

    protected void loginAs(String username, String password) {
        client.logIn(getVicUrl(), username, password);
    }

    protected void logOut() {
        client.main().logOut();
        client.waitToLogout();
    }

    protected MainPage main() {
        return client.main();
    }

    protected VICHomeTab home() {
        return client.home();
    }

    protected VICAdministrationTab administration() {
        return client.administration();
    }

    protected ApplicationsPageLibrary applications() {
        return client.applications();
    }

    protected ContainersPageLibrary containers() {
        return client.containers();
    }

    protected NetworksPageLibrary networks() {
        return client.networks();
    }

    protected VolumesPageLibrary volumes() {
        return client.volumes();
    }

    protected TemplatesPageLibrary templates() {
        return client.templates();
    }

    protected RepositoriesPageLibrary repositories() {
        return client.repositories();
    }

    protected BuiltInRepositoriesPageLibrary builtInRepositories() {
        return client.builtInRepositories();
    }

    protected ContainerHostsPageLibrary clusters() {
        return client.clusters();
    }

    protected IdentityManagementPageLibrary identity() {
        return client.identity();
    }

    protected ProjectsPageLibrary projects() {
        return client.projects();
    }

    protected GlobalRegistriesPageLibrary registries() {
        return client.registries();
    }

    protected ConfigurationPageLibrary configuration() {
        return client.configuration();
    }

    protected LogsPageLibrary logs() {
        return client.logs();
    }

    protected static SSHCommandExecutor createVicOvaSshCommandExecutor() {
        return new SSHCommandExecutor(getVicOvaAuthContext(), 22);
    }

    public void sleep(int miliseconds) {
        try {
            Thread.sleep(miliseconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
