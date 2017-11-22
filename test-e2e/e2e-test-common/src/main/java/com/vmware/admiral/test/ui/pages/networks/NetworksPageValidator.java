/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.networks;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class NetworksPageValidator extends PageValidator {

    private final By PAGE_TITLE = By.cssSelector(".list-holder .title>span:nth-child(1)");
    private final By ITEMS_COUNT = By.cssSelector(".title .total-items");
    private final By DELETE_NETWORK_ERROR_MESSAGE = By
            .cssSelector(".alert.alert-warning.alert-dismissible");

    private NetworksPage page;

    public NetworksPageValidator(NetworksPage page) {
        this.page = page;
    }

    @Override
    public NetworksPageValidator validateIsCurrentPage() {
        $(HomeTabSelectors.NETWORKS_BUTTON).shouldHave(Condition.cssClass("active"));
        executeInFrame(0, () -> {
            $(PAGE_TITLE).shouldHave(Condition.text("Networks"));
            $(HomeTabSelectors.CHILD_PAGE_SLIDE).shouldNotBe(Condition.exist);
        });
        return this;
    }

    public NetworksPageValidator validateNetworkExistsWithName(String namePrefix) {
        executeInFrame(0, () -> $(page.getNetworkCardSelector(namePrefix)).should(Condition.exist));
        return this;
    }

    public NetworksPageValidator validateNetworkDoesNotExist(String namePrefix) {
        executeInFrame(0,
                () -> $(page.getNetworkCardSelector(namePrefix)).shouldNot(Condition.exist));
        return this;
    }

    public NetworksPageValidator validateNetworkCannotBeDeleted(String namePrefix) {
        executeInFrame(0, () -> {
            SelenideElement card = waitForElementToStopMoving(
                    page.getNetworkCardSelector(namePrefix));
            actions().moveToElement(card)
                    .click(card.$(page.CARD_RELATIVE_DELETE_BUTTON))
                    .build()
                    .perform();
            card.$(DELETE_NETWORK_ERROR_MESSAGE)
                    .should(Condition.appear)
                    .shouldHave(Condition.text("There are connected containers."))
                    .should(Condition.disappear);
        });
        return this;
    }

    public NetworksPageValidator validateNetworksCount(int count) {
        String countText = executeInFrame(0, () -> {
            return $(ITEMS_COUNT).getText();
        });
        int actualCount = Integer.parseInt(countText.substring(1, countText.length() - 1));
        if (actualCount != count) {
            throw new AssertionError(String.format(
                    "Networks count mismatch, expected: [%d], actual: [%d]", count, actualCount));
        }
        return this;
    }

}
