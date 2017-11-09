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

package com.vmware.admiral.test.ui.pages.main;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import com.vmware.admiral.test.ui.pages.common.ExtendablePageValidator;

public abstract class AdministrationTabValidator<V extends AdministrationTabValidator<V>>
        extends ExtendablePageValidator<V> {

    @Override
    public V validateIsCurrentPage() {
        $(GlobalSelectors.ADMINISTRATION_BUTTON).shouldHave(Condition.cssClass("active"));
        return getThis();
    }

    public V validateIdentityManagementNotAvailable() {
        $(AdministrationTabSelectors.IDENTITY_MANAGEMENT_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateRegistriesNotAvailable() {
        $(AdministrationTabSelectors.REGISTRIES_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateLogsNotAvailable() {
        $(AdministrationTabSelectors.LOGS_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateProjectsAvailable() {
        $(AdministrationTabSelectors.PROJECTS_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateIdentityManagementAvailable() {
        $(AdministrationTabSelectors.IDENTITY_MANAGEMENT_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateRegistriesAvailable() {
        $(AdministrationTabSelectors.REGISTRIES_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public V validateLogsAvailable() {
        $(AdministrationTabSelectors.LOGS_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

    public static class DefaultAdministrationTabValidator
            extends AdministrationTabValidator<DefaultAdministrationTabValidator> {

        @Override
        public DefaultAdministrationTabValidator getThis() {
            return this;
        }

    }
}
