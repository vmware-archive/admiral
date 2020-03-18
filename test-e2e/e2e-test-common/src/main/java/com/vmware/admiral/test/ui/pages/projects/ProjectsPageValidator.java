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

package com.vmware.admiral.test.ui.pages.projects;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class ProjectsPageValidator extends PageValidator<ProjectsPageLocators> {

    public ProjectsPageValidator(By[] iFrameLocators, ProjectsPageLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().pageTitle()).shouldHave(Condition.text("Projects"));
    }

    public void validateProjectIsVisible(String name) {
        element(locators().projectCardByName(name)).should(Condition.exist);
    }

    public void validateProjectIsNotVisible(String name) {
        element(locators().projectCardByName(name)).shouldNot(Condition.exist);
    }

    public void validateProjectsAreVisible(String... names) {
        for (String name : names) {
            validateProjectIsVisible(name);
        }
    }

    public void validateAddProjectButtonAvailable() {
        element(locators().addProjectButton()).shouldBe(Condition.visible);
    }

    public void validateAddProjectButtonNotAvailable() {
        element(locators().addProjectButton()).shouldNotBe(Condition.visible);
    }

    public void validateProjectDeleteButtonAvailable(String projectName) {
        element(locators().projectDeleteButtonByName(projectName)).shouldBe(Condition.visible);
    }

    public void validateProjectDeleteButtonNotAvailable(String projectName) {
        element(locators().projectDeleteButtonByName(projectName)).shouldNotBe(Condition.visible);
    }

    public void validateProjectsCount(int expectedCount) {
        int actualCount = pageActions().getElementCount(By.cssSelector(".items .card-item"));
        if (expectedCount != actualCount) {
            throw new AssertionError(
                    String.format("Projects count mismatch, expected: [%d], actual: [%d]",
                            expectedCount, actualCount));
        }
    }

}
