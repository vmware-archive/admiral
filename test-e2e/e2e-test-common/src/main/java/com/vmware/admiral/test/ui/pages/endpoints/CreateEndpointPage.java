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

package com.vmware.admiral.test.ui.pages.endpoints;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class CreateEndpointPage
        extends BasicPage<CreateEndpointPageValidator, CreateEndpointPageLocators> {

    public CreateEndpointPage(By[] iFrameLocators, CreateEndpointPageValidator validator,
            CreateEndpointPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void setName(String name) {
        pageActions().clear(locators().endpointNameInput());
        pageActions().sendKeys(name, locators().endpointNameInput());
    }

    public void setDescription(String description) {
        pageActions().clear(locators().endpointDescriptionInput());
        pageActions().sendKeys(description, locators().endpointDescriptionInput());
    }

    public void setUaaAddress(String address) {
        pageActions().clear(locators().uaaAddressInput());
        pageActions().sendKeys(address, locators().uaaAddressInput());
    }

    public void chooseCredentialsByName(String name) {
        pageActions().click(locators().credentialsDropdownButton());
        pageActions().click(locators().credentialsByName(name));
    }

    public void setPksAddress(String address) {
        pageActions().clear(locators().pksAddressInput());
        pageActions().sendKeys(address, locators().pksAddressInput());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

}
