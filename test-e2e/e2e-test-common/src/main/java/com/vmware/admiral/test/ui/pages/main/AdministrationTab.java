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
        if (clickIfNotActive(AdministrationTabSelectors.PROJECTS_BUTTON)) {
            LOG.info("Navigating to Projects page");
            getProjectsPage().waitToLoad();
        }
        return getProjectsPage();
    }

    public IdentityManagementPage navigateToIdentityManagementPage() {
        if (clickIfNotActive(AdministrationTabSelectors.IDENTITY_MANAGEMENT_BUTTON)) {
            LOG.info("Navigating to Identity Management page");
            getIdentityManagementPage().waitToLoad();
        }
        return getIdentityManagementPage();
    }

    public RegistriesPage navigateToRegistriesPage() {
        if (clickIfNotActive(AdministrationTabSelectors.REGISTRIES_BUTTON)) {
            LOG.info("Navigating to Registries page");
            getRegistriesPage().waitToLoad();
        }
        return getRegistriesPage();
    }

    public LogsPage navigateToLogsPage() {
        if (clickIfNotActive(AdministrationTabSelectors.LOGS_BUTTON)) {
            LOG.info("Navigating to Logs page");
            getLogsPage().waitToLoad();
        }
        return getLogsPage();
    }

    protected LogsPage getLogsPage() {
        if (Objects.isNull(logsPage)) {
            logsPage = new LogsPage();
        }
        return logsPage;
    }

    @Override
    public void waitToLoad() {
        getProjectsPage().waitToLoad();
    }

    protected ProjectsPage getProjectsPage() {
        if (Objects.isNull(projectsPage)) {
            projectsPage = new ProjectsPage();
        }
        return projectsPage;
    }

    protected IdentityManagementPage getIdentityManagementPage() {
        if (Objects.isNull(identityManagementPage)) {
            identityManagementPage = new IdentityManagementPage();
        }
        return identityManagementPage;
    }

    protected RegistriesPage getRegistriesPage() {
        if (Objects.isNull(registriesPage)) {
            registriesPage = new RegistriesPage();
        }
        return registriesPage;
    }

    protected boolean clickIfNotActive(By selector) {
        SelenideElement element = $(selector);
        if (!element.getAttribute("class").contains("active")) {
            element.click();
            return true;
        }
        return false;
    }

}
