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

package com.vmware.admiral.vic.test.ui.pages;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.Wait;
import static com.codeborne.selenide.Selenide.open;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.ExpectedConditions;

import com.vmware.admiral.test.ui.pages.AdmiralWebClient;
import com.vmware.admiral.test.ui.pages.AdmiralWebClientConfiguration;
import com.vmware.admiral.test.ui.pages.main.GlobalSelectors;
import com.vmware.admiral.vic.test.ui.pages.main.VICAdministrationTab;
import com.vmware.admiral.vic.test.ui.pages.main.VICHomeTab;

public class VICWebClient extends AdmiralWebClient<VICWebClient, VICWebClientValidator> {

    private final By ERROR_RESPONSE = By.id("response");
    private final By USERNAME_INPUT = By.id("username");
    private final By PASSWORD_INPUT = By.id("password");
    private final By SUBMIT_BUTTON = By.id("submit");
    private final By CONTENT_CONTAINER = By.cssSelector(".content-container");

    private VICAdministrationTab vicAdministrationTab;
    private VICHomeTab vicHomeTab;
    private VICWebClientValidator validator;

    @Override
    public void logIn(String url, String username, String password) {
        Objects.requireNonNull(url, "url parameter cannot be null");
        Objects.requireNonNull(username, "username parameter cannot be null");
        open(url);
        try {
            $(USERNAME_INPUT).sendKeys(username);
            $(PASSWORD_INPUT).sendKeys(password);
            $(SUBMIT_BUTTON).click();
        } catch (TimeoutException e) {
            throw new RuntimeException("target url is not a VIC url, or login page has changed");
        }
        validateLogIn();
    }

    private void validateLogIn() {
        Wait().withTimeout(AdmiralWebClientConfiguration.getLoginTimeoutSeconds(), TimeUnit.SECONDS)
                .until(ExpectedConditions.or(
                        d -> {
                            return $(GlobalSelectors.LOGGED_USER_DISPLAY).isDisplayed()
                                    && $(CONTENT_CONTAINER).isDisplayed();
                        },
                        d -> {
                            SelenideElement response = $(ERROR_RESPONSE);
                            if (response.isDisplayed()) {
                                throw new AssertionError(response.getText());
                            }
                            return false;
                        }));
    }

    @Override
    public void logOut() {
        $(GlobalSelectors.LOGGED_USER_DISPLAY).click();
        $(GlobalSelectors.LOGOUT_BUTTON).should(Condition.appear).click();
        $(By.id("username")).shouldBe(Condition.visible);
    }

    @Override
    public VICAdministrationTab navigateToAdministrationTab() {
        clickAdministrationIfNotActive();
        if (Objects.isNull(vicAdministrationTab)) {
            vicAdministrationTab = new VICAdministrationTab();
        }
        return vicAdministrationTab;
    }

    @Override
    public VICWebClientValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new VICWebClientValidator();
        }
        return validator;
    }

    @Override
    public VICHomeTab navigateToHomeTab() {
        clickHomeIfNotActive();
        if (Objects.isNull(vicHomeTab)) {
            vicHomeTab = new VICHomeTab();
        }
        return vicHomeTab;
    }

    @Override
    public VICWebClient getThis() {
        return this;
    }

}
