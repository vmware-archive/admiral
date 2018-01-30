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

package com.vmware.admiral.test.ui.pages.volumes;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class CreateVolumePage
        extends BasicPage<CreateVolumePageValidator, CreateVolumePageLocators> {

    public CreateVolumePage(By[] iFrameLocators, CreateVolumePageValidator validator,
            CreateVolumePageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void setName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void setDriver(String driver) {
        LOG.info(String.format("Setting driver: [%s]", driver));
        pageActions().clear(locators().driverInput());
        pageActions().sendKeys(driver, locators().driverInput());
    }

    public void selectHostByName(String hostName) {
        LOG.info(String.format("Setting host by name: [%s]", hostName));
        pageActions().click(locators().selectHostDropdown());
        pageActions().click(locators().hostSelectorByName(hostName));
    }

    public void navigateBack() {
        LOG.info("Navigating back");
        pageActions().click(locators().backButton());
    }

    public void submit() {
        LOG.info("Submitting...");
        pageActions().click(locators().submitButton());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForElementToSettle(locators().childPageSlide());
    }
}
