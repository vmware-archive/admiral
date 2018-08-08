/*
 * Copyright (c) 2018 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package com.vmware.admiral.test.ui.pages.common;

import org.openqa.selenium.By;

public class EventLogToolbarLocators extends PageLocators {

    private final By LAST_LOG_MESSAGE = By
            .cssSelector(".eventlog-list #all .eventlog-item:first-child .description");
    private final By EVENT_LOG_BUTTON = By
            .cssSelector(".right-context-panel .toolbar-item:nth-child(2) .toolbar-item-title");

    public By lastLogMessage() {
        return LAST_LOG_MESSAGE;
    }

    public By eventLogButton() {
        return EVENT_LOG_BUTTON;
    }

}
