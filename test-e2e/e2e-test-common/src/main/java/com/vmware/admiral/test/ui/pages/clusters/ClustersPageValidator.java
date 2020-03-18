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

package com.vmware.admiral.test.ui.pages.clusters;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class ClustersPageValidator extends PageValidator<ClustersPageLocators> {

    public ClustersPageValidator(By[] iFrameLocators, ClustersPageLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().pageTitle()).shouldHave(Condition.text("Clusters"));
    }

    public void validateHostExistsWithName(String name) {
        element(locators().clusterCardByName(name)).should(Condition.exist);
    }

    public void validateHostDoesNotExistWithName(String name) {
        element(locators().clusterCardByName(name)).shouldNot(Condition.exist);
    }

    public void validateAddHostButtonNotAvailable() {
        element(locators().addClusterButton()).shouldNotBe(Condition.visible);
    }

    public void validateAddHostButtonIsAvailable() {
        element(locators().addClusterButton()).shouldBe(Condition.visible);
    }

    public void validateHostActionsAvailable(String hostName) {
        element(locators().clusterDetailsButton(hostName)).shouldBe(Condition.visible);
        element(locators().clusterDeleteButtonByName(hostName)).shouldBe(Condition.visible);
    }

    public void validateHostActionsNotAvailable(String hostName) {
        element(locators().clusterDetailsButton(hostName)).shouldNotBe(Condition.visible);
        element(locators().clusterDeleteButtonByName(hostName)).shouldNotBe(Condition.visible);
    }

    public void validateHostsCount(int expectedCount) {
        int actualCount = pageActions().getElementCount(locators().allClusterCards());
        if (actualCount != expectedCount) {
            throw new AssertionError(
                    String.format("Hosts count mismatch, expected: [%d], actual: [%d]",
                            expectedCount, actualCount));
        }
    }

}
