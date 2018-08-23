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

package com.vmware.admiral.test.ui.pages.registries;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicClass;

public class AddGlobalRegistryForm extends BasicClass<AddGlobalRegistryFormLocators> {

    public AddGlobalRegistryForm(By[] iframeLocators, AddGlobalRegistryFormLocators pageLocators) {
        super(iframeLocators, pageLocators);
    }

    public void setAddress(String address) {
        LOG.info(String.format("Setting address [%s]", address));
        int retries = 5;
        while (retries > 0) {
            pageActions().clear(locators().addressInput());
            pageActions().sendKeys(address, locators().addressInput());
            if (pageActions().getAttribute("value", locators().addressInput()).equals(address)) {
                return;
            }
            LOG.warning("Setting address failed, retrying...");
            retries--;
        }
        throw new RuntimeException(
                String.format("Could not set registry address after %d retries", retries));
    }

    public void setName(String name) {
        LOG.info(String.format("Setting name [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void clickSaveButton() {
        LOG.info("Submitting");
        pageActions().click(locators().saveButton());
    }

    public void clickVerifyButton() {
        LOG.info("Verifying");
        pageActions().click(locators().verifyButton());
    }

    public void clickCancelButton() {
        LOG.info("Cancelling");
        pageActions().click(locators().cancelButton());
    }

}
