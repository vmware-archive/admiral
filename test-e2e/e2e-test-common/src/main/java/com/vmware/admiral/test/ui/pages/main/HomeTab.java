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

package com.vmware.admiral.test.ui.pages.main;

import static com.codeborne.selenide.Selenide.Wait;

import java.time.Duration;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class HomeTab extends BasicPage<HomeTabValidator, HomeTabLocators> {

    public HomeTab(By[] iFrameLocators, HomeTabValidator validator, HomeTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickApplicationsButton() {
        LOG.info("Navigating to Applications page");
        pageActions().click(locators().applicationsButton());
    }

    public void clickContainersButton() {
        LOG.info("Navigating to Containers page");
        pageActions().click(locators().containersButton());
    }

    public void clickNetworksButton() {
        LOG.info("Navigating to Networks page");
        pageActions().click(locators().networksButton());
    }

    public void clickVolumesButton() {
        LOG.info("Navigating to Volumes page");
        pageActions().click(locators().volumesButton());
    }

    public void clickTemplatesButton() {
        LOG.info("Navigating to Templates page");
        pageActions().click(locators().templatesButton());
    }

    public void clickRepositoriesButton() {
        LOG.info("Navigating to Public Repositories page");
        pageActions().click(locators().publicRepositoriesButton());
    }

    public void clickClustersButton() {
        LOG.info("Navigating to Clusters page");
        pageActions().click(locators().clustersButton());
    }

    public void switchToProject(String projectName) {
        String currentProject = getCurrentProject();
        if (projectName.equals(currentProject)) {
            LOG.info(String.format("Current project already is: [%s]", currentProject));
        } else {
            LOG.info(String.format("Switching to project: [%s]", projectName));
            doSwitchToProject(projectName, 3);
        }
    }

    private void doSwitchToProject(String projectName, int retries) {
        if (retries == 0) {
            throw new RuntimeException("Could not switch to project: " + projectName);
        }
        openProjectSelector();
        selectProject(projectName);
        if (!getCurrentProject().equals(projectName)) {
            doSwitchToProject(projectName, --retries);
        }
    }

    private void openProjectSelector() {
        int retries = 3;
        while (retries > 0) {
            pageActions().click(locators().projectsDropdownButton());
            try {
                Wait().withTimeout(Duration.ofSeconds(5))
                        .until(d -> element((locators().projectSelectorDropdownMenu()))
                                .is(Condition.visible));
                return;
            } catch (TimeoutException e) {
                LOG.info("Could not open project selector dropdown, retrying...");
                retries--;
            }
        }
        throw new RuntimeException("Could not open project selector dropdown");
    }

    private void selectProject(String projectName) {
        int retries = 3;
        while (retries > 0) {
            pageActions().click(locators().projectSelectorByName(projectName));
            try {
                Wait().withTimeout(Duration.ofSeconds(5))
                        .until(d -> element((locators().projectSelectorDropdownMenu()))
                                .is(Condition.hidden));
                return;
            } catch (TimeoutException e) {
                LOG.info("Could not select project, retrying...");
                retries--;
            }
        }
        throw new RuntimeException("Could not select project: " + projectName);
    }

    public String getCurrentProject() {
        element(locators().currentProjectDiv()).shouldBe(Condition.visible)
                .shouldNotHave(Condition.exactTextCaseSensitive("--"));
        return pageActions().getText(locators().currentProjectDiv());
    }

    @Override
    public void waitToLoad() {
        // TODO wait to load
    }

}
