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

package com.vmware.admiral.test.ui.pages.containers.create;

import org.openqa.selenium.By;

public class EnvironmentTabLocators extends CreateContainerPageLocators {

    private final String LAST_ENV_VARIABLES_BASE = "#environment .container-environment-input .multicolumn-inputs-list-body .multicolumn-input:last-child";
    private final By ADD_ENV_VARIABLE_ROW_BUTTON = By
            .cssSelector(LAST_ENV_VARIABLES_BASE + " .fa-plus");
    private final By LAST_VARIABLE_NAME_INPUT = By
            .cssSelector(LAST_ENV_VARIABLES_BASE + " input[name='name']");
    private final By LAST_VARIABLE_VALUE_INPUT = By
            .cssSelector(LAST_ENV_VARIABLES_BASE + " input[name='value']");
    private final String LAST_OPTIONS_BASE = "#environment .container-custom-properties-input .multicolumn-inputs-list-body .multicolumn-input:last-child";
    private final By ADD_PROPERTIES_ROW_BUTTON = By.cssSelector(LAST_OPTIONS_BASE + " .fa-plus");
    private final By LAST_PROPERTY_NAME_INPUT = By
            .cssSelector(LAST_OPTIONS_BASE + " input[name='name']");
    private final By LAST_PROPERTY_VALUE_INPUT = By
            .cssSelector(LAST_OPTIONS_BASE + " input[name='value']");

    public By addVariableRowButton() {
        return ADD_ENV_VARIABLE_ROW_BUTTON;
    }

    public By lastVariableNameInput() {
        return LAST_VARIABLE_NAME_INPUT;
    }

    public By lastVariableValueInput() {
        return LAST_VARIABLE_VALUE_INPUT;
    }

    public By addPropertiesRowButton() {
        return ADD_PROPERTIES_ROW_BUTTON;
    }

    public By lastPropertyNameInput() {
        return LAST_PROPERTY_NAME_INPUT;
    }

    public By lastPropertyValueInput() {
        return LAST_PROPERTY_VALUE_INPUT;
    }

}
