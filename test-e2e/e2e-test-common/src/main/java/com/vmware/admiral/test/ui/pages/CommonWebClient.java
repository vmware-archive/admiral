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

import java.util.Objects;

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
import com.vmware.admiral.test.ui.pages.registries.GlobalRegistriesPageLibrary;
import com.vmware.admiral.test.ui.pages.repositories.RepositoriesPageLibrary;
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

    private final By[] ADMIRAL_INNER_FRAME_LOCATORS = new By[] { By.cssSelector("iframe") };

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

    protected void logInCommon(String username, String password) {
        pageActions().sendKeys(username, locators().loginUsernameInput());
        pageActions().sendKeys(password, locators().loginPasswordInput());
        pageActions().click(locators().loginSubmitButton());
        Wait().until(ExpectedConditions.or(
                d -> {
                    return didLoginSucceed();
                },
                d -> {
                    if (pageActions().isDisplayed(locators().errorResponse())) {
                        throw new AssertionError(pageActions().getText(locators().errorResponse()));
                    }
                    return false;
                }));
        waitForLandingPage();
    }

    protected abstract boolean didLoginSucceed();

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
            HomeTabValidator validator = new HomeTabValidator(admiralTopFrameLocators(), locators);
            home = new HomeTab(admiralTopFrameLocators(), validator, locators);
        }
        return home;
    }

    public AdministrationTab administration() {
        if (Objects.isNull(administration)) {
            AdministrationTabLocators locators = new AdministrationTabLocators();
            AdministrationTabValidator validator = new AdministrationTabValidator(
                    admiralTopFrameLocators(), locators);
            administration = new AdministrationTab(admiralTopFrameLocators(), validator, locators);
        }
        return administration;
    }

    public ApplicationsPageLibrary applications() {
        if (Objects.isNull(applications)) {
            applications = new ApplicationsPageLibrary(admiralInnerFrameLocators());
        }
        return applications;
    }

    public ContainersPageLibrary containers() {
        if (Objects.isNull(containers)) {
            containers = new ContainersPageLibrary(admiralInnerFrameLocators());
        }
        return containers;
    }

    public NetworksPageLibrary networks() {
        if (Objects.isNull(networks)) {
            networks = new NetworksPageLibrary(admiralInnerFrameLocators());
        }
        return networks;
    }

    public ClustersPageLibrary clusters() {
        if (Objects.isNull(clusters)) {
            clusters = new ClustersPageLibrary(admiralTopFrameLocators());
        }
        return clusters;
    }

    public IdentityManagementPageLibrary identity() {
        if (Objects.isNull(identity)) {
            identity = new IdentityManagementPageLibrary(admiralTopFrameLocators(),
                    admiralInnerFrameLocators());
        }
        return identity;
    }

    public LogsPageLibrary logs() {
        if (Objects.isNull(logs)) {
            logs = new LogsPageLibrary(admiralTopFrameLocators());
        }
        return logs;
    }

    public ProjectsPageLibrary projects() {
        if (Objects.isNull(projects)) {
            projects = new ProjectsPageLibrary(admiralTopFrameLocators());
        }
        return projects;
    }

    public RepositoriesPageLibrary repositories() {
        if (Objects.isNull(publicRepositories)) {
            publicRepositories = new RepositoriesPageLibrary(admiralInnerFrameLocators());
        }
        return publicRepositories;
    }

    public GlobalRegistriesPageLibrary registries() {
        if (Objects.isNull(registries)) {
            registries = new GlobalRegistriesPageLibrary(admiralInnerFrameLocators());
        }
        return registries;
    }

    public TemplatesPageLibrary templates() {
        if (Objects.isNull(templates)) {
            templates = new TemplatesPageLibrary(admiralInnerFrameLocators());
        }
        return templates;
    }

    public VolumesPageLibrary volumes() {
        if (Objects.isNull(volumes)) {
            volumes = new VolumesPageLibrary(admiralInnerFrameLocators());
        }
        return volumes;
    }

    protected By[] admiralTopFrameLocators() {
        return null;
    }

    protected By[] admiralInnerFrameLocators() {
        return ADMIRAL_INNER_FRAME_LOCATORS;
    }

}
