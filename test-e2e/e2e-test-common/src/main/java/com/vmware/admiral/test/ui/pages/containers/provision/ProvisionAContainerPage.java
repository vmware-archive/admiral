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

import java.util.Objects;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

import com.vmware.admiral.test.ui.pages.common.CreateResourcePage;

public class ProvisionAContainerPage
        extends CreateResourcePage<ProvisionAContainerPage, ProvisionAContainerPageValidator> {

    private final By BASIC_TAB_BUTTON = By
            .cssSelector(".nav .nav-item:nth-child(1) .nav-link");
    private final By PROVISION_BUTTON = By.cssSelector(".btn.btn-primary");
    private final By BACK_BUTTON = By
            .cssSelector(
                    ".create-container.closable-view.slide-and-fade-transition .fa.fa-chevron-circle-left");
    private ProvisionAContainerPageValidator validator;

    private ProvisionValidator provisionValidator;
    private BasicTab basicTab;

    public BasicTab navigateToBasicTab() {
        executeInFrame(0, () -> {
            WebElement basicTabButton = $(BASIC_TAB_BUTTON);
            if (!basicTabButton.getAttribute("class").contains("active")) {
                basicTabButton.click();
            }
        });
        if (Objects.isNull(basicTab)) {
            basicTab = new BasicTab();
        }
        return basicTab;
    }

    @Override
    public void cancel() {
        executeInFrame(0, () -> $(BACK_BUTTON).click());
    }

    @Override
    public ProvisionValidator submit() {
        executeInFrame(0, () -> $(PROVISION_BUTTON).click());
        if (Objects.isNull(provisionValidator)) {
            provisionValidator = new ProvisionValidator();
        }
        return provisionValidator;
    }

    @Override
    public ProvisionAContainerPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ProvisionAContainerPageValidator();
        }
        return validator;
    }

    @Override
    public ProvisionAContainerPage getThis() {
        return this;
    }

}
