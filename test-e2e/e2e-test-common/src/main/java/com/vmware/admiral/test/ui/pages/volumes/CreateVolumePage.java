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

import static com.codeborne.selenide.Selenide.Wait;

import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

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

    public void setExistingName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        pageActions().clear(locators().existingNameInput());
        pageActions().sendKeys(name, locators().existingNameInput());
    }

    public void setDriver(String driver) {
        LOG.info(String.format("Setting driver: [%s]", driver));
        pageActions().clear(locators().driverInput());
        pageActions().sendKeys(driver, locators().driverInput());
    }

    public void toggleAdvancedOptions(boolean toggle) {
        LOG.info("Clicking the Advanced checkbox");
        pageActions().setCheckbox(toggle, locators().advancedCheckbox());
    }

    public void addDriverOption(String option, String value) {
        LOG.info(String.format("Adding driver option [%s] with value [%s]", option, value));
        if (!pageActions().getAttribute("value", locators().lastDriverOptionsKeyInput())
                .isEmpty()) {
            pageActions().click(locators().addDriverOptionsInputButton());
        }
        pageActions().sendKeys(option, locators().lastDriverOptionsKeyInput());
        pageActions().sendKeys(value, locators().lastDriverOptionsValueInput());
    }

    public void toggleExistingCheckbox(boolean toggle) {
        pageActions().setCheckbox(toggle, locators().existingCheckbox());
    }

    public void selectHostByName(String hostName) {
        LOG.info(String.format("Setting host by name: [%s]", hostName));
        pageActions().click(locators().selectHostDropdown());
        int retries = 3;
        By host = locators().hostSelectorByName(hostName);
        // sometimes clicking the host fails so we retry
        while (retries > 0) {
            try {
                pageActions().click(host);
                Wait().withTimeout(2, TimeUnit.SECONDS)
                        .until(d -> element(host).is(Condition.hidden));
                break;
            } catch (TimeoutException e) {
                if (--retries <= 0) {
                    throw new RuntimeException("Could not select host for volume");
                }
                LOG.info("Selecting the host failed, retrying...");
            }
        }
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
