/*
 * Copyright (c) 2017 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.templates;

import static com.codeborne.selenide.Selenide.$;

import java.io.File;
import java.util.Objects;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.CreateResourcePage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;
import com.vmware.admiral.test.ui.pages.common.PageValidator;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;
import com.vmware.admiral.test.ui.pages.templates.ImportTemplatePage.ImportTemplatePageValidator;

public class ImportTemplatePage
        extends CreateResourcePage<ImportTemplatePage, ImportTemplatePageValidator> {

    private final By BACK_BUTTON = By.cssSelector(".fa.fa-chevron-circle-left");
    private final By SUBMIT_BUTTON = By.cssSelector(".templateImport.content .btn.btn-primary");
    private final By TEMPLATE_TEXT_INPUT = By.cssSelector(".template-input");
    private final By IMPORT_FROM_FILE_BUTTON = By.cssSelector(".template-import-option .upload");

    private ImportTemplatePageValidator validator;
    private ImportTemplateValidator importValidator;

    private PageProxy parentProxy;

    public ImportTemplatePage(PageProxy parentProxy) {
        this.parentProxy = parentProxy;
    }

    public ImportTemplatePage importFromFile(String file) {
        LOG.info("Loading template content from file: " + file);
        File f = new File(file);
        if (!f.exists()) {
            throw new IllegalArgumentException("Specified file does not exist");
        }
        if (f.isDirectory()) {
            throw new IllegalArgumentException("Specified file is a directory");
        }
        executeInFrame(0, () -> {
            $(IMPORT_FROM_FILE_BUTTON).uploadFile(f);
        });
        return this;
    }

    public ImportTemplatePage setText(String yamlOrDockerCompose) {
        executeInFrame(0, () -> {
            $(TEMPLATE_TEXT_INPUT).clear();
            $(TEMPLATE_TEXT_INPUT).sendKeys(yamlOrDockerCompose);
        });
        return this;
    }

    @Override
    public ImportTemplatePageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ImportTemplatePageValidator();
        }
        return validator;
    }

    @Override
    public void cancel() {
        LOG.info("Cancelling...");
        executeInFrame(0, () -> $(BACK_BUTTON).click());
        parentProxy.waitToLoad();
    }

    @Override
    public ImportTemplateValidator submit() {
        LOG.info("Submitting...");
        executeInFrame(0, () -> $(SUBMIT_BUTTON).click());
        if (Objects.isNull(importValidator)) {
            importValidator = new ImportTemplateValidator();
        }
        return importValidator;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForElementToStopMoving(HomeTabSelectors.CHILD_PAGE_SLIDE));
    }

    @Override
    public ImportTemplatePage getThis() {
        return this;
    }

    public static class ImportTemplatePageValidator extends PageValidator {

        private final By PAGE_TITLE = By.cssSelector(".templateImport-header .title");

        @Override
        public ImportTemplatePageValidator validateIsCurrentPage() {
            executeInFrame(0, () -> $(PAGE_TITLE).shouldHave(Condition.text("Import Template")));
            return this;
        }

    }

}
