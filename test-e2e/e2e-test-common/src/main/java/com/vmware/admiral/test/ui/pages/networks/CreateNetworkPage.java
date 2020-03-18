/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.networks;

import static com.codeborne.selenide.Selenide.Wait;

import java.time.Duration;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class CreateNetworkPage
        extends BasicPage<CreateNetworkPageValidator, CreateNetworkPageLocators> {

    public CreateNetworkPage(By[] iFrameLocators, CreateNetworkPageValidator validator,
            CreateNetworkPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void setName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void addHostByName(String hostName) {
        LOG.info(String.format("Adding host by name: [%s]", hostName));
        if (!pageActions().getText(locators().lastHostDropdown())
                .contains("Select from the host list")) {
            pageActions().click(locators().addHostButton());
        }
        pageActions().click(locators().lastHostDropdown());
        int retries = 3;
        By host = locators().hostSelectorByName(hostName);
        // sometimes clicking the host fails so we retry
        while (retries > 0) {
            try {
                pageActions().click(host);
                Wait().withTimeout(Duration.ofSeconds(2))
                        .until(d -> element(host).is(Condition.hidden));
                break;
            } catch (TimeoutException e) {
                if (--retries <= 0) {
                    throw new RuntimeException("Could not select host for network");
                }
                LOG.info("Selecting the host failed, retrying...");
            }
        }
    }

    public void clickBackButton() {
        LOG.info("Clicking back button");
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
