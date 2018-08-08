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

package com.vmware.admiral.test.ui.pages.projects.configure.registries;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class AddProjectRegistryForm
        extends BasicPage<AddProjectRegistryFormValidator, AddProjectRegistryFormLocators> {

    public AddProjectRegistryForm(By[] iFrameLocators, AddProjectRegistryFormValidator validator,
            AddProjectRegistryFormLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

    public void setAddress(String address) {
        LOG.info(String.format("Setting address [%s]", address));
        pageActions().clear(locators().addressInput());
        pageActions().sendKeys(address, locators().addressInput());
    }

    public void setName(String name) {
        LOG.info(String.format("Setting name [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void submit() {
        LOG.info("Submitting");
        pageActions().click(locators().saveButton());
    }

}
