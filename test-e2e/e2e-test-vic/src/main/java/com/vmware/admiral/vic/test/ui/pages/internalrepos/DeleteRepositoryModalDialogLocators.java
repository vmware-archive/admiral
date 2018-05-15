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

package com.vmware.admiral.vic.test.ui.pages.internalrepos;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialogLocators;

public class DeleteRepositoryModalDialogLocators extends ModalDialogLocators {

    private By SUBMIT_BUTTON = By.cssSelector(".btn.btn-danger");
    private By CLOSE_BUTTON = By.cssSelector(".btn.btn-primary");
    private By MODAL_TITLE = By.cssSelector(".modal-header .confirmation-title");

    @Override
    public By submitButton() {
        return SUBMIT_BUTTON;
    }

    @Override
    public By modalTitle() {
        return MODAL_TITLE;
    }

    public By closeButton() {
        return CLOSE_BUTTON;
    }

}
