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

package com.vmware.admiral.test.ui.pages.containers.provision;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicClass;
import com.vmware.admiral.test.ui.pages.common.FailableActionValidator;

public class ProvisionValidator extends BasicClass implements FailableActionValidator {

    private final By NAME_INPUT_WRAPPER = By.cssSelector(".form-group.container-name-input");
    private final By PROVISION_CONTAINER_SLIDER_PAGE = By
            .cssSelector(".create-container.closable-view.slide-and-fade-transition");
    private final String INPUT_ERROR_CLASS = "has-error";

    @Override
    public void expectSuccess() {
        executeInFrame(0, () -> {
            $(NAME_INPUT_WRAPPER).shouldNotHave(Condition.cssClass(INPUT_ERROR_CLASS));
            $(PROVISION_CONTAINER_SLIDER_PAGE).should(Condition.disappear);
        });
    }

    @Override
    public void expectFailure() {
        executeInFrame(0, () -> $(By.cssSelector("." + INPUT_ERROR_CLASS)).should(Condition.exist));
    }

    public void expectNameFieldValueIsRequiredError() {
        executeInFrame(0,
                () -> $(NAME_INPUT_WRAPPER).shouldHave(Condition.cssClass(INPUT_ERROR_CLASS))
                        .shouldHave(Condition.text("Value is required")));
    }

    public void expectInvalidNameError() {
        executeInFrame(0,
                () -> $(NAME_INPUT_WRAPPER).shouldHave(Condition.cssClass(INPUT_ERROR_CLASS))
                        .shouldHave(Condition.text("Invalid container name")));
    }
}