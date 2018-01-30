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

package com.vmware.admiral.test.ui.pages.containers.create;

import org.openqa.selenium.By;

public class BasicTabLocators extends CreateContainerPageLocators {

    private final By IMAGE_FIELD = By
            .cssSelector(".image-name-input.search-input .form-control.tt-input");
    private final By NAME_FIELD = By.cssSelector(".form-group.container-name-input .form-control");
    private final By TAG_FIELD = By
            .cssSelector(".image-tags-input.search-input .form-control.tt-input");
    private final By ADD_COMMAND_BUTTON = By
            .cssSelector(
                    ".form-group.container-commands-input .multicolumn-input-add:not([style*=\"hidden\"])");
    private final By LAST_COMMAND_INPUT = By
            .cssSelector(
                    ".multicolumn-input:last-child .inline-input.form-control[name='command']");

    public By imageInput() {
        return IMAGE_FIELD;
    }

    public By nameInput() {
        return NAME_FIELD;
    }

    public By tagInput() {
        return TAG_FIELD;
    }

    public By addCommandButton() {
        return ADD_COMMAND_BUTTON;
    }

    public By lastCommandInput() {
        return LAST_COMMAND_INPUT;
    }

}
