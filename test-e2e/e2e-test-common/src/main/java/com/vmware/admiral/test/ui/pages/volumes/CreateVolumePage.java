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

package com.vmware.admiral.test.ui.pages.volumes;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.CreateResourcePage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class CreateVolumePage
        extends CreateResourcePage<CreateVolumePage, CreateVolumePageValidator> {

    private final By NAME_INPUT = By.cssSelector(".form-group.volume-name .form-control");
    private final By DRIVER_INPUT = By.cssSelector(".form-group.volume-driver .form-control");
    private final String HOST_DROPDOWNS_CONTAINER_CSS = ".form-group:not(.ipam-config):not(.custom-properties):not(.network-name):not([style]) .multicolumn-input .dropdown-select.dropdown-search-menu";
    private final By SELECT_HOST_DROPDOWN_BUTTON = By
            .cssSelector(HOST_DROPDOWNS_CONTAINER_CSS + " button.dropdown-toggle");
    private final By CREATE_VOLUME_BUTTON = By.cssSelector(".btn.btn-primary");
    private final By BACK_BUTTON = By
            .cssSelector(
                    ".create-volume.closable-view.slide-and-fade-transition .fa.fa-chevron-circle-left");

    private PageProxy parentProxy;

    private CreateVolumePageValidator validator;
    private CreateVolumeValidator createValidator;

    public CreateVolumePage(PageProxy parentProxy) {
        this.parentProxy = parentProxy;
    }

    public CreateVolumePage setName(String name) {
        LOG.info(String.format("Setting name: [%s]", name));
        executeInFrame(0, () -> $(NAME_INPUT).sendKeys(name));
        return this;
    }

    public CreateVolumePage setDriver(String driver) {
        LOG.info(String.format("Setting driver: [%s]", driver));
        executeInFrame(0, () -> $(DRIVER_INPUT).sendKeys(driver));
        return this;
    }

    public CreateVolumePage selectHostByName(String hostName) {
        LOG.info(String.format("Setting host by name: [%s]", hostName));
        executeInFrame(0, () -> {
            $(SELECT_HOST_DROPDOWN_BUTTON).click();
            $(By.cssSelector(String.format(".host-picker-item-primary[title$=\"(%s)\"]", hostName)))
                    .click();
        });
        return this;
    }

    @Override
    public CreateVolumePageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new CreateVolumePageValidator();
        }
        return validator;
    }

    @Override
    public void cancel() {
        LOG.info("Cancelling...");
        executeInFrame(0, () -> $(BACK_BUTTON).click());
        parentProxy.waitToLoad();
    }

    @Override
    public CreateVolumeValidator submit() {
        LOG.info("Submitting...");
        executeInFrame(0, () -> $(CREATE_VOLUME_BUTTON).click());
        if (Objects.isNull(createValidator)) {
            createValidator = new CreateVolumeValidator();
        }
        return createValidator;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
        executeInFrame(0, () -> waitForElementToStopMoving(HomeTabSelectors.CHILD_PAGE_SLIDE));
    }

    @Override
    public CreateVolumePage getThis() {
        return this;
    }

}
