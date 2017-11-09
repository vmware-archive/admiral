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

package com.vmware.admiral.test.ui.pages.applications;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class ApplicationsPageValidator extends PageValidator {

    private final By PAGE_TITLE = By.cssSelector(".title>span:nth-child(1)");
    private final By CREATE_APPLICATION_SLIDE = By
            .cssSelector(".closable-view.slide-and-fade-transition");

    private ApplicationsPage page;

    ApplicationsPageValidator(ApplicationsPage page) {
        this.page = page;
    }

    @Override
    public ApplicationsPageValidator validateIsCurrentPage() {
        $(HomeTabSelectors.APPLICATIONS_BUTTON).shouldHave(Condition.cssClass("active"));
        executeInFrame(0, () -> {
            $(PAGE_TITLE).shouldHave(Condition.text("Applications"));
            $(CREATE_APPLICATION_SLIDE).shouldNot(Condition.exist);
        });
        return this;
    }

    public ApplicationsPageValidator validateApplicationExistsWithName(String name) {
        executeInFrame(0, () -> page.getApplicationCard(name).should(Condition.exist));
        return this;
    }

    public ApplicationsPageValidator validateApplicationDoesNotExistWithName(String name) {
        executeInFrame(0, () -> page.getApplicationCard(name).shouldNot(Condition.exist));
        return this;
    }

}
