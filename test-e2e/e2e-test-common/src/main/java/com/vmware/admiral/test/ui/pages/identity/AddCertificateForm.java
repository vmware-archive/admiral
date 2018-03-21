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

public class AddCertificateForm extends BasicClass<AddCertificateFormLocators> {

    public AddCertificateForm(By[] iframeLocators, AddCertificateFormLocators pageLocators) {
        super(iframeLocators, pageLocators);
    }

    public void setCertificateText(String certificateText) {
        LOG.info("Setting certificate text");
        pageActions().sendKeys(certificateText, locators().certificateInput());
    }

    public void toggleImportFromUrl() {
        LOG.info("Toggling import from URL");
        pageActions().click(locators().importFromUrlToggleButton());
    }

    public void setUrl(String url) {
        LOG.info(String.format("Setting url [%s]", url));
        pageActions().sendKeys(url, locators().urlInput());
    }

    public void submitImportFromUrl() {
        LOG.info("Submitting import from URL");
        pageActions().click(locators().importFromUrlConfirmButton());
    }

    public void cancelImportFromUrl() {
        LOG.info("Cancelling import from URL");
        pageActions().click(locators().importFromUrlCancelButton());
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
