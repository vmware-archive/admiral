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

import static com.codeborne.selenide.Condition.disappear;
import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ModalDialogue;

public class AddHostModalDialogue
        extends ModalDialogue<AddHostModalDialogue, AddHostModalDialogueValidator> {

    private final String MODAL_BASE = ".modal-content";
    private final By NAME_INPUT = By.id("name");
    private final By DESCRIPTION_INPUT = By.id("description");
    private final By URL_INPUT = By.id("url");
    private final By HOST_TYPE_OPTIONS = By
            .cssSelector(".ng-pristine.ng-valid[formcontrolname*=type]");
    private final By MODAL_BACKDROP = By.cssSelector(".modal-backdrop");
    private final By NEW_CONTAINER_HOST_SAVE_BUTTON = By
            .cssSelector(MODAL_BASE + " .btn.btn-primary");
    private final By NEW_CONTAINER_HOST_CANCEL_BUTTON = By
            .cssSelector(MODAL_BASE + " .btn.btn-outline");

    private AddHostModalDialogueValidator validator;

    public static enum HostType {
        VCH, DOCKER;
    }

    public AddHostModalDialogue setName(String name) {
        LOG.info(String.format("Setting host name: [%s]", name));
        $(URL_INPUT).clear();
        $(NAME_INPUT).sendKeys(name);
        return this;
    }

    public AddHostModalDialogue setDescription(String description) {
        LOG.info(String.format("Setting description: [%s]", description));
        $(DESCRIPTION_INPUT).clear();
        $(DESCRIPTION_INPUT).sendKeys(description);
        return this;
    }

    public AddHostModalDialogue setHostType(HostType hostType) {
        LOG.info(String.format("Setting host type: [%s]", hostType.toString()));
        if (hostType == HostType.DOCKER) {
            $(HOST_TYPE_OPTIONS).selectOptionByValue(HostType.DOCKER.toString());
        } else {
            $(HOST_TYPE_OPTIONS).selectOptionByValue(HostType.VCH.toString());
        }
        return this;
    }

    public AddHostModalDialogue setUrl(String url) {
        LOG.info(String.format("Setting host url: [%s]", url));
        $(URL_INPUT).clear();
        $(URL_INPUT).sendKeys(url);
        return this;
    }

    @Override
    protected void confirmDialogue() {
        LOG.info("Submitting...");
        $(NEW_CONTAINER_HOST_SAVE_BUTTON).click();
    }

    @Override
    public void cancel() {
        LOG.info("Cancelling...");
        $(NEW_CONTAINER_HOST_CANCEL_BUTTON).click();
        $(MODAL_BACKDROP).should(disappear);
    }

    @Override
    protected AddHostModalDialogueValidator getValidator() {
        if (Objects.isNull(validator)) {
            validator = new AddHostModalDialogueValidator();
        }
        return validator;
    }

    @Override
    protected AddHostModalDialogue getThis() {
        return this;
    }

}
