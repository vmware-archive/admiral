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

import java.io.File;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class ConfigurationPage
        extends BasicPage<ConfigurationPageValidator, ConfigurationPageLocators> {

    public ConfigurationPage(By[] iFrameLocators, ConfigurationPageValidator validator,
            ConfigurationPageLocators pageLocators) {
        super(iFrameLocators, validator, pageLocators);
    }

    public void downloadCertificate(String localFile) {
        LOG.info(String.format("Downloading certificate"));
        File downloadedFile = pageActions().donwload(localFile,
                locators().certificateDownloadLink());
        LOG.info(String.format("Downloaded certificate to [%s]", downloadedFile.getAbsolutePath()));
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }

}
