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

package com.vmware.admiral.test.ui.pages.templates.create;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class CreateTemplatePage
        extends BasicPage<CreateTemplatePageValidator, CreateTemplatePageLocators> {

    public CreateTemplatePage(By[] iFrameLocators, CreateTemplatePageValidator validator,
            CreateTemplatePageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void navigateBack() {
        LOG.info("Navigating back...");
        pageActions().click(locators().backButton());
    }

    public void setName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void clickProceedButton() {
        LOG.info("Clicking proceed...");
        pageActions().click(locators().proceedButton());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForElementToSettle(locators().childPageSlide());
    }

}
