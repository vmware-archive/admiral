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

package com.vmware.admiral.test.ui.pages.hosts;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.Wait;

import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.vmware.admiral.test.ui.pages.AdmiralWebClientConfiguration;
import com.vmware.admiral.test.ui.pages.common.BasicClass;
import com.vmware.admiral.test.ui.pages.common.FailableActionValidator;
import com.vmware.admiral.test.ui.pages.main.GlobalSelectors;

public class AddHostModalDialogueValidator extends BasicClass implements FailableActionValidator {

    private final String MODAL_BASE = ".modal-content";
    private final By ADD_HOST_ERROR_MESSAGE_DIV = By.cssSelector(".modal-content .alert-text");
    private final By ADD_HOST_ERROR_MESSAGE_CLOSE_BUTTON = By
            .cssSelector(".modal-body .alert.alert-danger .close");
    private final By CERTIFICATE_CONFIRMATION_DIALOGUE = By
            .cssSelector(MODAL_BASE + " " + MODAL_BASE);
    private final By CERTIFICATE_CONFIRMATION_BUTTON = By
            .cssSelector(".btn.btn-primary.save-certificate-btn");

    @Override
    public void expectSuccess() {
        Wait().withTimeout(AdmiralWebClientConfiguration.getAddHostTimeoutSeconds(),
                TimeUnit.SECONDS)
                .until(f -> {
                    return $(GlobalSelectors.MODAL_BACKDROP).is(Condition.hidden)
                            && $(GlobalSelectors.SPINNER).is(Condition.hidden);
                });
    }

    @Override
    public void expectFailure() {
        validateErrorAndGetMessage();
        closeErrorMessage();
    }

    private String validateErrorAndGetMessage() {
        String message = $(ADD_HOST_ERROR_MESSAGE_DIV).shouldBe(Condition.visible).getText();
        return message;
    }

    public void acceptCertificateIfShownAndExpectSuccess() {
        Wait().withTimeout(AdmiralWebClientConfiguration.getAddHostTimeoutSeconds(),
                TimeUnit.SECONDS)
                .until(ExpectedConditions.or(
                        d -> {
                            return $(GlobalSelectors.MODAL_BACKDROP).is(Condition.hidden)
                                    && $(GlobalSelectors.SPINNER).is(Condition.hidden);
                        },
                        d -> {
                            if ($(CERTIFICATE_CONFIRMATION_DIALOGUE).is(Condition.visible)) {
                                waitForElementToStopMoving($(CERTIFICATE_CONFIRMATION_BUTTON))
                                        .click();
                                expectSuccess();
                                return true;
                            }
                            return false;
                        }));
    }

    private void closeErrorMessage() {
        $(ADD_HOST_ERROR_MESSAGE_CLOSE_BUTTON).click();
        $(ADD_HOST_ERROR_MESSAGE_CLOSE_BUTTON).should(Condition.disappear);
    }

    public void expectErrorMessage(String message) {
        String errorMessage = validateErrorAndGetMessage();
        if (!errorMessage.contains(message)) {
            throw new AssertionError(
                    String.format("Error message mismatch, expected: [%s], actual: [%s]", message,
                            errorMessage));
        }
        closeErrorMessage();
    }

}
