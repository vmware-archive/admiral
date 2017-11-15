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
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.Wait;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.vmware.admiral.test.ui.pages.applications.ApplicationsPage;
import com.vmware.admiral.test.ui.pages.common.BasicPage;
import com.vmware.admiral.test.ui.pages.containers.ContainersPage;
import com.vmware.admiral.test.ui.pages.hosts.ContainerHostsPage;
import com.vmware.admiral.test.ui.pages.networks.NetworksPage;
import com.vmware.admiral.test.ui.pages.publicrepos.PublicRepositoriesPage;
import com.vmware.admiral.test.ui.pages.templates.TemplatesPage;
import com.vmware.admiral.test.ui.pages.volumes.VolumesPage;

public abstract class HomeTab<P extends HomeTab<P, V>, V extends HomeTabValidator<V>>
        extends BasicPage<P, V> {

    private final By ALL_PROJECT_NAMES = By
            .cssSelector(".project-selector .dropdown-menu .dropdown-item");

    private ApplicationsPage applicationsPage;
    private ContainersPage containersPage;
    private NetworksPage networksPage;
    private VolumesPage volumesPage;
    private TemplatesPage templatesPage;
    private PublicRepositoriesPage publicRepositoriesPage;
    private ContainerHostsPage containerHostsPage;

    public void switchToProject(String projectName) {
        getProjectWithName(projectName).click();
    }

    private SelenideElement getProjectWithName(String name) {
        waitForProjectsReady();
        $(HomeTabSelectors.AVAILABLE_PROJECTS_BUTTON).click();
        List<SelenideElement> projects = $$(ALL_PROJECT_NAMES);
        for (SelenideElement project : projects) {
            if (project.getText().equals(name)) {
                return project;
            }
        }
        throw new AssertionError(
                "Project '" + name + "' does not exist or is not available for the current user");
    }

    List<String> getProjectsNames() {
        waitForProjectsReady();
        $(HomeTabSelectors.AVAILABLE_PROJECTS_BUTTON).click();
        List<String> projectNames = $$(ALL_PROJECT_NAMES).stream().map(e -> e.getText())
                .collect(Collectors.toList());
        $(HomeTabSelectors.AVAILABLE_PROJECTS_BUTTON).click();
        return projectNames;
    }

    void waitForProjectsReady() {
        $(HomeTabSelectors.CURRENT_PROJECT_INDICATOR).shouldBe(Condition.visible)
                .shouldNotHave(Condition.exactTextCaseSensitive("--"));
    }

    public ApplicationsPage navigateToApplicationsPage() {
        if (clickIfNotActive(HomeTabSelectors.APPLICATIONS_BUTTON)) {
            // waitForItemsToLoad(); }
        }
        if (Objects.isNull(applicationsPage)) {
            applicationsPage = new ApplicationsPage();
        }
        return applicationsPage;
    }

    public ContainersPage navigateToContainersPage() {
        if (clickIfNotActive(HomeTabSelectors.CONTAINERS_BUTTON)) {
            // waitForItemsToLoad();
        }
        if (Objects.isNull(containersPage)) {
            containersPage = new ContainersPage();
        }
        return containersPage;
    }

    public NetworksPage navigateToNetworksPage() {
        if (clickIfNotActive(HomeTabSelectors.NETWORKS_BUTTON)) {
            // waitForItemsToLoad();
        }
        if (Objects.isNull(networksPage)) {
            networksPage = new NetworksPage();
        }
        return networksPage;
    }

    public VolumesPage navigateToVolumesPage() {
        if (clickIfNotActive(HomeTabSelectors.VOLUMES_BUTTON)) {
            // waitForItemsToLoad();
        }
        if (Objects.isNull(volumesPage)) {
            volumesPage = new VolumesPage();
        }
        return volumesPage;
    }

    public TemplatesPage navigateToTemplatesPage() {
        if (clickIfNotActive(HomeTabSelectors.TEMPLATES_BUTTON)) {
            // waitForItemsToLoad();
        }
        if (Objects.isNull(templatesPage)) {
            templatesPage = new TemplatesPage();
        }
        return templatesPage;
    }

    public PublicRepositoriesPage navigateToPublicRepositoriesPage() {
        clickIfNotActive(HomeTabSelectors.PUBLIC_REPOSITORIES_BUTTON);
        if (Objects.isNull(publicRepositoriesPage)) {
            publicRepositoriesPage = new PublicRepositoriesPage();
        }
        return publicRepositoriesPage;
    }

    public ContainerHostsPage navigateToContainerHostsPage() {
        if (clickIfNotActive(HomeTabSelectors.CONTAINER_HOSTS_BUTTON)) {
            Wait().until(ExpectedConditions.or(
                    d -> {
                        return $(By.cssSelector(".card-item")).exists();
                    },
                    d -> {
                        return $(By.cssSelector(".content-empty")).exists();
                    }));
        }
        if (Objects.isNull(containerHostsPage)) {
            containerHostsPage = new ContainerHostsPage();
        }
        return containerHostsPage;
    }

    protected boolean clickIfNotActive(By selector) {
        SelenideElement element = $(selector);
        if (!element.getAttribute("class").contains("active")) {
            element.click();
            return true;
        }
        return false;
    }

    /*
     * private void waitForItemsToLoad() { executeInFrame(0, () -> {
     * waitForElementToAppearAndDisappear(GlobalSelectors.SPINNER); }); }
     */

}
