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

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

public abstract class ResourcePage<V extends PageValidator<L>, L extends ResourcePageLocators>
        extends BasicPage<V, L> {

    public ResourcePage(By[] iFrameLocators, V validator, L pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    protected void deleteItemByExactTitle(String title) {
        deleteItem(locators().cardByExactTitle(title),
                locators().cardDeleteButtonByExactTitle(title),
                locators().cardDeleteConfirmationHolderByExactTitle(title),
                locators().cardDeleteConfirmButtonByExactTitle(title), String.format(
                        "Could not delete item with with title: [%s]",
                        title));
    }

    protected void deleteItemByTitlePrefix(String titlePrefix) {
        deleteItem(locators().cardByTitlePrefix(titlePrefix),
                locators().cardDeleteButtonByTitlePrefix(titlePrefix),
                locators().cardDeleteConfirmationHolderByTitlePrefix(titlePrefix),
                locators().cardDeleteConfirmButtonByTitlePrefix(titlePrefix), String.format(
                        "Could not delete item with with title prefix: [%s]",
                        titlePrefix));
    }

    private void deleteItem(By card, By cardDeleteButton, By deleteConfirmHolder, By deleteConfirm,
            String errorMessage) {
        waitForElementToSettle(card);
        // Sometimes clicking the trash icon fails so we retry
        int retries = 3;
        do {
            try {
                pageActions().hover(locators().pageTitle());
                pageActions().hover(card);
                pageActions().click(cardDeleteButton);
                Wait().withTimeout(5, TimeUnit.SECONDS)
                        .until(d -> !element(deleteConfirmHolder).has(Condition.cssClass("hide")));
                pageActions().click(deleteConfirm);
                return;
            } catch (Throwable e) {
                retries--;
            }
        } while (retries > 0);
        throw new RuntimeException(errorMessage);
    }

    public void expandRequestsToolbar() {
        pageActions().click(locators().requestsButton());
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
