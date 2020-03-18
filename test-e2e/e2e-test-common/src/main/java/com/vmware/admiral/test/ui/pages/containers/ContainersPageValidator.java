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

package com.vmware.admiral.test.ui.pages.containers;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage.ContainerState;

public class ContainersPageValidator extends PageValidator<ContainersPageLocators> {

    public ContainersPageValidator(By[] iFrameLocators, ContainersPageLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().pageTitle()).shouldHave(Condition.text("Containers"));
        element(locators().childPageSlide()).shouldNot(Condition.exist);
    }

    public void validateContainerExistsWithName(String namePrefix) {
        element(locators().cardByTitlePrefix(namePrefix)).should(Condition.exist);
    }

    public void validateContainerDoesNotExistWithName(String namePrefix) {
        element(locators().cardByTitlePrefix(namePrefix)).shouldNot(Condition.exist);
    }

    public void validateContainersCount(int expectedCount) {
        String countText = pageActions().getText(locators().itemsCount());
        int actualCount = Integer.parseInt(countText.substring(1, countText.length() - 1));
        if (actualCount != expectedCount) {
            throw new AssertionError(String.format(
                    "Containers count mismatch, expected: [%d], actual: [%d]", expectedCount,
                    actualCount));
        }
    }

    public void validateContainerState(String namePrefix, ContainerState state) {
        String actualState = pageActions().getText(locators().cardHeaderByTitlePrefix(namePrefix));
        boolean match = false;
        if (state == ContainerState.RUNNING) {
            match = actualState.startsWith(state.toString());
        } else {
            match = actualState.contentEquals(state.toString());
        }
        if (!match) {
            throw new AssertionError(String.format(
                    "Container state mismatch: expected [%s], actual [%s]", state, actualState));
        }
    }
}
