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

public class AdministrationTabLocators extends PageLocators {

    private final By ADMINISTRATION_TAB_BUTTON = By
            .cssSelector(".nav-link.nav-text[href='#/administration']");
    private final By IDENTITY_MANAGEMENT_BUTTON = By
            .cssSelector(".nav-content .nav-link[href='#/administration/identity-management']");
    private final By PROJECTS_BUTTON = By
            .cssSelector(".nav-content .nav-link[href='#/administration/projects']");
    private final By REGISTRIES_BUTTON = By
            .cssSelector(".nav-content .nav-link[href='#/administration/registries']");
    private final By LOGS_BUTTON = By
            .cssSelector(".nav-content .nav-link[href='#/administration/logs']");

    public By administrationTabButton() {
        return ADMINISTRATION_TAB_BUTTON;
    }

    public By identityManagementButton() {
        return IDENTITY_MANAGEMENT_BUTTON;
    }

    public By projectsButton() {
        return PROJECTS_BUTTON;
    }

    public By registriesButton() {
        return REGISTRIES_BUTTON;
    }

    public By logsButton() {
        return LOGS_BUTTON;
    }

}
