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

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class BasicTab extends BasicPage<BasicTabValidator, BasicTabLocators> {

    public BasicTab(By[] iFrameLocators, BasicTabValidator validator,
            BasicTabLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void setImage(String image) {
        LOG.info(String.format("Setting image: [%s]", image));
        pageActions().clear(locators().imageInput());
        pageActions().sendKeys(image, locators().imageInput());
    }

    public void setTag(String tag) {
        LOG.info(String.format("Setting tag: [%s]", tag));
        // sometimes sending keys to the tag input does not send all keys
        // so we retry
        int retries = 5;
        while (retries > 0) {
            pageActions().clear(locators().tagInput());
            pageActions().sendKeys(tag, locators().tagInput());
            if (pageActions().getAttribute("value", locators().tagInput()).equals(tag)) {
                return;
            }
            LOG.warning("Setting tag failed, retrying...");
            retries--;
        }
        throw new RuntimeException("Could not set image tag: " + tag);
    }

    public void setName(String name) {
        LOG.info(String.format("Setting container name: [%s]", name));
        pageActions().clear(locators().nameInput());
        pageActions().sendKeys(name, locators().nameInput());
    }

    public void addCommand(String command) {
        LOG.info(String.format("Adding command: [%s]", command));
        if (pageActions().getAttribute("value", locators().lastCommandInput()).isEmpty()) {
            pageActions().click(locators().addCommandButton());
        }
        pageActions().sendKeys(command, locators().lastCommandInput());
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

}
