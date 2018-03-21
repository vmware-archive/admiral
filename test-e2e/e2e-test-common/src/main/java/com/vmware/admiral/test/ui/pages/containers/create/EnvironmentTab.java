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

package com.vmware.admiral.test.ui.pages.containers.create;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class EnvironmentTab extends BasicPage<EnvironmentTabValidator, EnvironmentTabLocators> {

    public EnvironmentTab(By[] iFrameLocators, EnvironmentTabValidator validator,
            EnvironmentTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void addEnvironmentVariable(String name, String value) {
        LOG.info(String.format("Adding environment variable with name [%s] and value [%s]", name,
                value));
        if (!pageActions().getAttribute("value", locators().lastVariableNameInput()).trim()
                .isEmpty()) {
            pageActions().click(locators().addVariableRowButton());
        }
        pageActions().sendKeys(name, locators().lastVariableNameInput());
        pageActions().sendKeys(value, locators().lastVariableValueInput());
    }

    public void addCustomProperty(String name, String value) {
        LOG.info(String.format("Adding custom property with name [%s] and value [%s]", name,
                value));
        if (!pageActions().getAttribute("value", locators().lastPropertyNameInput()).trim()
                .isEmpty()) {
            pageActions().click(locators().addPropertiesRowButton());
        }
        pageActions().sendKeys(name, locators().lastPropertyNameInput());
        pageActions().sendKeys(value, locators().lastPropertyValueInput());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

}
