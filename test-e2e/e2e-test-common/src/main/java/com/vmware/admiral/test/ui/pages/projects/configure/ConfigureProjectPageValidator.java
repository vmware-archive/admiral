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

package com.vmware.admiral.test.ui.pages.projects.configure;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class ConfigureProjectPageValidator extends PageValidator {

    private final By BACK_BUTTON = By.xpath(
            "html/body/my-app/clr-main-container/div/app-administration/div/app-projects/navigation-container/div/back-button/a/span");

    @Override
    public ConfigureProjectPageValidator validateIsCurrentPage() {
        $(BACK_BUTTON).shouldBe(Condition.visible);
        return this;
    }

}
