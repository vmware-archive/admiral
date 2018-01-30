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

package com.vmware.admiral.test.ui.pages.common;

import org.openqa.selenium.By;

public class ModalDialogLocators extends PageLocators {

    protected final String MODAL_BASE = ".modal-content";
    private final By MODAL_BACKDROP = By.cssSelector(".modal-backdrop");
    private final By SAVE_BUTTON = By.cssSelector(MODAL_BASE + " .btn.btn-primary");
    private final By CANCEL_BUTTON = By.cssSelector(MODAL_BASE + " .btn.btn-outline");
    private final By MODAL_TITLE = By.cssSelector(".modal-content .modal-title");

    public By submitButton() {
        return SAVE_BUTTON;
    }

    public By cancelButton() {
        return CANCEL_BUTTON;
    }

    public By modalBackdrop() {
        return MODAL_BACKDROP;
    }

    public By modalTitle() {
        return MODAL_TITLE;
    }

}
