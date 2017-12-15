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

package com.vmware.admiral.test.ui.pages.common;

import static com.codeborne.selenide.Selenide.$;

import java.util.Objects;

import org.openqa.selenium.By;

public abstract class HomeTabAdvancedPage<P extends HomeTabAdvancedPage<P, V>, V extends PageValidator>
        extends HomeTabPage<P, V> {

    private final String CARD_TITLE_SELECTOR = "//div[contains(concat(' ', normalize-space(@class), ' '), ' grid-item ')]//div[contains(concat(' ', @class, ' '), ' title ')]";
    protected final String CARD_SELECTOR_BY_EXACT_NAME_XPATH = CARD_TITLE_SELECTOR
            + "[text()='%s']/../../../..";
    protected final String CARD_SELECTOR_BY_NAME_PREFIX_XPATH = CARD_TITLE_SELECTOR
            + "[starts-with(text(), '%s')]/../../../..";
    protected final By CARD_RELATIVE_DELETE_CONFIRMATION_BUTTON = By
            .cssSelector(".delete-inline-item-confirmation-confirm");
    protected final By REFRESH_BUTTON = By.cssSelector(".fa.fa-refresh");
    public final By CARD_RELATIVE_DELETE_BUTTON = By.cssSelector(".fa.fa-trash");
    protected final By CREATE_RESOURCE_BUTTON = By.cssSelector(".btn.btn-link.create-resource-btn");

    private RequestsToolbar requestsToolbar;

    private EventLogToolbar eventLogToolbar;

    public RequestsToolbar requests() {
        return Objects.isNull(requestsToolbar) ? new RequestsToolbar() : requestsToolbar;
    }

    public EventLogToolbar eventLogs() {
        return Objects.isNull(eventLogToolbar) ? new EventLogToolbar() : eventLogToolbar;
    }

    @Override
    public P refresh() {
        LOG.info("Refreshing...");
        executeInFrame(0, () -> {
            $(REFRESH_BUTTON).click();
            waitForSpinner();
        });
        return getThis();
    }

}
