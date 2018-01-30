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

package com.vmware.admiral.vic.test.ui.pages;

import static com.codeborne.selenide.Selenide.Wait;
import static com.codeborne.selenide.WebDriverRunner.url;

import java.util.Objects;

import com.vmware.admiral.test.ui.pages.CommonWebClient;
import com.vmware.admiral.vic.test.ui.pages.configuration.ConfigurationPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.hosts.ContainerHostsPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTabLocators;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTabValidator;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTabLocators;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTabValidator;
import com.vmware.admiral.vic.test.ui.pages.projectrepos.ProjectRepositoriesPageLibrary;

public class VICWebClient extends CommonWebClient<VICWebCLientLocators> {

    public VICWebClient() {
        super(null, new VICWebCLientLocators());
    }

    private VICHomeTab homeTab;
    private VICAdministrationTab administrationTab;

    private ContainerHostsPageLibrary clusters;
    private ConfigurationPageLibrary configuration;
    private ProjectRepositoriesPageLibrary projectRepositories;

    @Override
    protected void waitForLandingPage() {
        String landingUrl = url();
        if (landingUrl.endsWith("/applications")) {
            applications().applicationsPage().waitToLoad();
        } else if (landingUrl.endsWith("/project-repositories")) {
            projectRepositories().projectRepositoriesPage().waitToLoad();
        } else {
            Wait().until(d -> pageActions().isDisplayed(locators().loggedUserDiv()));
        }
    }

    @Override
    public VICHomeTab home() {
        if (Objects.isNull(homeTab)) {
            VICHomeTabLocators locators = new VICHomeTabLocators();
            VICHomeTabValidator validator = new VICHomeTabValidator(getFrameLocators(), locators);
            homeTab = new VICHomeTab(getFrameLocators(), validator, locators);
        }
        return homeTab;
    }

    @Override
    public VICAdministrationTab administration() {
        if (Objects.isNull(administrationTab)) {
            VICAdministrationTabLocators locators = new VICAdministrationTabLocators();
            VICAdministrationTabValidator validator = new VICAdministrationTabValidator(
                    getFrameLocators(), locators);
            administrationTab = new VICAdministrationTab(getFrameLocators(), validator, locators);
        }
        return administrationTab;
    }

    @Override
    public ContainerHostsPageLibrary clusters() {
        if (Objects.isNull(clusters)) {
            clusters = new ContainerHostsPageLibrary();
        }
        return clusters;
    }

    public ConfigurationPageLibrary configuration() {
        if (Objects.isNull(configuration)) {
            configuration = new ConfigurationPageLibrary();
        }
        return configuration;
    }

    public ProjectRepositoriesPageLibrary projectRepositories() {
        if (Objects.isNull(projectRepositories)) {
            projectRepositories = new ProjectRepositoriesPageLibrary();
        }
        return projectRepositories;
    }

}
