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

package com.vmware.admiral.test.ui.pages.containers;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class ContainersPageValidator extends PageValidator {

    private ContainersPage page;

    ContainersPageValidator(ContainersPage page) {
        this.page = page;
    }

    private final By PAGE_TITLE = By.cssSelector(".title>span:nth-child(1)");
    private final By CREATE_CONTAINER_SLIDE = By
            .cssSelector(".create-container.closable-view.slide-and-fade-transition");
    private final By ITEMS_COUNT_FIELD = By.cssSelector(".title .total-items");

    @Override
    public ContainersPageValidator validateIsCurrentPage() {
        $(HomeTabSelectors.CONTAINERS_BUTTON).shouldHave(Condition.cssClass("active"));
        executeInFrame(0, () -> {
            $(PAGE_TITLE).shouldHave(Condition.text("Containers"));
            $(CREATE_CONTAINER_SLIDE).shouldNot(Condition.exist);
        });
        return this;
    }

    public ContainersPageValidator validateContainerExistsWithName(String name) {
        executeInFrame(0, () -> $(page.getContainerCardSelector(name)).should(Condition.exist));
        return this;
    }

    public ContainersPageValidator validateContainerDoesNotExistWithName(String name) {
        executeInFrame(0, () -> $(page.getContainerCardSelector(name)).shouldNot(Condition.exist));
        return this;
    }

    public ContainersPageValidator validateContainersCount(int expectedCount) {
        String countText = executeInFrame(0, () -> {
            return $(ITEMS_COUNT_FIELD).getText();
        });
        int actualCount = Integer.parseInt(countText.substring(1, countText.length() - 1));
        if (actualCount != expectedCount) {
            throw new AssertionError(String.format(
                    "Containers count mismatch, expected: [%d], actual: [%d]", expectedCount,
                    actualCount));
        }
        return this;
    }

}
