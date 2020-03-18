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

package com.vmware.admiral.test.ui.pages.main;

import java.util.Arrays;
import java.util.List;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class HomeTabValidator extends PageValidator<HomeTabLocators> {

    public HomeTabValidator(By[] iFrameLocators, HomeTabLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().homeButton()).shouldHave(Condition.cssClass("active"));
    }

    public void validateApplicationsNotAvailable() {
        element(locators().applicationsButton()).shouldNotBe(Condition.visible);
    }

    public void validateContainersNotAvailable() {
        element(locators().containersButton()).shouldNotBe(Condition.visible);
    }

    public void validateNetworksNotAvailable() {
        element(locators().networksButton()).shouldNotBe(Condition.visible);
    }

    public void validateVolumesNotAvailable() {
        element(locators().volumesButton()).shouldNotBe(Condition.visible);
    }

    public void validateTemplatesNotAvailable() {
        element(locators().templatesButton()).shouldNotBe(Condition.visible);
    }

    public void validatePublicRepositoriesNotAvailable() {
        element(locators().publicRepositoriesButton()).shouldNotBe(Condition.visible);
    }

    public void validateContainerHostsNotAvailable() {
        element(locators().clustersButton()).shouldNotBe(Condition.visible);
    }

    public void validateApplicationsAvailable() {
        element(locators().applicationsButton()).shouldBe(Condition.visible);
    }

    public void validateContainersAvailable() {
        element(locators().containersButton()).shouldBe(Condition.visible);
    }

    public void validateNetworksAvailable() {
        element(locators().networksButton()).shouldBe(Condition.visible);
    }

    public void validateVolumesAvailable() {
        element(locators().volumesButton()).shouldBe(Condition.visible);
    }

    public void validateTemplatesAvailable() {
        element(locators().templatesButton()).shouldBe(Condition.visible);
    }

    public void validatePublicRepositoriesAvailable() {
        element(locators().publicRepositoriesButton()).shouldBe(Condition.visible);
    }

    public void validateContainerHostsAvailable() {
        element(locators().clustersButton()).shouldBe(Condition.visible);
    }

    public void validateCurrentProjectIs(String projectName) {
        element(locators().currentProjectDiv()).shouldBe(Condition.visible)
                .shouldNotHave(Condition.exactTextCaseSensitive("--"));
        String currentProjectName = pageActions()
                .getText(locators().currentProjectDiv());
        if (!currentProjectName.equals(projectName)) {
            throw new AssertionError(
                    String.format("Project name mismatch: expected[%s], actual[%s]", projectName,
                            currentProjectName));
        }
    }

    public void validateProjectIsAvailable(String projectName) {
        validateProjectsAreAvailable(projectName);
    }

    public void validateProjectsAreAvailable(String... projectNames) {
        validateProjectsAreAvailable(Arrays.asList(projectNames));
    }

    public void validateProjectsAreAvailable(List<String> projectNames) {
        pageActions().click(locators().projectsDropdownButton());
        for (String projectName : projectNames) {
            element(locators().projectSelectorByName(projectName)).shouldBe(Condition.visible);
        }
        pageActions().click(locators().projectsDropdownButton());
    }

    public void validateProjectIsNotAvailable(String projectName) {
        validateProjectsAreNotAvailable(projectName);
    }

    public void validateProjectsAreNotAvailable(String... projectNames) {
        validateProjectsAreNotAvailable(Arrays.asList(projectNames));
    }

    public void validateProjectsAreNotAvailable(List<String> projectNames) {
        pageActions().click(locators().projectsDropdownButton());
        for (String projectName : projectNames) {
            element(locators().projectSelectorByName(projectName)).shouldNotBe(Condition.visible);
        }
        pageActions().click(locators().projectsDropdownButton());
    }

}
