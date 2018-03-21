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

public class LogConfigTabLocators extends CreateContainerPageLocators {

    private final By DRIVER_SELECT = By
            .cssSelector("#logconfig .container-logconfig-driver-input .form-control");
    private final By ADD_OPTIONS_ROW_BUTTON = By.cssSelector(
            "#logconfig .container-logconfig-options-input .multicolumn-input:last-child .fa-plus");
    private final By LAST_OPTIONS_NAME_INPUT = By.cssSelector(
            "#logconfig .container-logconfig-options-input .multicolumn-input:last-child input[name='name']");
    private final By LAST_OPTIONS_VALUE_INPUT = By.cssSelector(
            "#logconfig .container-logconfig-options-input .multicolumn-input:last-child input[name='value']");

    public By driverSelect() {
        return DRIVER_SELECT;
    }

    public By addOptionsRowButton() {
        return ADD_OPTIONS_ROW_BUTTON;
    }

    public By lastOptionNameInput() {
        return LAST_OPTIONS_NAME_INPUT;
    }

    public By lastOptionValueInput() {
        return LAST_OPTIONS_VALUE_INPUT;
    }

}
