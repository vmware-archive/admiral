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
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.actions;

import java.util.List;
import java.util.Objects;

import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.CreateResourcePage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class CreateNetworkPage
        extends CreateResourcePage<CreateNetworkPage, CreateNetworkPageValidator> {

    private final By BACK_BUTTON = By.cssSelector(
            ".create-network.closable-view.slide-and-fade-transition .fa.fa-chevron-circle-left");
    private final By NAME_INPUT = By.cssSelector(".form-group.network-name .form-control");
    private final By ADD_HOST_BUTTON = By
            .cssSelector(".multicolumn-input-add:not([style]):not([href])");
    private final By HOST_DROPDOWNS_AND_BUTTONS_PARENTS = By.cssSelector(
            ".form-group:not(.ipam-config):not(.custom-properties):not(.network-name):not([style]) .multicolumn-input .dropdown-select.dropdown-search-menu");
    private final By CREATE_NETWORK_BUTTON = By.cssSelector(".create-network .btn-primary");
    private final String ROW_RELATIVE_HOST_SELECTOR_BY_NAME = "[role*=\"menuitem\"][data-name$=\"(%s)\"]";

    private CreateNetworkPageValidator validator;
    private CreateNetworkValidator createValidator;
    private PageProxy parentProxy;

    public CreateNetworkPage(PageProxy parentProxy) {
        this.parentProxy = parentProxy;
    }

    public CreateNetworkPage setName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        executeInFrame(0, () -> $(NAME_INPUT).sendKeys(name));
        return this;
    }

    public CreateNetworkPage addHostByName(String hostName) {
        LOG.info(String.format("Adding host by name: [%s]", hostName));
        executeInFrame(0, () -> {
            SelenideElement emptyRow = findEmptyRowOrCreate();
            emptyRow.click();
            SelenideElement host = emptyRow
                    .$(By.cssSelector(String.format(ROW_RELATIVE_HOST_SELECTOR_BY_NAME, hostName)));
            actions().moveToElement(host).click().build().perform();
        });
        return this;
    }

    private SelenideElement findEmptyRowOrCreate() {
        List<SelenideElement> elements = $$(HOST_DROPDOWNS_AND_BUTTONS_PARENTS);
        for (SelenideElement element : elements) {
            SelenideElement button = element.$(By.cssSelector(".dropdown-title.placeholder"));
            if (button.exists()) {
                return element;
            }
        }
        $(ADD_HOST_BUTTON).click();
        return findEmptyRowOrCreate();
    }

    @Override
    public void cancel() {
        LOG.info("Cancelling...");
        executeInFrame(0, () -> $(BACK_BUTTON).click());
        parentProxy.waitToLoad();
    }

    @Override
    public CreateNetworkPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new CreateNetworkPageValidator();
        }
        return validator;
    }

    @Override
    public CreateNetworkValidator submit() {
        LOG.info("Submitting...");
        executeInFrame(0, () -> $(CREATE_NETWORK_BUTTON).click());
        if (Objects.isNull(createValidator)) {
            createValidator = new CreateNetworkValidator();
        }
        return createValidator;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForElementToStopMoving(HomeTabSelectors.CHILD_PAGE_SLIDE));
    }

    @Override
    public CreateNetworkPage getThis() {
        return this;
    }

}
