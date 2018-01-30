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

package com.vmware.admiral.test.ui.pages.projects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialog;

public class AddProjectModalDialog
        extends ModalDialog<AddProjectModalDialogValidator, AddProjectModalDialogLocators> {

    public AddProjectModalDialog(By[] iFrameLocators, AddProjectModalDialogValidator validator,
            AddProjectModalDialogLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void setName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void setDescription(String description) {
        LOG.info(String.format("Setting description: [%s]", description));
        pageActions().clear(locators().descriptionInput());
        pageActions().sendKeys(description, locators().descriptionInput());
    }

    public void setIsPublic(boolean isPublic) {
        LOG.info(String.format("Setting public: [%s]", isPublic));
        pageActions().setCheckbox(isPublic, locators().publicAccessCheckbox());
    }

    public void closeErrorMessage() {
        pageActions().click(locators().alertCloseButton());
    }

}
