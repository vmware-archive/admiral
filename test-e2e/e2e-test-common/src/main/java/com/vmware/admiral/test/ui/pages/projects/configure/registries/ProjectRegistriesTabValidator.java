/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.projects.configure.registries;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageValidator;

public class ProjectRegistriesTabValidator extends PageValidator<ProjectRegistriesTabLocators> {

    public ProjectRegistriesTabValidator(By[] iFrameLocators,
            ProjectRegistriesTabLocators pageLocators) {
        super(iFrameLocators, pageLocators);
    }

    @Override
    public void validateIsCurrentPage() {
        element(locators().projectRegistriesTabButton()).shouldHave(Condition.cssClass("active"));
    }

    public void validateRegistryExistsWithName(String name) {
        element(locators().registryRowByName(name)).should(Condition.exist);
    }

    public void validateRegistryDoesNotExistWithName(String name) {
        element(locators().registryRowByName(name)).shouldNot(Condition.exist);
    }

}
