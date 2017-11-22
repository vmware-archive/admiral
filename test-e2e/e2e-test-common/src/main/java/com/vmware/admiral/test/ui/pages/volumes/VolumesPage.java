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
import static com.codeborne.selenide.Selenide.actions;

import java.util.Objects;

import com.codeborne.selenide.SelenideElement;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.HomeTabAdvancedPage;
import com.vmware.admiral.test.ui.pages.common.PageProxy;

public class VolumesPage extends HomeTabAdvancedPage<VolumesPage, VolumesPageValidator> {

    private final By CREATE_VOLUME_BUTTON = By.cssSelector(".btn.btn-link.create-resource-btn");
    private final By CARD_RELATIVE_DELETE_BUTTON = By.cssSelector(".fa.fa-trash");
    private final By CARD_RELATIVE_DELETE_CONFIRMATION = By
            .cssSelector(".delete-inline-item-confirmation-confirm>div>a");
    private final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");
    private final String VOLUME_CARD_SELECTOR_BY_NAME = "html/body/div/div/div[2]/div[2]/div[1]/div[1]/div/div/div[3]/div/div/div/div/div[3]/div/div[1][starts-with(text(), '%s')]/../../..";

    private VolumesPageValidator validator;
    private CreateVolumePage createVolumePage;

    public CreateVolumePage createVolume() {
        LOG.info("Creating volume");
        if (Objects.isNull(createVolumePage)) {
            createVolumePage = new CreateVolumePage(new PageProxy(this));
        }
        executeInFrame(0, () -> {
            $(CREATE_VOLUME_BUTTON).click();
        });
        createVolumePage.waitToLoad();
        return createVolumePage;
    }

    public VolumesPage deleteVolume(String namePrefix) {
        LOG.info(String.format("Deleting volume with name prefix: [%s]", namePrefix));
        executeInFrame(0, () -> {
            SelenideElement card = waitForElementToStopMoving(getVolumeCardSelector(namePrefix));
            actions().moveToElement(card).click(card.$(CARD_RELATIVE_DELETE_BUTTON))
                    .click(card.$(CARD_RELATIVE_DELETE_CONFIRMATION)).build().perform();
        });
        return this;
    }

    By getVolumeCardSelector(String namePrefix) {
        return By.xpath(String.format(VOLUME_CARD_SELECTOR_BY_NAME, namePrefix));
    }

    @Override
    public VolumesPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new VolumesPageValidator(this);
        }
        return validator;
    }

    @Override
    public VolumesPage refresh() {
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
    public VolumesPage getThis() {
        return this;
    }

}
