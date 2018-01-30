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

package com.vmware.admiral.test.ui.pages.containers;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLocators;

public class ContainersPageLocators extends ResourcePageLocators {

    private final String CARD_RELATIVE_STOP_BUTTON = "//i[contains(concat(' ', @class, ' '), ' fa-stop ')]";
    private final String CARD_RELATIVE_SCALE_BUTTON = "//i[contains(concat(' ', @class, ' '), ' fa-plus ')]";
    private final String CARD_RELATIVE_INSPECT_BUTTON = "//i[contains(concat(' ', @class, ' '), ' fa-eye ')]";
    private final String CARD_RELATIVE_PORTS_HOLDER = "//div[contains(concat(' ', @class, ' '), ' container-ports-holder ')]/span[2]";

    public By cardStopButtonByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_RELATIVE_STOP_BUTTON);
    }

    public By cardScaleButtonByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_RELATIVE_SCALE_BUTTON);
    }

    public By cardPortsHolder(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_RELATIVE_PORTS_HOLDER);
    }

    public By cardInspectButtonByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + CARD_RELATIVE_INSPECT_BUTTON);
    }

}
