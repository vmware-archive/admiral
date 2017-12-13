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

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicClass;
import com.vmware.admiral.test.ui.pages.common.FailableActionValidator;
import com.vmware.admiral.test.ui.pages.common.PageProxy;

public class ImportTemplateValidator extends BasicClass implements FailableActionValidator {

    private final By ALERT_MESSAGE_HOLDER = By.cssSelector(".alert.alert-danger.alert-dismissible");
    private final By CLOSE_ALERT_BUTTON = By.cssSelector(".fa.fa-close");

    @Override
    public void expectSuccess() {
        EditTemplatePage editTemplatePage = new EditTemplatePage(
                new PageProxy(new TemplatesPage()));
        editTemplatePage.waitToLoad();
        editTemplatePage.navigateBack();
    }

    @Override
    public void expectFailure() {
        executeInFrame(0, () -> {
            $(ALERT_MESSAGE_HOLDER).should(Condition.appear);
            $(ALERT_MESSAGE_HOLDER).$(CLOSE_ALERT_BUTTON).click();
        });
    }

}
