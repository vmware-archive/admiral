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

package com.vmware.admiral.test.ui.pages.containers;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;

import java.util.Objects;

import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;
import com.vmware.admiral.test.ui.pages.containers.provision.ProvisionAContainerPage;

public class ContainersPage extends HomeTabAdvancedPage<ContainersPage, ContainersPageValidator> {

    private final By CARD_RELATIVE_STOP_BUTTON = By.cssSelector(".fa.fa-stop");
    private final By CARD_RELATIVE_SCALE_BUTTON = By
            .cssSelector(".btn.btn-circle-outline.container-action-scale");

    private ProvisionAContainerPage provisionAContainerPage;
    private ContainersPageValidator validator;

    public ProvisionAContainerPage provisionAContainer() {
        LOG.info("Provisioning a container");
        if (Objects.isNull(provisionAContainerPage)) {
            provisionAContainerPage = new ProvisionAContainerPage(new PageProxy(this));
        }
        executeInFrame(0, () -> $(CREATE_RESOURCE_BUTTON).click());
        provisionAContainerPage.waitToLoad();
        return provisionAContainerPage;
    }

    public ContainersPage stopContainer(String namePrefix) {
        LOG.info(String.format("Stopping container with name prefix: [%s]", namePrefix));
        executeInFrame(0, () -> {
            SelenideElement container = waitForElementToStopMoving(
                    getContainerCardSelector(namePrefix));
            actions().moveToElement(container).moveToElement(container.$(CARD_RELATIVE_STOP_BUTTON))
                    .click().build().perform();
        });
        return this;
    }

    public ContainersPage deleteContainer(String namePrefix) {
        LOG.info(String.format("Deleting container with name prefix: [%s]", namePrefix));
        executeInFrame(0, () -> {
            SelenideElement container = waitForElementToStopMoving(
                    getContainerCardSelector(namePrefix));
            actions().moveToElement(container)
                    .moveToElement(container.$(CARD_RELATIVE_DELETE_BUTTON))
                    .click().build()
                    .perform();
            container.$(CARD_RELATIVE_DELETE_CONFIRMATION_BUTTON).click();
        });
        return this;
    }

    public ContainersPage scaleContainer(String namePrefix) {
        LOG.info(String.format("Scaling container with name prefix: [%s]", namePrefix));
        executeInFrame(0, () -> {
            SelenideElement container = waitForElementToStopMoving(
                    getContainerCardSelector(namePrefix));
            actions().moveToElement(container)
                    .moveToElement(container.$(CARD_RELATIVE_SCALE_BUTTON))
                    .click().build()
                    .perform();
        });
        return getThis();
    }

    By getContainerCardSelector(String name) {
        return By.xpath(String.format(CARD_SELECTOR_BY_NAME_PREFIX_XPATH, name));
    }

    @Override
    public ContainersPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ContainersPageValidator(this);
        }
        return validator;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForSpinner());
    }

    @Override
    public ContainersPage getThis() {
        return this;
    }
}
