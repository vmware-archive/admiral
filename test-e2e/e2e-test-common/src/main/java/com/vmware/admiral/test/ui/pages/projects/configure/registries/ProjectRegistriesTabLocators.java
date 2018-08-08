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

import com.vmware.admiral.test.ui.pages.projects.configure.ConfigureProjectPageLocators;

public class ProjectRegistriesTabLocators extends ConfigureProjectPageLocators {

    private final By ADD_REGISTRY_BUTTON = By
            .cssSelector("#projectRegistriesContent .btn:first-child");
    private final String REGISTRY_ROW_BY_NAME_XPATH = "//clr-tab-content[@id='projectRegistriesContent']//clr-dg-row//clr-dg-cell[2]//a[text()='%s']/ancestor::clr-dg-row";
    private final String REGISTRY_ROW_RELATIVE_CHECKBOX_XPATH = "//clr-dg-cell[1]";
    private final String EDIT_REGISTRY_BUTTON_RELATIVE_XPATH = "//clr-dg-cell[2]//a";
    private final By DELETE_BUTTON = By
            .cssSelector("#projectRegistriesContent .datagrid-action-bar button:nth-child(2)");

    public By addRegistryButton() {
        return ADD_REGISTRY_BUTTON;
    }

    public By registryRowByName(String name) {
        return By.xpath(String.format(REGISTRY_ROW_BY_NAME_XPATH, name));
    }

    public By registryCheckboxByName(String name) {
        return By.xpath(String.format(REGISTRY_ROW_BY_NAME_XPATH, name)
                + REGISTRY_ROW_RELATIVE_CHECKBOX_XPATH);
    }

    public By registryEditButtonByName(String name) {
        return By.xpath(String.format(REGISTRY_ROW_BY_NAME_XPATH, name)
                + EDIT_REGISTRY_BUTTON_RELATIVE_XPATH);
    }

    public By deleteButton() {
        return DELETE_BUTTON;
    }

}
