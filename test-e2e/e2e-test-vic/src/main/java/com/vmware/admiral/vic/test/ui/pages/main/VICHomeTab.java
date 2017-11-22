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

package com.vmware.admiral.vic.test.ui.pages.main;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.main.HomeTab;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;
import com.vmware.admiral.test.ui.pages.main.HomeTabValidator;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab.VICHomeTabValidator;
import com.vmware.admiral.vic.test.ui.pages.projectrepos.ProjectRepositoriesPage;

public class VICHomeTab extends HomeTab<VICHomeTab, VICHomeTabValidator> {

    public static final By PROJECT_REPOSITORIES_BUTTON = By.cssSelector(
            HomeTabSelectors.LEFT_MENU_BASE + " .nav-link[href*=project-repositories]");

    private VICHomeTabValidator validator;

    private ProjectRepositoriesPage projectRepositoriesPage;

    public ProjectRepositoriesPage navigateToProjectRepositoriesPage() {
        if (clickIfNotActive(PROJECT_REPOSITORIES_BUTTON)) {
            LOG.info("Navigating to Project Repositories page");
            getProjectRepositoriesPage().waitToLoad();
        }
        return getProjectRepositoriesPage();
    }

    private ProjectRepositoriesPage getProjectRepositoriesPage() {
        if (Objects.isNull(projectRepositoriesPage)) {
            projectRepositoriesPage = new ProjectRepositoriesPage();
        }
        return projectRepositoriesPage;
    }

    @Override
    public VICHomeTabValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new VICHomeTabValidator(this);
        }
        return validator;
    }

    @Override
    public VICHomeTab getThis() {
        return this;
    }

    public static class VICHomeTabValidator extends HomeTabValidator<VICHomeTabValidator> {

        VICHomeTabValidator(VICHomeTab page) {
            super(page);
        }

        public VICHomeTabValidator validateProjectRepositoriesAvailable() {
            $(PROJECT_REPOSITORIES_BUTTON).shouldBe(Condition.visible);
            return this;
        }

        public VICHomeTabValidator validateProjectRepositoriesNotAvailable() {
            $(PROJECT_REPOSITORIES_BUTTON).shouldNotBe(Condition.visible);
            return this;
        }

        public VICHomeTabValidator validateAllHomeTabsAreAvailable() {
            validateApplicationsAvailable();
            validateContainersAvailable();
            validateNetworksAvailable();
            validateVolumesAvailable();
            validateTemplatesAvailable();
            validateProjectRepositoriesAvailable();
            validatePublicRepositoriesAvailable();
            validateContainerHostsAvailable();
            return this;
        }

        @Override
        public VICHomeTabValidator getThis() {
            return this;
        }

    }

}
