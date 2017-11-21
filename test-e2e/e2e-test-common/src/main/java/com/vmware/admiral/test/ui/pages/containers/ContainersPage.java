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
import com.vmware.admiral.test.ui.pages.containers.provision.ProvisionAContainerPage;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class ContainersPage extends HomeTabAdvancedPage<ContainersPage, ContainersPageValidator> {

    private final By NEW_CONTAINER_BUTTON = By.cssSelector(".btn.btn-link.create-resource-btn");
    private final By CARD_RELATIVE_STOP_BUTTON = By.cssSelector(".fa.fa-stop");
    private final By CARD_RELATIVE_DELETE_BUTTON = By.cssSelector(".fa.fa-trash");
    private final By CARD_RELATIVE_DELETE_CONFIRMATION_BUTTON = By
            .cssSelector(".delete-inline-item-confirmation-confirm>div>a");
    private final By CARD_RELATIVE_SCALE_BUTTON = By
            .cssSelector(".btn.btn-circle-outline.container-action-scale");
    private final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");
    private final String CONTAINER_CARD_SELECTOR_BY_NAME = ".container-item .container-header .title[title^=\"%s\"]";

    private ProvisionAContainerPage provisionAContainerPage;
    private ContainersPageValidator validator;

    public ProvisionAContainerPage provisionAContainer() {
        LOG.info("Creating a container");
        executeInFrame(0, () -> {
            $(NEW_CONTAINER_BUTTON).click();
            waitForElementToStopMoving($(HomeTabSelectors.CHILD_PAGE_SLIDE));
        });
        if (Objects.isNull(provisionAContainerPage)) {
            provisionAContainerPage = new ProvisionAContainerPage();
        }
        return provisionAContainerPage;
    }

    public ContainersPage stopContainer(String namePrefix) {
        LOG.info(String.format("Stopping container with name prefix: [%s]", namePrefix));
        executeInFrame(0, () -> {
            SelenideElement container = getContainerCard(namePrefix);
            waitForElementToStopMoving(container);
            actions().moveToElement(container).moveToElement(container.$(CARD_RELATIVE_STOP_BUTTON))
                    .click().build().perform();
        });
        return this;
    }

    public ContainersPage deleteContainer(String namePrefix) {
        LOG.info(String.format("Deleting container with name prefix: [%s]", namePrefix));
        executeInFrame(0, () -> {
            SelenideElement container = getContainerCard(namePrefix);
            waitForElementToStopMoving(container);
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
            SelenideElement container = getContainerCard(namePrefix);
            waitForElementToStopMoving(container);
            actions().moveToElement(container)
                    .moveToElement(container.$(CARD_RELATIVE_SCALE_BUTTON))
                    .click().build()
                    .perform();
        });
        return getThis();
    }

    SelenideElement getContainerCard(String name) {
        return $(By.cssSelector(String.format(CONTAINER_CARD_SELECTOR_BY_NAME, name))).parent()
                .parent().parent();
    }

    @Override
    public ContainersPage refresh() {
        LOG.info("Refreshing...");
        executeInFrame(0, () -> $(REFRESH_BUTTON).click());
        return getThis();
    }

    @Override
    public ContainersPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ContainersPageValidator(this);
        }
        return validator;
    }

    @Override
    public ContainersPage getThis() {
        return this;
    }
}
