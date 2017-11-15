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

import com.vmware.admiral.test.ui.pages.common.BasicPage;
import com.vmware.admiral.test.ui.pages.main.AdministrationTab;
import com.vmware.admiral.test.ui.pages.main.AdministrationTabValidator;
import com.vmware.admiral.test.ui.pages.main.GlobalSelectors;
import com.vmware.admiral.test.ui.pages.main.HomeTab;
import com.vmware.admiral.test.ui.pages.main.HomeTabValidator;

public abstract class AdmiralWebClient<P extends AdmiralWebClient<P, V>, V extends AdmiralWebClientValidator<V>>
        extends BasicPage<P, V> {

    public abstract void logIn(String url, String username, String password);

    public abstract void logOut();

    public abstract HomeTab<? extends HomeTab<?, ?>, ? extends HomeTabValidator<?>> navigateToHomeTab();

    public abstract AdministrationTab<? extends AdministrationTab<?, ?>, ? extends AdministrationTabValidator<?>> navigateToAdministrationTab();

    protected void clickHomeIfNotActive() {
        if (!$(GlobalSelectors.HOME_BUTTON).has(Condition.cssClass("active"))) {
            $(GlobalSelectors.HOME_BUTTON).click();
        }
    }

    protected void clickAdministrationIfNotActive() {
        if (!$(GlobalSelectors.ADMINISTRATION_BUTTON).has(Condition.cssClass("active"))) {
            $(GlobalSelectors.ADMINISTRATION_BUTTON).click();
        }
    }

}
