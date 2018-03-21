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

package com.vmware.admiral.vic.test.ui.pages.projectrepos;

import static com.codeborne.selenide.Selenide.Wait;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialog;

public class DeleteRepositoryModalDialog extends
        ModalDialog<DeleteRepositoryModalDialogValidator, DeleteRepositoryModalDialogLocators> {

    public DeleteRepositoryModalDialog(By[] iFrameLocators,
            DeleteRepositoryModalDialogValidator validator,
            DeleteRepositoryModalDialogLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void waitForDeleteToComplete() {
        LOG.info("Waiting for delete to complete");
        Wait().until(d -> !element(locators().closeButton()).has(Condition.attribute("hidden"))
                && !element(locators().closeButton()).has(Condition.attribute("disabled")));
    }

    public void close() {
        LOG.info("Closing");
        pageActions().click(locators().closeButton());
    }

}
