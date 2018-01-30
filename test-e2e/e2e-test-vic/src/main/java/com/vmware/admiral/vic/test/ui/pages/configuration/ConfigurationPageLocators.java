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

package com.vmware.admiral.vic.test.ui.pages.configuration;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.PageLocators;

public class ConfigurationPageLocators extends PageLocators {

    private final By CERTIFICATE_DOWNLOAD_LINK = By
            .cssSelector(".form-group>a[href='/hbr-api/systeminfo/getcert']");
    private final By PAGE_TITLE = By.cssSelector("div.title");

    public By certificateDownloadLink() {
        return CERTIFICATE_DOWNLOAD_LINK;
    }

    public By pageTitle() {
        return PAGE_TITLE;
    }

}
