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

package com.vmware.admiral.test.ui.pages.hosts;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.Wait;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.codeborne.selenide.Condition;
import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.AdmiralWebClientConfiguration;
import com.vmware.admiral.test.ui.pages.common.HomeTabPage;
import com.vmware.admiral.test.ui.pages.main.GlobalSelectors;

public class ContainerHostsPage
        extends HomeTabPage<ContainerHostsPage, ContainerHostsPageValidator> {

    private final String ALL_CONTAINER_HOST_NAMES_XPATH = "html/body/my-app/clr-main-container/div/app-main-resources/div[1]/app-clusters/grid-view/div[3]/div/span/card/div/div[1]/div/div[2]/div";
    private final By ADD_CONTAINER_HOST_BUTTON = By
            .cssSelector(".col-sm-6.toolbar-primary>div .btn.btn-link");
    private final By CARD_RELATIVE_DROPDOWN_MENU = By
            .cssSelector(".btn.btn-sm.btn-link.dropdown-toggle");
    private final By CARD_RELATIVE_DELETE_BUTTON = By.cssSelector(".dropdown-item:nth-child(2)");
    private final By DELETE_HOST_CONFIRMATION_BUTTON = By
            .cssSelector(".modal-content .btn.btn-danger");
    private final By DELETE_CONTAINER_MODAL_BACKDROP = By.cssSelector(".modal-backdrop");
    private final By REFRESH_BUTTON = By.cssSelector(".btn.btn-link[title=\"Refresh\"]");

    private ContainerHostsPageValidator validator;

    private AddHostModalDialogue addHostModalDialogue;

    public AddHostModalDialogue addContainerHost() {
        $(ADD_CONTAINER_HOST_BUTTON).click();
        waitForElementToStopMoving($(GlobalSelectors.MODAL_CONTENT));
        if (Objects.isNull(addHostModalDialogue)) {
            addHostModalDialogue = new AddHostModalDialogue();
        }
        return addHostModalDialogue;
    }

    public ContainerHostsPage deleteContainerHost(String name) {
        SelenideElement host = getHostCard(name);
        waitForElementToStopMoving(host);
        host.$(CARD_RELATIVE_DROPDOWN_MENU).click();
        host.$(CARD_RELATIVE_DELETE_BUTTON).click();
        waitForElementToStopMoving($(DELETE_HOST_CONFIRMATION_BUTTON)).click();
        Wait().withTimeout(AdmiralWebClientConfiguration.getDeleteHostTimeoutSeconds(),
                TimeUnit.SECONDS)
                .until(d -> {
                    return $(DELETE_CONTAINER_MODAL_BACKDROP).is(Condition.hidden);
                });
        return this;
    }

    SelenideElement getHostCard(String name) {
        return $(By.xpath(ALL_CONTAINER_HOST_NAMES_XPATH + "[text() = \"" + name + "\"]")).parent()
                .parent().parent().parent().parent();
    }

    @Override
    public ContainerHostsPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ContainerHostsPageValidator(this);
        }
        return validator;
    }

    @Override
    public ContainerHostsPage refresh() {
        $(REFRESH_BUTTON).click();
        return this;
    }

    @Override
    public ContainerHostsPage getThis() {
        return this;
    }

}
