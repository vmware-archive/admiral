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

package com.vmware.admiral.test.ui.pages.main;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class AdministrationTab
        extends BasicPage<AdministrationTabValidator, AdministrationTabLocators> {

    public AdministrationTab(By[] iFrameLocators, AdministrationTabValidator validator,
            AdministrationTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickProjectsButton() {
        LOG.info("Navigating to Projects page");
        pageActions().click(locators().projectsButton());
    }

    public void clickIdentityManagementButton() {
        LOG.info("Navigating to Identity Management page");
        pageActions().click(locators().identityManagementButton());
    }

    public void clickRegistriesButton() {
        LOG.info("Navigating to Registries page");
        pageActions().click(locators().registriesButton());
    }

    public void clickLogsButton() {
        LOG.info("Navigating to Logs page");
        pageActions().click(locators().logsButton());
    }

    @Override
    public void waitToLoad() {
        // TODO wait to load
    }
}
