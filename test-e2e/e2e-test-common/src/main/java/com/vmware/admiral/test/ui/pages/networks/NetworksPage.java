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

package com.vmware.admiral.test.ui.pages.networks;

import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.actions;

import java.util.Objects;

import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;

public class NetworksPage extends HomeTabAdvancedPage<NetworksPage, NetworksPageValidator> {

    private final By CREATE_NETWORK_BUTTON = By
            .cssSelector(".btn.btn-link.create-resource-btn[href*=\"networks/new\"]");
    final By CARD_RELATIVE_DELETE_BUTTON = By.cssSelector(".fa.fa-trash");
    private final By CARD_RELATIVE_DELETE_CONFIRMATION = By
            .cssSelector(".delete-inline-item-confirmation-confirm>div>a");
    private final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");
    private final String NETWORK_CARD_SELECTOR_BY_NAME = "html/body/div/div/div[2]/div[2]/div[1]/div[1]/div/div/div[3]/div/div/div/div/div[3]/div/div[1][starts-with(text(), '%s')]/../../..";

    private NetworksPageValidator validator;
    private CreateNetworkPage createNetworkPage;

    public CreateNetworkPage createNetwork() {
        LOG.info("Creating network");
        if (Objects.isNull(createNetworkPage)) {
            createNetworkPage = new CreateNetworkPage(new PageProxy(this));
        }
        executeInFrame(0, () -> {
            $(CREATE_NETWORK_BUTTON).click();
        });
        createNetworkPage.waitToLoad();
        return createNetworkPage;
    }

    public NetworksPage deleteNetwork(String namePrefix) {
        LOG.info(String.format("Deleting network with name prefix: [%s]", namePrefix));
        executeInFrame(0, () -> {
            SelenideElement card = waitForElementToStopMoving(getNetworkCardSelector(namePrefix));
            actions().moveToElement(card).click(card.$(CARD_RELATIVE_DELETE_BUTTON)).build()
                    .perform();
            card.$(CARD_RELATIVE_DELETE_CONFIRMATION).click();
        });
        return this;
    }

    By getNetworkCardSelector(String namePrefix) {
        return By.xpath(String.format(NETWORK_CARD_SELECTOR_BY_NAME, namePrefix));
    }

    @Override
    public NetworksPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new NetworksPageValidator(this);
        }
        return validator;
    }

    @Override
    public NetworksPage refresh() {
        LOG.info("Refreshing...");
        executeInFrame(0, () -> {
            $(REFRESH_BUTTON).click();
            waitForSpinner();
        });
        return this;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForSpinner());
    }

    @Override
    public NetworksPage getThis() {
        return this;
    }

}
