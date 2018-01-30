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

package com.vmware.admiral.test.ui.pages.templates;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class TemplatesPageValidator extends PageValidator<TemplatesPageLocators> {

    public TemplatesPageValidator(By[] iFrameLocators, TemplatesPageLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().pageTitle()).shouldHave(Condition.exactText("Templates"));
        element(locators().childPageSlide()).shouldNot(Condition.exist);
    }

    public void validateTemplateExistsWithName(String name) {
        element(locators().cardByExactTitle(name)).should(Condition.exist);
    }

    public void validateTemplateDoesNotExistWithName(String name) {
        element(locators().cardByExactTitle(name)).shouldNot(Condition.exist);
    }

    public void validateTemplatesCount(int count) {
        String countText = pageActions().getText(locators().itemsCount());
        int actualCount = Integer.parseInt(countText.substring(1, countText.length() - 1));
        if (actualCount != count) {
            throw new AssertionError(String.format(
                    "Templates count mismatch, expected: [%d], actual: [%d]", count, actualCount));
        }
    }

}
