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

package com.vmware.admiral.test.ui.pages.projects;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicClass;
import com.vmware.admiral.test.ui.pages.common.FailableActionValidator;

public class AddProjectModalDialogueValidator extends BasicClass
        implements FailableActionValidator {

    private final By ALERT_TEXT = By.cssSelector(".modal-content .alert-text");
    private final By MODAL_BACKDROP = By.cssSelector(".modal-backdrop");
    private final By ALERT_CLOSE_BUTTON = By.cssSelector(".alert.alert-danger .close");

    private final String INVALID_NAME_ERROR_MESSAGE = "Project name is allowed to contain alpha-numeric characters, periods, dashes and underscores, lowercase only.";

    @Override
    public void expectSuccess() {
        $(MODAL_BACKDROP).should(Condition.disappear);
        waitForSpinner();
    }

    @Override
    public void expectFailure() {
        validateErrorAndGetMessage();
        closeErrorMessage();
    }

    public void expectInvalidNameErrorMessage() {
        errorMessage(INVALID_NAME_ERROR_MESSAGE);
    }

    private String validateErrorAndGetMessage() {
        String message = $(ALERT_TEXT).shouldBe(Condition.visible).getText();
        return message;
    }

    private void closeErrorMessage() {
        $(ALERT_CLOSE_BUTTON).click();
        $(ALERT_TEXT).should(Condition.disappear);
    }

    public void errorMessage(String message) {
        String errorMessage = validateErrorAndGetMessage();
        if (!errorMessage.contains(message)) {
            throw new AssertionError(
                    String.format("Error message mismatch, expected: [%s], actual: [%s]", message,
                            errorMessage));
        }
        closeErrorMessage();
    }

}
