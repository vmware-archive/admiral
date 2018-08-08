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

package com.vmware.admiral.test.ui.pages.projects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class ProjectsPage extends BasicPage<ProjectsPageValidator, ProjectsPageLocators> {

    public ProjectsPage(By[] iFrameLocators, ProjectsPageValidator validator,
            ProjectsPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickAddProjectButton() {
        LOG.info("Adding project");
        pageActions().click(locators().addProjectButton());
    }

    public void clickProjectDetailsButton(String projectName) {
        LOG.info(String.format("Configuring project with name: [%s]", projectName));
        By card = locators().projectCardByName(projectName);
        waitForElementToSettle(card);
        pageActions().click(locators().projectContextMenuButtonByName(projectName));
        pageActions().click(locators().projectDetailsButtonByName(projectName));
    }

    public void clickProjectDeleteButton(String projectName) {
        LOG.info(String.format("Deleting project with name: [%s]", projectName));
        By card = locators().projectCardByName(projectName);
        waitForElementToSettle(card);
        pageActions().click(locators().projectContextMenuButtonByName(projectName));
        pageActions().click(locators().projectDeleteButtonByName(projectName));
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForSpinner();
    }

}
