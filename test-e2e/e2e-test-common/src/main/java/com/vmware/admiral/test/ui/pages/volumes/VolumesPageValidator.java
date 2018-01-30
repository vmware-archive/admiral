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

package com.vmware.admiral.test.ui.pages.volumes;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage.VolumeState;

public class VolumesPageValidator extends PageValidator<VolumesPageLocators> {

    public VolumesPageValidator(By[] iFrameLocators, VolumesPageLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().pageTitle()).shouldHave(Condition.text("Volumes"));
        element(locators().childPageSlide()).shouldNot(Condition.exist);
    }

    public void validateVolumesCount(int count) {
        String countText = pageActions().getText(locators().itemsCount());
        int actualCount = Integer.parseInt(countText.substring(1, countText.length() - 1));
        if (actualCount != count) {
            throw new AssertionError(String.format(
                    "Volumes count mismatch, expected: [%d], actual: [%d]", count, actualCount));
        }
    }

    public void validateVolumeExistsWithName(String namePrefix) {
        element(locators().cardByTitlePrefix(namePrefix)).should(Condition.exist);
    }

    public void validateVolumeDoesNotExistWithName(String namePrefix) {
        element(locators().cardByTitlePrefix(namePrefix)).shouldNot(Condition.exist);
    }

    public void validateVolumeState(String namePrefix, VolumeState state) {
        String actualState = pageActions().getText(locators().cardHeaderByTitlePrefix(namePrefix));
        if (!actualState.contentEquals(state.toString())) {
            throw new AssertionError(String.format(
                    "Volume state mismatch: expected [%s], actual [%s]", state, actualState));
        }
    }

}
