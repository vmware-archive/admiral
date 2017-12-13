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
import static com.codeborne.selenide.Selenide.actions;

import java.util.Objects;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class TemplatesPage extends HomeTabAdvancedPage<TemplatesPage, TemplatesPageValidator> {

    private final By CREATE_TEMPLATE_BUTTON = By
            .cssSelector(".btn.btn-link.create-resource-btn");
    private final By IMPORT_TEMPLATE_BUTTON = By
            .cssSelector(".btn.btn-link[href*=\"import-template\"]");
    private final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");
    private final By CARD_RELATIVE_DELETE_BUTTON = By.cssSelector(".fa.fa-trash");
    private final By CARD_RELATIVE_DELETE_CONFIRMATION_BUTTON = By
            .cssSelector(".delete-inline-item-confirmation-confirm>div>a");
    private final By CARD_RELATIVE_PROVISION_BUTTON = By.cssSelector(".btn.create-container-btn");
    private final String TEMPLATE_CARD_SELECTOR_BY_NAME = "html/body/div/div/div[2]/div[2]/div[1]/div[2]/div/div/div[3]/div/div/div/div/div[1]/div[2]/div[1][text()='%s']/../../../..";

    private TemplatesPageValidator validator;
    private ImportTemplatePage importTemplatePage;
    private CreateTemplatePage createTemplatePage;

    @Override
    public TemplatesPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new TemplatesPageValidator(this);
        }
        return validator;
    }

    public TemplatesPage provisionTemplate(String name) {
        LOG.info("Provisioning template");
        executeInFrame(0, () -> {
            waitForElementToStopMoving(getTemplateCardSelector(name))
                    .$(CARD_RELATIVE_PROVISION_BUTTON).click();
            $(CARD_RELATIVE_PROVISION_BUTTON).click();
        });
        return this;
    }

    public ImportTemplatePage importTemplate() {
        LOG.info("Importing template");
        executeInFrame(0, () -> {
            $(IMPORT_TEMPLATE_BUTTON).click();
            waitForElementToStopMoving(HomeTabSelectors.CHILD_PAGE_SLIDE);
        });
        if (Objects.isNull(importTemplatePage)) {
            importTemplatePage = new ImportTemplatePage(new PageProxy(this));
        }
        importTemplatePage.waitToLoad();
        return importTemplatePage;
    }

    public CreateTemplatePage createTemplate() {
        LOG.info("Creating template");
        executeInFrame(0, () -> {
            $(CREATE_TEMPLATE_BUTTON).click();
            waitForElementToStopMoving(HomeTabSelectors.CHILD_PAGE_SLIDE);
        });
        if (Objects.isNull(createTemplatePage)) {
            createTemplatePage = new CreateTemplatePage(new PageProxy(this));
        }
        createTemplatePage.waitToLoad();
        return createTemplatePage;
    }

    public TemplatesPage deleteTemplate(String name) {
        LOG.info(String.format("Deleting template with name: [%s]", name));
        executeInFrame(0, () -> {
            SelenideElement card = waitForElementToStopMoving(getTemplateCardSelector(name));
            actions().moveToElement(card)
                    .moveToElement(card.$(CARD_RELATIVE_DELETE_BUTTON))
                    .click()
                    .moveToElement(card.$(CARD_RELATIVE_DELETE_CONFIRMATION_BUTTON))
                    .click()
                    .build().perform();
            waitForSpinner();
            card.shouldNot(Condition.exist);
        });
        return this;
    }

    By getTemplateCardSelector(String name) {
        return By.xpath(String.format(TEMPLATE_CARD_SELECTOR_BY_NAME, name));
    }

    @Override
    public TemplatesPage refresh() {
        LOG.info("Refreshing...");
        executeInFrame(0, () -> {
            $(REFRESH_BUTTON).click();
            waitForSpinner();
        });
        return this;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForSpinner());
    }

    @Override
    public TemplatesPage getThis() {
        return this;
    }

}
