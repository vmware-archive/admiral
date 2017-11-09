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
import com.vmware.admiral.test.ui.pages.main.HomeTabSelectors;

public class VolumesPage extends HomeTabAdvancedPage<VolumesPage, VolumesPageValidator> {

    private final By CREATE_VOLUME_BUTTON = By.cssSelector(".btn.btn-link.create-resource-btn");
    private final By CARD_RELATIVE_DELETE_BUTTON = By.cssSelector(".fa.fa-trash");
    private final By CARD_RELATIVE_DELETE_CONFIRMATION = By
            .cssSelector(".delete-inline-item-confirmation-confirm>div>a");
    private final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");

    private VolumesPageValidator validator;
    private CreateVolumePage createVolumePage;

    public CreateVolumePage createVolume() {
        executeInFrame(0, () -> {
            $(CREATE_VOLUME_BUTTON).click();
            waitForElementToStopMoving($(HomeTabSelectors.CHILD_PAGE_SLIDE));
        });
        if (Objects.isNull(createVolumePage)) {
            createVolumePage = new CreateVolumePage();
        }
        return createVolumePage;
    }

    public VolumesPage deleteVolume(String namePrefix) {
        executeInFrame(0, () -> {
            SelenideElement card = getVolumeCard(namePrefix);
            waitForElementToStopMoving(card);
            actions().moveToElement(card).click(card.$(CARD_RELATIVE_DELETE_BUTTON))
                    .click(card.$(CARD_RELATIVE_DELETE_CONFIRMATION)).build().perform();
        });
        return this;
    }

    SelenideElement getVolumeCard(String namePrefix) {
        return $(By.cssSelector(".title.truncateText[title^=\"" + namePrefix + "\"]")).parent()
                .parent()
                .parent();
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
        executeInFrame(0, () -> $(REFRESH_BUTTON).click());
        return this;
    }

    @Override
    public VolumesPage getThis() {
        return this;
    }

}
