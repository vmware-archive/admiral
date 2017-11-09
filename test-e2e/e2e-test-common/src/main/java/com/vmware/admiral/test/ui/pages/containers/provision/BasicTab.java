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
import static com.codeborne.selenide.Selenide.$$;

import com.codeborne.selenide.ElementsCollection;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;

public class BasicTab extends ProvisionAContainerPage {

    private final By IMAGE_FIELD = By
            .cssSelector(".image-name-input.search-input .form-control.tt-input");
    private final By NAME_FIELD = By.cssSelector(".form-group.container-name-input .form-control");
    private final By TAG_FIELD = By
            .cssSelector(".image-tags-input.search-input .form-control.tt-input");
    private final By ADD_COMMAND_BUTTON = By
            .cssSelector(
                    ".form-group.container-commands-input .multicolumn-input-add:not([style*=\"hidden\"])");
    private final By ALL_INPUT_ROWS = By
            .cssSelector(".inline-input.form-control[name*=\"command\"]");

    public BasicTab setImage(String image) {
        executeInFrame(0, () -> $(IMAGE_FIELD).sendKeys(image));
        return this;
    }

    public BasicTab setTag(String tag) {
        executeInFrame(0, () -> $(TAG_FIELD).sendKeys(tag));
        return this;
    }

    public BasicTab setName(String name) {
        executeInFrame(0, () -> {
            $(NAME_FIELD).clear();
            $(NAME_FIELD).sendKeys(name);
        });
        return this;
    }

    public BasicTab addCommand(String command) {
        executeInFrame(0, () -> findOrCreateEmptyRow().sendKeys(command));
        return this;
    }

    private WebElement findOrCreateEmptyRow() {
        ElementsCollection rows = $$(ALL_INPUT_ROWS);
        if (rows.last().getAttribute("value").isEmpty()) {
            return rows.last();
        }
        $(ADD_COMMAND_BUTTON).click();
        return $$(ALL_INPUT_ROWS).last();
    }

    @Override
    public ProvisionAContainerPageValidator validate() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ProvisionAContainerPage getThis() {
        return this;
    }

}
