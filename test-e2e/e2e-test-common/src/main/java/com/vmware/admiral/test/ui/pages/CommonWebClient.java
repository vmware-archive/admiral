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

package com.vmware.admiral.test.ui.pages;

import static com.codeborne.selenide.Selenide.Wait;
import static com.codeborne.selenide.Selenide.open;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.vmware.admiral.test.ui.pages.applications.ApplicationsPageLibrary;
import com.vmware.admiral.test.ui.pages.clusters.ClustersPageLibrary;
import com.vmware.admiral.test.ui.pages.common.BasicClass;
import com.vmware.admiral.test.ui.pages.containers.ContainersPageLibrary;
import com.vmware.admiral.test.ui.pages.identity.IdentityManagementPageLibrary;
import com.vmware.admiral.test.ui.pages.logs.LogsPageLibrary;
import com.vmware.admiral.test.ui.pages.main.AdministrationTab;
import com.vmware.admiral.test.ui.pages.main.AdministrationTabLocators;
import com.vmware.admiral.test.ui.pages.main.AdministrationTabValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTab;
import com.vmware.admiral.test.ui.pages.main.HomeTabLocators;
import com.vmware.admiral.test.ui.pages.main.HomeTabValidator;
import com.vmware.admiral.test.ui.pages.main.MainPage;
import com.vmware.admiral.test.ui.pages.main.MainPageLocators;
import com.vmware.admiral.test.ui.pages.main.MainPageValidator;
import com.vmware.admiral.test.ui.pages.networks.NetworksPageLibrary;
import com.vmware.admiral.test.ui.pages.projects.ProjectsPageLibrary;
import com.vmware.admiral.test.ui.pages.publicrepos.RepositoriesPageLibrary;
import com.vmware.admiral.test.ui.pages.registries.GlobalRegistriesPageLibrary;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPageLibrary;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPageLibrary;

public abstract class CommonWebClient<L extends CommonWebClientLocators> extends BasicClass<L> {

    public CommonWebClient(By[] iframeLocators, L pageLocators) {
        super(iframeLocators, pageLocators);
    }

    private static int loginTimeout = 60;

    public static int getLoginTimeoutSeconds() {
        return loginTimeout;
    }

    public static void setLoginTimeoutSeconds(int seconds) {
        loginTimeout = seconds;
    }

    private MainPage main;
    private HomeTab home;
    private AdministrationTab administration;

    private ApplicationsPageLibrary applications;
    private ClustersPageLibrary clusters;
    private ContainersPageLibrary containers;
    private NetworksPageLibrary networks;
    private IdentityManagementPageLibrary identity;
    private LogsPageLibrary logs;
    private ProjectsPageLibrary projects;
    private RepositoriesPageLibrary publicRepositories;
    private GlobalRegistriesPageLibrary registries;
    private TemplatesPageLibrary templates;
    private VolumesPageLibrary volumes;

    public void logIn(String url, String username, String password) {
        Objects.requireNonNull(url, "url parameter cannot be null");
        Objects.requireNonNull(username, "username parameter cannot be null");
        open(url);
        LOG.info(String.format("Logging in to [%s] with user: [%s]", url, username));
        pageActions().sendKeys(username, locators().loginUsernameInput());
        pageActions().sendKeys(password, locators().loginPasswordInput());
        pageActions().click(locators().loginSubmitButton());
        Wait().until(ExpectedConditions.or(
                d -> {
                    return element(locators().spinner()).is(Condition.visible);
                },
                d -> {
                    if (pageActions().isDisplayed(locators().errorResponse())) {
                        throw new AssertionError(pageActions().getText(locators().errorResponse()));
                    }
                    return false;
                }));
        Wait().withTimeout(getLoginTimeoutSeconds(), TimeUnit.SECONDS)
                .until(d -> element(locators().spinner()).is(Condition.hidden));
        waitForLandingPage();
    }

    protected abstract void waitForLandingPage();

    public void waitToLogout() {
        element(locators().loginUsernameInput()).shouldBe(Condition.visible);
    }

    public MainPage main() {
        if (Objects.isNull(main)) {
            MainPageLocators locators = new MainPageLocators();
            MainPageValidator validator = new MainPageValidator(null, locators);
            main = new MainPage(null, validator, locators);
        }
        return main;
    }

    public HomeTab home() {
        if (Objects.isNull(home)) {
            HomeTabLocators locators = new HomeTabLocators();
            HomeTabValidator validator = new HomeTabValidator(null, locators);
            home = new HomeTab(null, validator, locators);
        }
        return home;
    }

    public AdministrationTab administration() {
        if (Objects.isNull(administration)) {
            AdministrationTabLocators locators = new AdministrationTabLocators();
            AdministrationTabValidator validator = new AdministrationTabValidator(null, locators);
            administration = new AdministrationTab(null, validator, locators);
        }
        return administration;
    }

    public ApplicationsPageLibrary applications() {
        if (Objects.isNull(applications)) {
            applications = new ApplicationsPageLibrary();
        }
        return applications;
    }

    public ContainersPageLibrary containers() {
        if (Objects.isNull(containers)) {
            containers = new ContainersPageLibrary();
        }
        return containers;
    }

    public NetworksPageLibrary networks() {
        if (Objects.isNull(networks)) {
            networks = new NetworksPageLibrary();
        }
        return networks;
    }

    public ClustersPageLibrary clusters() {
        if (Objects.isNull(clusters)) {
            clusters = new ClustersPageLibrary();
        }
        return clusters;
    }

    public IdentityManagementPageLibrary identity() {
        if (Objects.isNull(identity)) {
            identity = new IdentityManagementPageLibrary();
        }
        return identity;
    }

    public LogsPageLibrary logs() {
        if (Objects.isNull(logs)) {
            logs = new LogsPageLibrary();
        }
        return logs;
    }

    public ProjectsPageLibrary projects() {
        if (Objects.isNull(projects)) {
            projects = new ProjectsPageLibrary();
        }
        return projects;
    }

    public RepositoriesPageLibrary repositories() {
        if (Objects.isNull(publicRepositories)) {
            publicRepositories = new RepositoriesPageLibrary();
        }
        return publicRepositories;
    }

    public GlobalRegistriesPageLibrary registries() {
        if (Objects.isNull(registries)) {
            registries = new GlobalRegistriesPageLibrary();
        }
        return registries;
    }

    public TemplatesPageLibrary templates() {
        if (Objects.isNull(templates)) {
            templates = new TemplatesPageLibrary();
        }
        return templates;
    }

    public VolumesPageLibrary volumes() {
        if (Objects.isNull(volumes)) {
            volumes = new VolumesPageLibrary();
        }
        return volumes;
    }

}
