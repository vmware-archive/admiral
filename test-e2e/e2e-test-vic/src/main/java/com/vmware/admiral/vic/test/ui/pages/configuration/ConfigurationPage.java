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

package com.vmware.admiral.vic.test.ui.pages.configuration;

import static com.codeborne.selenide.Selenide.$;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

import com.google.common.io.Files;

import org.openqa.selenium.By;

import com.vmware.admiral.test.ui.pages.common.BasicPage;

public class ConfigurationPage extends BasicPage<ConfigurationPage, ConfigurationPageValidator> {

    private final By CERTIFICATE_DOWNLOAD_LINK = By
            .cssSelector(".form-group>a[href=\"/hbr-api/systeminfo/getcert\"]");

    private ConfigurationPageValidator validator;

    public ConfigurationPage downloadCertificate(String localFile) {
        File file = new File(localFile);
        String folder = file.getParent();
        File localFolder = new File(folder);
        if (!localFolder.exists() || !localFolder.isDirectory()) {
            throw new IllegalArgumentException(
                    String.format("Folder [%s] does not exist.", folder));
        }
        File remoteFile;
        try {
            remoteFile = $(CERTIFICATE_DOWNLOAD_LINK).download();
            Files.move(remoteFile, new File(localFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Could now download certificate.", e);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Certificate was downloaded, but was not moved to desired diretory.", e);
        }
        return this;
    }

    @Override
    public ConfigurationPageValidator validate() {
        if (Objects.isNull(validator)) {
            validator = new ConfigurationPageValidator();
        }
        return validator;
    }

    @Override
    public ConfigurationPage getThis() {
        return this;
    }

    @Override
    public void waitToLoad() {
        validate().validateIsCurrentPage();
    }
}
