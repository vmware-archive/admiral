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

package com.vmware.admiral.test.ui.pages.identity;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicClass;

public class NewCredentialForm extends BasicClass<NewCredentialFormLocators> {

    public NewCredentialForm(By[] iframeLocators, NewCredentialFormLocators pageLocators) {
        super(iframeLocators, pageLocators);
    }

    public void setName(String name) {
        LOG.info(String.format("Setting name [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void selectUsernameType() {
        LOG.info("Selecting Username credential type");
        pageActions().click(locators().usernameRadioButton());
    }

    public void selectCertificateType() {
        LOG.info("Selecting Certificate credential type");
        pageActions().click(locators().certificateRadioButton());
    }

    public void addCustomProperty(String name, String value) {
        LOG.info(
                String.format("Addinf custom property witn name [%s] and value [%s]", name, value));
        if (!pageActions().getAttribute("value", locators().lastPropertyNameInput()).trim()
                .isEmpty()) {
            pageActions().click(locators().addPropertyRowButton());
        }
        pageActions().sendKeys(name, locators().lastPropertyNameInput());
        pageActions().sendKeys(value, locators().lastPropertyValueInput());
    }

    public void submit() {
        LOG.info("Submitting");
        pageActions().click(locators().saveButton());
    }

    public void cancel() {
        LOG.info("Cancelling");
        pageActions().click(locators().cancelButton());
    }

}
