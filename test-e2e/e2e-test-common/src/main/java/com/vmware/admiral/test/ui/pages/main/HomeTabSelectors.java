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

import org.openqa.selenium.By;

public class HomeTabSelectors {
    public static final String LEFT_MENU_BASE = ".sidenav-content";
    public static final By CURRENT_PROJECT_INDICATOR = By
            .cssSelector(LEFT_MENU_BASE + " .project-label");
    public static final By APPLICATIONS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href*=applications]");
    public static final By CONTAINERS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href*=containers]");
    public static final By NETWORKS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href*=networks]");
    public static final By VOLUMES_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href*=volumes]");
    public static final By TEMPLATES_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href*=templates]");
    public static final By PUBLIC_REPOSITORIES_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href*=public-repositories]");
    public static final By CONTAINER_HOSTS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .nav-link[href*=clusters]");
    public static final By AVAILABLE_PROJECTS_BUTTON = By
            .cssSelector(LEFT_MENU_BASE + " .btn.btn-link.dropdown-toggle");
    public static final By CHILD_PAGE_SLIDE = By
            .cssSelector(".closable-view.slide-and-fade-transition");
}
