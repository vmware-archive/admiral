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

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class SourceRegistriesTab
        extends BasicPage<SourceRegistriesTabValidator, SourceRegistriesTabLocators> {

    public SourceRegistriesTab(By[] iFrameLocators, SourceRegistriesTabValidator validator,
            SourceRegistriesTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickAddRegistryButton() {
        LOG.info("Adding registry");
        pageActions().click(locators().addRegistryButton());
    }

    public void disableRegistryByAddress(String address) {
        LOG.info(String.format("Disabling registry with address [%s]", address));
        pageActions().hover(locators().registryRowByAddress(address));
        pageActions().click(locators().registryDisableButtonByAddress(address));
    }

    public void enableRegistryByAddress(String address) {
        LOG.info(String.format("Enabling registry with address [%s]", address));
        pageActions().hover(locators().registryRowByAddress(address));
        pageActions().click(locators().registryEnableButtonByAddress(address));
    }

    public void editRegistryByAddress(String address) {
        LOG.info(String.format("Enabling registry with address [%s]", address));
        pageActions().hover(locators().registryRowByAddress(address));
        pageActions().click(locators().registryEditButtonByAddress(address));
    }

    public void deleteRegistryByAddress(String address) {
        LOG.info(String.format("Deleting registry with address [%s]", address));
        pageActions().hover(locators().registryRowByAddress(address));
        pageActions().click(locators().registryDeleteButtonByAddress(address));
        pageActions().click(locators().registryDeleteConfirmationButtonByAddress(address));
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        element(locators().addRegistryButton()).shouldNotBe(Condition.visible);
    }

}
