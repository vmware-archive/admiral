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

package com.vmware.admiral.test.ui.pages.volumes;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class VolumesPageValidator extends PageValidator {

    private final By PAGE_TITLE = By.cssSelector(".title>span:nth-child(1)");
    private final By CREATE_VOLUME_SLIDE = By
            .cssSelector(".create-volume.closable-view.slide-and-fade-transition");
    private final By ITEMS_COUNT = By.cssSelector(".title .total-items");

    private VolumesPage page;

    public VolumesPageValidator(VolumesPage page) {
        this.page = page;
    }

    @Override
    public VolumesPageValidator validateIsCurrentPage() {
        $(HomeTabSelectors.VOLUMES_BUTTON).shouldHave(Condition.cssClass("active"));
        executeInFrame(0, () -> {
            $(PAGE_TITLE).shouldHave(Condition.text("Volumes"));
            $(CREATE_VOLUME_SLIDE).shouldNot(Condition.exist);
        });
        return this;
    }

    public VolumesPageValidator validateVolumesCount(int count) {
        String countText = executeInFrame(0, () -> {
            return $(ITEMS_COUNT).getText();
        });
        int actualCount = Integer.parseInt(countText.substring(1, countText.length() - 1));
        if (actualCount != count) {
            throw new AssertionError(String.format(
                    "Volumes count mismatch, expected: [%d], actual: [%d]", count, actualCount));
        }
        return this;
    }

    public VolumesPageValidator validateVolumeExistsWithName(String name) {
        executeInFrame(0, () -> page.getVolumeCard(name).should(Condition.exist));
        return this;
    }

    public VolumesPageValidator validateVolumeDoesNotExistWithName(String name) {
        executeInFrame(0, () -> page.getVolumeCard(name).shouldNot(Condition.exist));
        return this;
    }

}
