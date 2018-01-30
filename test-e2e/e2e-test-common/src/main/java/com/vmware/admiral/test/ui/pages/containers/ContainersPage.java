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

package com.vmware.admiral.test.ui.pages.containers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePage;

public class ContainersPage extends ResourcePage<ContainersPageValidator, ContainersPageLocators> {

    public ContainersPage(By[] iFrameLocators, ContainersPageValidator validator,
            ContainersPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickCreateContainer() {
        LOG.info("Creating a container");
        pageActions().click(locators().createResourceButton());
    }

    public void stopContainer(String namePrefix) {
        LOG.info(String.format("Stopping container with name prefix: [%s]", namePrefix));
        By card = locators().cardByTitlePrefix(namePrefix);
        waitForElementToSettle(card);
        pageActions().hover(card);
        pageActions().click(locators().cardStopButtonByTitlePrefix(namePrefix));
    }

    public void deleteContainer(String namePrefix) {
        LOG.info(String.format("Deleting container with name prefix: [%s]", namePrefix));
        deleteItemByTitlePrefix(namePrefix);
    }

    public void scaleContainer(String namePrefix) {
        LOG.info(String.format("Scaling container with name prefix: [%s]", namePrefix));
        By card = locators().cardByTitlePrefix(namePrefix);
        waitForElementToSettle(card);
        pageActions().hover(card);
        pageActions().click(locators().cardScaleButtonByTitlePrefix(namePrefix));
    }

    public void inspectContainer(String namePrefix) {
        LOG.info(String.format("Inspecting container with name prefix: [%s]", namePrefix));
        By card = locators().cardByTitlePrefix(namePrefix);
        waitForElementToSettle(card);
        pageActions().hover(card);
        pageActions().click(locators().cardInspectButtonByTitlePrefix(namePrefix));
    }

    public List<String> getContainerPortSettings(String namePrefix) {
        String text = pageActions().getText(locators().cardPortsHolder(namePrefix));
        if (text.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(text.split("\n"));
    }

    public static enum ContainerState {
        UNKNOWN, PROVISIONING, RUNNING, PAUSED, STOPPED, RETIRED, ERROR;
    }

}
