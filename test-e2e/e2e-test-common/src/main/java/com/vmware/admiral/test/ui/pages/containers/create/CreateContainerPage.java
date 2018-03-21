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

public class CreateContainerPage
        extends BasicPage<CreateContainerPageValidator, CreateContainerPageLocators> {

    public CreateContainerPage(By[] iFrameLocators, CreateContainerPageValidator validator,
            CreateContainerPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickBasicTab() {
        LOG.info("Navigating to Basic tab");
        pageActions().click(locators().basicTabButton());
    }

    public void clickNetworkTab() {
        LOG.info("Navigating to Network tab");
        pageActions().click(locators().networkTabButton());
    }

    public void clickStorageTab() {
        LOG.info("Navigating to Storage tab");
        pageActions().click(locators().storageTabButton());
    }

    public void clickPolicyTab() {
        LOG.info("Navigating to Policy tab");
        pageActions().click(locators().policyTabButton());
    }

    public void clickEnvironmentTab() {
        LOG.info("Navigating to Environment tab");
        pageActions().click(locators().environmentTabButton());
    }

    public void clickHealthConfigTab() {
        LOG.info("Navigating to Health Config tab");
        pageActions().click(locators().healthConfigTabButton());
    }

    public void clickLogConfigTab() {
        LOG.info("Navigating to Log Config tab");
        pageActions().click(locators().logConfigTabButton());
    }

    public void navigateBack() {
        LOG.info("Navigating back...");
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
