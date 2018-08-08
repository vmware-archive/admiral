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
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.WebDriverRunner.url;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;

import com.vmware.admiral.test.ui.pages.CommonWebClient;
import com.vmware.admiral.vic.test.ui.pages.configuration.ConfigurationPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.hosts.ContainerHostsPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.internalrepos.BuiltInRepositoriesPageLibrary;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTabLocators;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTabValidator;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTabLocators;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTabValidator;

public class VICWebClient extends CommonWebClient<VICWebCLientLocators> {

    public VICWebClient() {
        super(null, new VICWebCLientLocators());
    }

    private VICHomeTab homeTab;
    private VICAdministrationTab administrationTab;

    private ContainerHostsPageLibrary clusters;
    private ConfigurationPageLibrary configuration;
    private BuiltInRepositoriesPageLibrary builtInRepositories;

    public void logIn(String target, String username, String password) {
        Objects.requireNonNull(target, "'target' parameter must not be null");
        Objects.requireNonNull(username, "'username' parameter must not be null");
        Objects.requireNonNull(password, "'password' parameter must not be null");
        LOG.info(String.format("Logging in to [%s] with user [%s]", target, username));
        open(target);
        logInCommon(username, password);
    }

    @Override
    protected void waitForLandingPage() {
        Wait().withTimeout(getLoginTimeoutSeconds(), TimeUnit.SECONDS)
                .until(d -> element(locators().spinner()).is(Condition.hidden));
        String landingUrl = url();
        if (landingUrl.endsWith("/applications")) {
            applications().applicationsPage().waitToLoad();
        } else if (landingUrl.endsWith("/project-repositories")) {
            builtInRepositories().builtInRepositoriesCardPage().waitToLoad();
        } else {
            Wait().until(d -> pageActions().isDisplayed(locators().loggedUserDiv()));
        }
    }

    @Override
    protected boolean didLoginSucceed() {
        return element(locators().loggedUserDiv()).is(Condition.visible);
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
            clusters = new ContainerHostsPageLibrary(admiralTopFrameLocators());
        }
        return clusters;
    }

    public ConfigurationPageLibrary configuration() {
        if (Objects.isNull(configuration)) {
            configuration = new ConfigurationPageLibrary(admiralTopFrameLocators());
        }
        return configuration;
    }

    public BuiltInRepositoriesPageLibrary builtInRepositories() {
        if (Objects.isNull(builtInRepositories)) {
            builtInRepositories = new BuiltInRepositoriesPageLibrary(admiralTopFrameLocators());
        }
        return builtInRepositories;
    }

}
