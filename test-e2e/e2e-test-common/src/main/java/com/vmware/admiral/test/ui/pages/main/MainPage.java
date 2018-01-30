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

public class MainPage extends BasicPage<MainPageValidator, MainPageLocators> {

    public MainPage(By[] iFrameLocators, MainPageValidator validator,
            MainPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    @Override
    public void waitToLoad() {
    }

    public void logOut() {
        LOG.info("Logging out...");
        pageActions().click(locators().loggedUserDiv());
        pageActions().click(locators().logoutButton());
    }

    public void clickAdministrationTabButton() {
        LOG.info("Navigating to Administration view");
        pageActions().click(locators().administrationTabLocators.administrationTabButton());
    }

    public void clickHomeTabButton() {
        LOG.info("Navigating to Home view");
        pageActions().click(locators().homeTabLocators.homeButton());
    }

}
