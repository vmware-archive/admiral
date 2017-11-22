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

package com.vmware.admiral.test.ui.pages.projects;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class ProjectsPageValidator extends PageValidator {

    private ProjectsPage page;

    private final By PAGE_TITLE = By.cssSelector(".title > div");

    public ProjectsPageValidator(ProjectsPage page) {
        this.page = page;
    }

    @Override
    public ProjectsPageValidator validateIsCurrentPage() {
        $(PAGE_TITLE).shouldHave(Condition.text("Projects"));
        return this;
    }

    public ProjectsPageValidator validateProjectIsVisible(String name) {
        $(page.getProjectCardSelector(name)).should(Condition.exist);
        return this;
    }

    public ProjectsPageValidator validateProjectsAreVisible(String... names) {
        for (String name : names) {
            validateProjectIsVisible(name);
        }
        return this;
    }

    public ProjectsPageValidator validateProjectIsNotVisible(String name) {
        $(page.getProjectCardSelector(name)).shouldNot(Condition.exist);
        return this;
    }

    public ProjectsPageValidator validateProjectsCount(int expectedCount) {
        int actualCount = $$(By.cssSelector(".items .card-item")).size();
        if (expectedCount != actualCount) {
            throw new AssertionError(
                    String.format("Projects count mismatch, expected: [%d], actual: [%d]",
                            expectedCount, actualCount));
        }
        return this;
    }

}
