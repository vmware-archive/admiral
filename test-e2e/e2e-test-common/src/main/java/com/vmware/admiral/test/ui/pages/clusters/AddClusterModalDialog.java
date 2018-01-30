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

package com.vmware.admiral.test.ui.pages.clusters;

import com.codeborne.selenide.Condition;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialog;

public class AddClusterModalDialog
        extends ModalDialog<AddClusterModalDialogValidator, AddClusterModalDialogLocators> {

    public AddClusterModalDialog(By[] iFrameLocators, AddClusterModalDialogValidator validator,
            AddClusterModalDialogLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public static enum HostType {
        VCH, DOCKER;
    }

    public void setName(String name) {
        LOG.info(String.format("Setting host name: [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void setDescription(String description) {
        LOG.info(String.format("Setting description: [%s]", description));
        pageActions().clear(locators().descriptionInput());
        pageActions().sendKeys(description, locators().descriptionInput());
    }

    public void setHostType(HostType hostType) {
        LOG.info(String.format("Setting host type: [%s]", hostType.toString()));
        if (hostType == HostType.DOCKER) {
            pageActions().selectOptionByValue(HostType.DOCKER.toString(),
                    locators().hostTypeOptions());
        } else {
            pageActions().selectOptionByValue(HostType.VCH.toString(),
                    locators().hostTypeOptions());
        }
    }

    public void setUrl(String url) {
        LOG.info(String.format("Setting host url: [%s]", url));
        pageActions().clear(locators().urlInput());
        pageActions().sendKeys(url, locators().urlInput());
    }

    public void closeErrorMessage() {
        pageActions().click(locators().errorMessageCloseButton());
        element(locators().errorMessageCloseButton()).should(Condition.disappear);
    }

}
