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

import java.io.File;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class ImportTemplatePage
        extends BasicPage<ImportTemplatePageValidator, ImportTemplatePageLocators> {

    public ImportTemplatePage(By[] iFrameLocators, ImportTemplatePageValidator validator,
            ImportTemplatePageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void importFromFile(String file) {
        LOG.info("Loading template content from file: " + file);
        File f = new File(file);
        if (!f.exists()) {
            throw new IllegalArgumentException("Specified file does not exist");
        }
        if (f.isDirectory()) {
            throw new IllegalArgumentException("Specified file is a directory");
        }
        pageActions().uploadFile(f, locators().importFromFileButton());
    }

    public void setText(String yamlOrDockerCompose) {
        LOG.info("Setting template content text");
        pageActions().clear(locators().templateTextInput());
        pageActions().sendKeys(yamlOrDockerCompose, locators().templateTextInput());
    }

    public void navigateBack() {
        LOG.info("Navigating back");
        pageActions().click(locators().backButton());
    }

    public void submit() {
        LOG.info("Submitting...");
        pageActions().click(locators().submitButton());
    }

    public void closeErrorMessage() {
        pageActions().click(locators().alertCloseButton());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForElementToSettle(locators().childPageSlide());
    }

}
