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

package com.vmware.admiral.test.ui.pages.networks;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.ResourcePageLocators;

public class NetworksPageLocators extends ResourcePageLocators {

    private final String DELETE_NETWORK_ERROR_MESSAGE = "//div[contains(concat(' ', @class, ' '), ' alert-dismissible ')]";

    public By deleteNetworkErrorMessageByTitlePrefix(String titlePrefix) {
        return By.xpath(cardByTitlePrefixXpath(titlePrefix) + DELETE_NETWORK_ERROR_MESSAGE);
    }

}
