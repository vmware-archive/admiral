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

public abstract class ModalDialog<V extends PageValidator<L>, L extends ModalDialogLocators>
        extends BasicPage<V, L> {

    public ModalDialog(By[] iFrameLocators, V validator, L pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void cancel() {
        LOG.info("Canceling");
        pageActions().click(locators().cancelButton());
    }

    public void submit() {
        LOG.info("Submitting");
        pageActions().click(locators().submitButton());
    }

    @Override
    public void waitToLoad() {
        waitForElementToSettle(locators().modalContent());
    }

}
