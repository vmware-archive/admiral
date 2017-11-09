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

import java.util.Objects;

import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;
import com.vmware.admiral.test.ui.pages.common.ExtendablePageValidator;
import com.vmware.admiral.test.ui.pages.identity.IdentityManagementPage;
import com.vmware.admiral.test.ui.pages.logs.LogsPage;
import com.vmware.admiral.test.ui.pages.projects.ProjectsPage;
import com.vmware.admiral.test.ui.pages.registries.RegistriesPage;

public abstract class AdministrationTab<P extends AdministrationTab<P, V>, V extends ExtendablePageValidator<V>>
        extends BasicPage<P, V> {

    // private AdministrationTabValidator validator;
    private ProjectsPage projectsPage;
    private IdentityManagementPage identityManagementPage;
    private RegistriesPage registriesPage;
    private LogsPage logsPage;

    public ProjectsPage navigateToProjectsPage() {
        clickIfNotActive(AdministrationTabSelectors.PROJECTS_BUTTON);
        if (Objects.isNull(projectsPage)) {
            projectsPage = new ProjectsPage();
        }
        return projectsPage;
    }

    public IdentityManagementPage navigateToIdentityManagementPage() {
        clickIfNotActive(AdministrationTabSelectors.IDENTITY_MANAGEMENT_BUTTON);
        if (Objects.isNull(identityManagementPage)) {
            identityManagementPage = new IdentityManagementPage();
        }
        return identityManagementPage;
    }

    public RegistriesPage navigateToRegistriesPage() {
        clickIfNotActive(AdministrationTabSelectors.REGISTRIES_BUTTON);
        if (Objects.isNull(registriesPage)) {
            registriesPage = new RegistriesPage();
        }
        return registriesPage;
    }

    public LogsPage navigateToLogsPage() {
        clickIfNotActive(AdministrationTabSelectors.LOGS_BUTTON);
        if (Objects.isNull(logsPage)) {
            logsPage = new LogsPage();
        }
        return logsPage;
    }

    protected void clickIfNotActive(By selector) {
        SelenideElement element = $(selector);
        if (!element.getAttribute("class").contains("active")) {
            element.click();
        }
    }

}
