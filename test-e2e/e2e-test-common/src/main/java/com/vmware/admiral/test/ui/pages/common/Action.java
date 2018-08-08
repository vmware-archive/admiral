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

package com.vmware.admiral.test.ui.pages.common;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.switchTo;

import java.util.Objects;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

public class Action {

    private final By[] iframeLocators;

    public Action(final By[] iframeLocators) {
        if (Objects.nonNull(iframeLocators)) {
            this.iframeLocators = iframeLocators.clone();
        } else {
            this.iframeLocators = null;
        }
    }

    protected ElementsCollection getElements(By selector) {
        return $$(selector);
    }

    protected SelenideElement getElement(By selector) {
        ElementsCollection elements = getElements(selector);
        if (elements.size() > 1) {
            throw new IllegalArgumentException(String.format(
                    "Multiple elements found with selector: [%s], modify the selector to locate only a single element",
                    selector));
        }
        if (elements.size() == 1) {
            return elements.get(0);
        }
        return $(selector);
    }

    protected void switchToFrame() {
        switchTo().defaultContent();
        if (Objects.isNull(iframeLocators) || iframeLocators.length == 0) {
            return;
        }
        for (By frame : iframeLocators) {
            switchTo().frame($(frame));
        }
    }

}
