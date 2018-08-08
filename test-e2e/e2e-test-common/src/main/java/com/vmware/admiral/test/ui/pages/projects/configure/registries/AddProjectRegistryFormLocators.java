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

package com.vmware.admiral.test.ui.pages.projects.configure.registries;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class AddProjectRegistryFormLocators extends PageLocators {

    private final By PAGE_TITLE = By
            .cssSelector("app-project-registry-details .projects-details-header-title");
    private final By REGISTRY_ADDRESS_FIELD = By.cssSelector("#registryAddress");
    private final By REGISTRY_NAME_FIELD = By.cssSelector("#registryName");
    private final By SAVE_BUTTON = By.cssSelector("app-project-registry-details .btn-primary");

    public By pageTitle() {
        return PAGE_TITLE;
    }

    public By addressInput() {
        return REGISTRY_ADDRESS_FIELD;
    }

    public By nameInput() {
        return REGISTRY_NAME_FIELD;
    }

    public By saveButton() {
        return SAVE_BUTTON;
    }

}
