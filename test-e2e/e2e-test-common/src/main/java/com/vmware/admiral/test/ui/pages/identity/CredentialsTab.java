/*
 * Copyright (c) 2018-2020 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.identity;

import static com.codeborne.selenide.Selenide.Wait;

import java.time.Duration;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class CredentialsTab extends BasicPage<CredentialsTabValidator, CredentialsTabLocators> {

    public CredentialsTab(By[] iframeLocators, CredentialsTabValidator validator,
            CredentialsTabLocators pageLocators) {
        super(iframeLocators, validator, pageLocators);
    }

    public void clickAddCredential() {
        LOG.info("Adding credential");
        pageActions().click(locators().addCredentialButton());
    }

    public void deleteCredentials(String credentialsName) {
        LOG.info(String.format("Deleting credentials with name: %s", credentialsName));
        int retries = 3;
        while (retries > 0) {
            pageActions().hover(locators().addCredentialButton());
            pageActions().hover(locators().credentialsRowByName(credentialsName));
            try {
                Wait().withTimeout(Duration.ofSeconds(3)).until(d -> element(locators()
                        .deleteCredentialsButtonByName(credentialsName))
                                .is(Condition.visible));
                pageActions().click(locators().deleteCredentialsButtonByName(credentialsName));
                Wait().withTimeout(Duration.ofSeconds(3)).until(d -> element(locators()
                        .deleteCredentialsConfirmationButtonByName(credentialsName))
                                .is(Condition.visible));
                pageActions().click(locators()
                        .deleteCredentialsConfirmationButtonByName(credentialsName));
                return;
            } catch (TimeoutException e) {
                LOG.warning("Clicking the delete credentials button failed, retrying...");
                retries--;
            }
        }
        throw new RuntimeException("Could not click the delete credentials button");
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        waitForElementToSettle(locators().addCredentialButton());
    }

}
