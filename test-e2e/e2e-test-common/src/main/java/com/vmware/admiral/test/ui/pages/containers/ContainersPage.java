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

package com.vmware.admiral.test.ui.pages.containers;

import static com.codeborne.selenide.Selenide.Wait;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.codeborne.selenide.Condition;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;

import com.vmware.admiral.test.ui.pages.common.ResourcePage;

public class ContainersPage extends ResourcePage<ContainersPageValidator, ContainersPageLocators> {

    public ContainersPage(By[] iFrameLocators, ContainersPageValidator validator,
            ContainersPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void clickCreateContainer() {
        LOG.info("Creating a container");
        int retries = 3;
        while (retries > 0) {
            pageActions().click(locators().createResourceButton());
            try {
                Wait().withTimeout(Duration.ofSeconds(5))
                        .until(d -> element(locators().childPageSlide()).is(Condition.visible));
                return;
            } catch (TimeoutException e) {
                LOG.info("Clicking on the create container button failed, retrying...");
                retries--;
            }
        }
        throw new RuntimeException("Could not click on the create contaienr button");
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

    public void waitForContainerStateByNamePrefix(String namePrefix, ContainerState state,
            int timeoutSeconds) {
        LOG.info(
                String.format(
                        "Waiting [%d] seconds for container witn name prefix [%s] to become in state [%s]",
                        timeoutSeconds, namePrefix, state.toString()));
        waitForContainerState(locators().cardHeaderByTitlePrefix(namePrefix), state,
                timeoutSeconds);
    }

    public void waitForContainerStateByExactName(String name, ContainerState state,
            int timeoutSeconds) {
        LOG.info(
                String.format(
                        "Waiting [%d] seconds for container witn name [%s] to become in state [%s]",
                        timeoutSeconds, name, state.toString()));
        waitForContainerState(locators().cardHeaderByExactTitle(name), state,
                timeoutSeconds);
    }

    private void waitForContainerState(By containerTitleLocator, ContainerState state,
            int timeoutSeconds) {
        if (state == ContainerState.RUNNING) {
            Wait().withTimeout(Duration.ofSeconds(timeoutSeconds))
                    .until(d -> pageActions()
                            .getText(containerTitleLocator).trim()
                            .startsWith(state.toString()));
        } else {
            Wait().withTimeout(Duration.ofSeconds(timeoutSeconds))
                    .until(d -> pageActions()
                            .getText(containerTitleLocator).trim()
                            .equals(state.toString()));
        }
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
