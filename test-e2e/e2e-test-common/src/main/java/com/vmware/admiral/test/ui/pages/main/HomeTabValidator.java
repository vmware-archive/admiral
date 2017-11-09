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

package com.vmware.admiral.test.ui.pages.main;

import static com.codeborne.selenide.Selenide.$;

import java.util.Arrays;
import java.util.List;

import com.codeborne.selenide.Condition;

import com.vmware.admiral.test.ui.pages.common.ExtendablePageValidator;

public abstract class HomeTabValidator<V extends HomeTabValidator<V>>
        extends ExtendablePageValidator<V> {

    private HomeTab<?, ?> page;

    public HomeTabValidator(HomeTab<?, ?> page) {
        this.page = page;
    }

    @Override
    public V validateIsCurrentPage() {
        $(GlobalSelectors.HOME_BUTTON).shouldHave(Condition.cssClass("active"));
        return getThis();
    }

    public V validateApplicationsNotAvailable() {
        $(HomeTabSelectors.APPLICATIONS_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateContainersNotAvailable() {
        $(HomeTabSelectors.CONTAINERS_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateNetworksNotAvailable() {
        $(HomeTabSelectors.NETWORKS_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateVolumesNotAvailable() {
        $(HomeTabSelectors.VOLUMES_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateTemplatesNotAvailable() {
        $(HomeTabSelectors.TEMPLATES_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validatePublicRepositoriesNotAvailable() {
        $(HomeTabSelectors.PUBLIC_REPOSITORIES_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateContainerHostsNotAvailable() {
        $(HomeTabSelectors.CONTAINER_HOSTS_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateApplicationsAvailable() {
        $(HomeTabSelectors.APPLICATIONS_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateContainersAvailable() {
        $(HomeTabSelectors.CONTAINERS_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateNetworksAvailable() {
        $(HomeTabSelectors.NETWORKS_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateVolumesAvailable() {
        $(HomeTabSelectors.VOLUMES_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateTemplatesAvailable() {
        $(HomeTabSelectors.TEMPLATES_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validatePublicRepositoriesAvailable() {
        $(HomeTabSelectors.PUBLIC_REPOSITORIES_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateContainerHostsAvailable() {
        $(HomeTabSelectors.CONTAINER_HOSTS_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateCurrentProjectIs(String projectName) {
        $(HomeTabSelectors.CURRENT_PROJECT_INDICATOR).shouldBe(Condition.visible)
                .shouldNotHave(Condition.exactTextCaseSensitive("--"));
        String actualProjectname = $(HomeTabSelectors.CURRENT_PROJECT_INDICATOR).getText();
        if (!actualProjectname.equals(projectName)) {
            throw new AssertionError(
                    String.format("Project name mismatch: expected[%s], actual[%s]", projectName,
                            actualProjectname));
        }
        return getThis();
    }

    public V validateProjectIsAvailable(String projectName) {
        validateProjectsAreAvailable(projectName);
        return getThis();
    }

    public V validateProjectsAreAvailable(String... projectNames) {
        validateProjectsAreAvailable(Arrays.asList(projectNames));
        return getThis();
    }

    public V validateProjectsAreAvailable(List<String> projectNames) {
        List<String> visibleProjectNames = page.getProjectsNames();
        for (String projectName : projectNames) {
            if (!visibleProjectNames.contains(projectName)) {
                String error = String.format(
                        "Project with name [%s] is not available for the current user",
                        projectName);
                throw new AssertionError(error);
            }
        }
        return getThis();
    }

    public V validateProjectIsNotAvailable(String projectName) {
        validateProjectsAreNotAvailable(projectName);
        return getThis();
    }

    public V validateProjectsAreNotAvailable(String... projectNames) {
        validateProjectsAreNotAvailable(Arrays.asList(projectNames));
        return getThis();
    }

    public V validateProjectsAreNotAvailable(List<String> projectNames) {
        List<String> visibleProjectNames = page.getProjectsNames();
        for (String projectName : projectNames) {
            if (visibleProjectNames.contains(projectName)) {
                String error = String.format(
                        "Project with name [%s] is available for the current user, but expected not to be",
                        projectName);
                throw new AssertionError(error);
            }
        }
        return getThis();
    }

}
