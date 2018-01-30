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

package com.vmware.admiral.test.ui.pages.clusters;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialogLocators;

public class AddClusterModalDialogLocators extends ModalDialogLocators {

    private final By NAME_INPUT = By.id("name");
    private final By DESCRIPTION_INPUT = By.id("description");
    private final By URL_INPUT = By.id("url");
    private final By HOST_TYPE_OPTIONS = By
            .cssSelector(".ng-pristine.ng-valid[formcontrolname*=type]");
    private final By ADD_HOST_ERROR_MESSAGE_DIV = By.cssSelector(MODAL_BASE + " .alert-text");
    private final By ADD_HOST_ERROR_MESSAGE_CLOSE_BUTTON = By
            .cssSelector(MODAL_BASE + " .alert.alert-danger .close");

    public By nameInput() {
        return NAME_INPUT;
    }

    public By descriptionInput() {
        return DESCRIPTION_INPUT;
    }

    public By urlInput() {
        return URL_INPUT;
    }

    public By hostTypeOptions() {
        return HOST_TYPE_OPTIONS;
    }

    public By errorMessageDiv() {
        return ADD_HOST_ERROR_MESSAGE_DIV;
    }

    public By errorMessageCloseButton() {
        return ADD_HOST_ERROR_MESSAGE_CLOSE_BUTTON;
    }

}
