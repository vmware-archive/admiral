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

package com.vmware.admiral.test.ui.pages.clusters;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;
import com.vmware.admiral.test.util.HostType;

public class AddClusterPage extends BasicPage<AddClusterPageValidator, AddClusterPageLocators> {

    public AddClusterPage(By[] iFrameLocators, AddClusterPageValidator validator,
            AddClusterPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

    public void setName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void setDescription(String description) {
        LOG.info(String.format("Setting description: [%s]", description));
        pageActions().sendKeys(description, locators().descriptionInput());
    }

    public void setHostType(HostType hostType) {
        LOG.info(String.format("Setting host type: [%s]", hostType));
        pageActions().selectOptionByValue(hostType.toString(), locators().hostTypeSelect());
    }

    public void setUrl(String url) {
        LOG.info(String.format("Setting url: [%s]", url));
        pageActions().sendKeys(url, locators().urlInput());
    }

    public void selectCredentials(String credentialsName) {
        LOG.info(String.format("Setting credentials: [%s]", credentialsName));
        pageActions().click(locators().credentialsSelectDropdown());
        pageActions().selectOptionByText(credentialsName, locators().credentialsSelectDropdown());
    }

    public void submit() {
        LOG.info("Submitting...");
        pageActions().click(locators().saveButton());
    }

}
