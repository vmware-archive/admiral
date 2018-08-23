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

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class HomeTabLocators extends PageLocators {

    private final By HOME_BUTTON = By.cssSelector(".nav-link.nav-text[href='#/home']");
    protected final String LEFT_MENU_BASE = ".sidenav-content";
    private final By CURRENT_PROJECT_DIV = By
            .cssSelector(LEFT_MENU_BASE + " .project-label");
    private final By PROJECTS_DROPDOWN_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .btn.btn-link.dropdown-toggle");

    private final By APPLICATIONS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href='#/home/applications']");
    private final By CONTAINERS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href='#/home/containers']");
    private final By NETWORKS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href='#/home/networks']");
    private final By VOLUMES_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href='#/home/volumes']");
    private final By TEMPLATES_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href='#/home/templates']");
    private final By PUBLIC_REPOSITORIES_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href='#/home/public-repositories']");
    private final By CLUSTERS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href='#/home/clusters']");
    private final String PROJECT_SELECTOR_BY_NAME_XPATH = "//nav//clr-dropdown-menu//a[contains(concat(' ', normalize-space(text()), ' '), ' %s ')]";
    private final By PROJECT_SELECTOR_DROPDOWN_MENU = By
            .cssSelector(".project-selector .dropdown-menu");

    public By projectSelectorByName(String projectName) {
        return By.xpath(String.format(PROJECT_SELECTOR_BY_NAME_XPATH, projectName));
    }

    public By homeButton() {
        return HOME_BUTTON;
    }

    public By currentProjectDiv() {
        return CURRENT_PROJECT_DIV;
    }

    public By projectsDropdownButton() {
        return PROJECTS_DROPDOWN_BUTTON;
    }

    public By applicationsButton() {
        return APPLICATIONS_BUTTON;
    }

    public By containersButton() {
        return CONTAINERS_BUTTON;
    }

    public By networksButton() {
        return NETWORKS_BUTTON;
    }

    public By volumesButton() {
        return VOLUMES_BUTTON;
    }

    public By templatesButton() {
        return TEMPLATES_BUTTON;
    }

    public By publicRepositoriesButton() {
        return PUBLIC_REPOSITORIES_BUTTON;
    }

    public By clustersButton() {
        return CLUSTERS_BUTTON;
    }

    public By projectSelectorDropdownMenu() {
        return PROJECT_SELECTOR_DROPDOWN_MENU;
    }

}
