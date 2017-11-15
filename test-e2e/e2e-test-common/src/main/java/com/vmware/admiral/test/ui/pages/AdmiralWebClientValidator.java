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

package com.vmware.admiral.test.ui.pages;

import static com.codeborne.selenide.Selenide.$;

import com.codeborne.selenide.Condition;

import com.vmware.admiral.test.ui.pages.common.ExtendablePageValidator;
import com.vmware.admiral.test.ui.pages.main.GlobalSelectors;

public abstract class AdmiralWebClientValidator<V extends AdmiralWebClientValidator<V>>
        extends ExtendablePageValidator<V> {

    @Override
    public V validateIsCurrentPage() {
        return getThis();
    }

    public V validateAdministrationTabIsNotVisible() {
        $(GlobalSelectors.ADMINISTRATION_BUTTON).shouldNotBe(Condition.visible);
        return getThis();
    }

    public V validateAdministrationTabIsVisible() {
        $(GlobalSelectors.ADMINISTRATION_BUTTON).shouldBe(Condition.visible);
        return getThis();
    }

}