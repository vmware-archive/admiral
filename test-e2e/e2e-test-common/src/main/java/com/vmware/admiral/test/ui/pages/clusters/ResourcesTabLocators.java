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

package com.vmware.admiral.test.ui.pages.clusters;

import org.openqa.selenium.By;

public class ResourcesTabLocators extends ClusterDetailsPageLocators {

    private final By ADD_HOST_BUTTON = By.cssSelector("#resourcesContent .btn.addHost");
    private final String HOST_CARD_BY_URL_XPATH = "//clr-tab-content//span[contains(concat(' ', @class, ' '), ' card-item ')]//div[contains(concat(' ', @class, ' '), ' card-text ')]//div[contains(concat(' ', @class, ' '), ' form-group ')][1]/div[text()='%s']/ancestor::card";
    private final String CARD_RELATIVE_STATE_XPATH = "//div[contains(concat(' ', @class, ' '), ' status ')]";

    public By addHostButton() {
        return ADD_HOST_BUTTON;
    }

    public By hostStateByUrl(String url) {
        return By.xpath(String.format(HOST_CARD_BY_URL_XPATH, url) + CARD_RELATIVE_STATE_XPATH);
    }

}
