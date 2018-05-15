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

package com.vmware.admiral.test.ui.pages.projects.configure;

import static com.codeborne.selenide.Selenide.Wait;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class ConfigureProjectPage
        extends BasicPage<ConfigureProjectPageValidator, ConfigureProjectPageLocators> {

    public ConfigureProjectPage(By[] iFrameLocators, ConfigureProjectPageValidator validator,
            ConfigureProjectPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickMembersTabButton() {
        LOG.info("Clicking on members tab");
        pageActions().click(locators().membersTabButton());
    }

    public void clickProjectRegistriesTabButton() {
        LOG.info("Clicking on members tab");
        pageActions().click(locators().projectRegistriesTabButton());
    }

    public void navigateBack() {
        LOG.info("Navigating back...");
        pageActions().click(locators().backButton());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForElementToSettle(locators().pageTitle());
        Wait().until(d -> !pageActions().getText(locators().pageTitle()).isEmpty());
    }

}
