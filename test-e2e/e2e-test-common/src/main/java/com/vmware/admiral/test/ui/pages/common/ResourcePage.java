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

package com.vmware.admiral.test.ui.pages.common;

import static com.codeborne.selenide.Selenide.Wait;

import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

public abstract class ResourcePage<V extends PageValidator<L>, L extends ResourcePageLocators>
        extends BasicPage<V, L> {

    public ResourcePage(By[] iFrameLocators, V validator, L pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    protected void deleteItemByTitlePrefix(String namePrefix) {
        By card = locators().cardByTitlePrefix(namePrefix);
        waitForElementToSettle(card);
        // Sometimes clicking the trash icon fails so we retry
        int retries = 3;
        while (retries >= 0) {
            pageActions().hover(card);
            pageActions().click(locators().cardDeleteButtonByTitlePrefix(namePrefix));
            try {
                By confirmButton = locators().cardDeleteConfirmButtonByTitlePrefix(namePrefix);
                Wait().withTimeout(5, TimeUnit.SECONDS)
                        .until(d -> pageActions().isDisplayed(confirmButton));
                pageActions().click(confirmButton);
                break;
            } catch (TimeoutException e) {
                if (--retries == 0) {
                    throw new RuntimeException(String.format(
                            "Could not delete item with with title prefix: [%s]",
                            namePrefix));
                }
            }
        }
    }

    protected void deleteItemByExactTitle(String title) {
        By card = locators().cardByExactTitle(title);
        waitForElementToSettle(card);
        // Sometimes clicking the trash icon fails so we retry
        int retries = 3;
        while (retries >= 0) {
            pageActions().hover(card);
            pageActions().click(locators().cardDeleteButtonByExactTitle(title));
            try {
                By confirmButton = locators().cardDeleteConfirmButtonByExactTitle(title);
                Wait().withTimeout(5, TimeUnit.SECONDS)
                        .until(d -> pageActions().isDisplayed(confirmButton));
                pageActions().click(confirmButton);
                break;
            } catch (TimeoutException e) {
                if (--retries == 0) {
                    throw new RuntimeException(String.format(
                            "Could not delete item with with title: [%s]",
                            title));
                }
            }
        }
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForSpinner();
    }

    public void refresh() {
        LOG.info("Refreshing...");
        pageActions().click(locators().refreshButton());
        waitForSpinner();
    }

}
