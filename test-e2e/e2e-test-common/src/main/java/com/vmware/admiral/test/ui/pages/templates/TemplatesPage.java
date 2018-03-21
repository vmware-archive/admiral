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

package com.vmware.admiral.test.ui.pages.templates;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePage;

public class TemplatesPage extends ResourcePage<TemplatesPageValidator, TemplatesPageLocators> {

    public TemplatesPage(By[] iFrameLocators, TemplatesPageValidator validator,
            TemplatesPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void provisionTemplate(String name) {
        LOG.info(String.format("Provisioning template [%s]", name));
        By card = locators().cardByExactTitle(name);
        waitForElementToSettle(card);
        pageActions().click(locators().provisionButtonByCardTitle(name));
    }

    public void editTemplate(String name) {
        LOG.info(String.format("Editing template [%s]", name));
        pageActions().hover(locators().cardByExactTitle(name));
        pageActions().click(locators().editTemplateButtonByCardTitle(name));
    }

    public void clickImportTemplateButton() {
        LOG.info("Importing template");
        pageActions().click(locators().importTemplateButton());
    }

    public void clickCreateTemplateButton() {
        LOG.info("Creating template");
        pageActions().click(locators().createResourceButton());
    }

    public void deleteTemplate(String name) {
        LOG.info(String.format("Deleting template with name: [%s]", name));
        deleteItemByExactTitle(name);
        waitForSpinner();
    }
}
