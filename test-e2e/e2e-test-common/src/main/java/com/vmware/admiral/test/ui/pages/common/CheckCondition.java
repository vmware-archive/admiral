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

package com.vmware.admiral.test.ui.pages.common;

import static com.codeborne.selenide.Selenide.switchTo;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;

public class CheckCondition extends Action {

    private By selector;

    public CheckCondition(By selector, By[] iframeLocators) {
        super(iframeLocators);
        this.selector = selector;
    }

    public CheckCondition should(Condition... conditions) {
        switchToFrame();
        getElement(selector).should(conditions);
        switchTo().defaultContent();
        return this;
    }

    public CheckCondition shouldBe(Condition... conditions) {
        return should(conditions);
    }

    public CheckCondition shouldHave(Condition... conditions) {
        return should(conditions);
    }

    public CheckCondition shouldNot(Condition... conditions) {
        switchToFrame();
        getElement(selector).shouldNot(conditions);
        switchTo().defaultContent();
        return this;
    }

    public CheckCondition shouldNotBe(Condition... conditions) {
        return shouldNot(conditions);
    }

    public CheckCondition shouldNotHave(Condition... conditions) {
        return shouldNot(conditions);
    }

    public boolean has(Condition condition) {
        switchToFrame();
        boolean has = getElement(selector).has(condition);
        switchTo().defaultContent();
        return has;
    }

    public boolean is(Condition condition) {
        switchToFrame();
        boolean is = getElement(selector).is(condition);
        switchTo().defaultContent();
        return is;
    }
}
