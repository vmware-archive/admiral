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

package com.vmware.admiral.test.ui.pages.hosts;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class ContainerHostsPageValidator extends PageValidator {

    private final By PAGE_TITLE = By.cssSelector(".title>div");
    private final By ALL_HOST_CARDS = By.cssSelector(".items .card-item");

    private ContainerHostsPage page;

    ContainerHostsPageValidator(ContainerHostsPage page) {
        this.page = page;
    }

    @Override
    public ContainerHostsPageValidator validateIsCurrentPage() {
        $(HomeTabSelectors.CONTAINER_HOSTS_BUTTON).shouldHave(Condition.cssClass("active"));
        $(PAGE_TITLE).shouldHave(Condition.text("Container Hosts"));
        return this;
    }

    public ContainerHostsPageValidator validateHostExistsWithName(String name) {
        $(page.getHostCardSelector(name)).should(Condition.exist);
        return this;
    }

    public ContainerHostsPageValidator validateHostDoesNotExistWithName(String name) {
        $(page.getHostCardSelector(name)).shouldNot(Condition.exist);
        return this;
    }

    public ContainerHostsPageValidator validateHostsCount(int expectedCount) {
        int actualCount = $$(ALL_HOST_CARDS).size();
        if (actualCount != expectedCount) {
            throw new AssertionError(
                    String.format("Hosts count mismatch, expected: [%d], actual: [%d]",
                            expectedCount, actualCount));
        }
        return this;
    }
}
