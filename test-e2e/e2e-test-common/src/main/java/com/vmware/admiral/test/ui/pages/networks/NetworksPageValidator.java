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

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage.NetworkState;

public class NetworksPageValidator extends PageValidator<NetworksPageLocators> {

    public NetworksPageValidator(By[] iFrameLocators, NetworksPageLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().pageTitle()).shouldHave(Condition.text("Networks"));
        element(locators().childPageSlide()).shouldNot(Condition.exist);
    }

    public void validateNetworkExistsWithName(String namePrefix) {
        element(locators().cardByTitlePrefix(namePrefix)).should(Condition.exist);
    }

    public void validateNetworkDoesNotExist(String namePrefix) {
        element(locators().cardByTitlePrefix(namePrefix)).shouldNot(Condition.exist);
    }

    public void validateNetworkCannotBeDeleted(String namePrefix) {
        By card = locators().cardByTitlePrefix(namePrefix);
        waitForElementToSettle(card);
        pageActions().hover(card);
        pageActions().click(locators().cardDeleteButtonByTitlePrefix(namePrefix));
        element(locators().deleteNetworkErrorMessageByTitlePrefix(namePrefix))
                .should(Condition.appear)
                .shouldHave(Condition.text("There are connected containers."))
                .should(Condition.disappear);
    }

    public void validateNetworkState(String namePrefix, NetworkState state) {
        String actualState = pageActions().getText(locators().cardHeaderByTitlePrefix(namePrefix));
        if (!actualState.contentEquals(state.toString())) {
            throw new AssertionError(String.format(
                    "Network state mismatch: expected [%s], actual [%s]", state, actualState));
        }
    }

    public void validateNetworksCount(int count) {
        String countText = pageActions().getText(locators().itemsCount());
        int actualCount = Integer.parseInt(countText.substring(1, countText.length() - 1));
        if (actualCount != count) {
            throw new AssertionError(String.format(
                    "Networks count mismatch, expected: [%d], actual: [%d]", count, actualCount));
        }
    }

}
