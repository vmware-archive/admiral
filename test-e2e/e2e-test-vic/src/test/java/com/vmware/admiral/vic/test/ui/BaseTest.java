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

import java.util.Properties;
import java.util.logging.Logger;

import com.spotify.docker.client.DockerClient;

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
import com.vmware.admiral.test.ui.pages.publicrepos.PublicRepositoriesPageLibrary;
import com.vmware.admiral.test.ui.pages.registries.RegistriesPageLibrary;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPageLibrary;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPageLibrary;
import com.vmware.admiral.test.util.DockerUtils;
import com.vmware.admiral.test.util.ScreenshotRule;
import com.vmware.admiral.test.util.TestStatusLoggerRule;
import com.vmware.admiral.vic.test.ui.pages.VICWebClient;
import com.vmware.admiral.vic.test.ui.pages.configuration.ConfigurationPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.hosts.ContainerHostsPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;
import com.vmware.admiral.vic.test.ui.pages.projectrepos.ProjectRepositoriesPageLibrary;
import com.vmware.admiral.vic.test.ui.util.VICReportsRule;

public class BaseTest {

    protected final Logger LOG = Logger.getLogger(getClass().getName());
    protected final Properties PROPERTIES = BaseSuite.PROPERTIES;

    @Rule
    public TestRule chain = RuleChain
            .outerRule(new TestStatusLoggerRule())
            .around(new VICReportsRule(getVicUrl(), getDefaultAdminUsername(),
                    getDefaultAdminPassword()))
            .around(new ScreenshotRule());

    private VICWebClient client = new VICWebClient();

    protected void loginAsAdmin() {
        String username = PROPERTIES.getProperty(PropertiesNames.DEFAULT_ADMIN_USERNAME_PROPERTY);
        String password = PROPERTIES.getProperty(PropertiesNames.DEFAULT_ADMIN_PASSWORD_PROPERTY);
        loginAs(username, password);
    }

    protected void loginAs(String username, String password) {
        client.logIn(getVicUrl(), username, password);
    }

    protected void logOut() {
        client.main().logOut();
        client.waitToLogout();
    }

    protected DockerClient getDockerClient(String dockerHostUrl) {
        return DockerUtils.createUnsecureDockerClient(dockerHostUrl);
    }

    protected String getVicUrl() {
        return BaseSuite.getVicUrl();
    }

    protected String getVicIp() {
        return BaseSuite.getVicIp();
    }

    protected String getVcenterIp() {
        return BaseSuite.getVcenterIp();
    }

    protected String getVchUrl(String vchIp) {
        return "https://" + vchIp + ":" + PROPERTIES.getProperty(PropertiesNames.VCH_PORT_PROPERTY);
    }

    protected String getDefaultAdminUsername() {
        return BaseSuite.getDefaultAdminUsername();
    }

    protected String getDefaultAdminPassword() {
        return BaseSuite.getDefaultAdminPassword();
    }

    protected String getVicVmUsername() {
        return PROPERTIES.getProperty(PropertiesNames.VIC_VM_USERNAME_PROPERTY);
    }

    protected String getVicVmPassword() {
        return PROPERTIES.getProperty(PropertiesNames.VIC_VM_PASSWORD_PROPERTY);
    }

    @AfterClass
    public static void closeBrowser() {
        close();
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

    protected PublicRepositoriesPageLibrary publicRepositories() {
        return client.publicRepositories();
    }

    protected ProjectRepositoriesPageLibrary projectRepositories() {
        return client.projectRepositories();
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

    protected RegistriesPageLibrary registries() {
        return client.registries();
    }

    protected ConfigurationPageLibrary configuration() {
        return client.configuration();
    }

    protected LogsPageLibrary logs() {
        return client.logs();
    }

    public void sleep(int miliseconds) {
        try {
            Thread.sleep(miliseconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
